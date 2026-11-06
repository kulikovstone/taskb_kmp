package com.mkulikov.taskb.feature.holiday.network

import com.mkulikov.taskb.feature.holiday.api.model.PublicHoliday
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.number
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth
import kotlinx.serialization.json.*

internal class EnricoApi(private val client: HttpClient?) {
    private val coverageMutex = Mutex()
    private var coverage: List<CountryCoverage>? = null
    suspend fun holidays(location: HolidayLocation, month: YearMonth): List<PublicHoliday> {
        val http = client ?: throw HolidayLocationException("Public holidays are not available on this platform yet.")
        val countries = coverageMutex.withLock {
            coverage ?: loadCoverage(http).also { coverage = it }
        }
        val country = countries.firstOrNull { it.countryCode == location.countryCode }
            ?: throw HolidayLocationException("Public holidays are not available for this country.")
        val lastDay = month.firstDay.plus(month.numberOfDays - 1, DateTimeUnit.DAY)
        if (month.firstDay < country.fromDate || lastDay > country.throughDate) {
            throw HolidayLocationException("Public holidays are not available for this month.")
        }
        if (country.regions.isNotEmpty() && location.region !in country.regions) {
            throw HolidayLocationException("Public holidays are not available for the selected region.")
        }
        val response = http.get("https://kayaposoft.com/enrico/json/v2.0/") {
            parameter("action", "getHolidaysForMonth")
            parameter("country", location.countryCode.lowercase())
            parameter("year", month.year)
            parameter("month", month.month.number)
            parameter("holidayType", "public_holiday")
            location.region?.let { parameter("region", it) }
        }
        check(response.status.value in 200..299)
        return parseHolidays(response.bodyAsText(), location, month)
    }

    private suspend fun loadCoverage(http: HttpClient): List<CountryCoverage> {
        val response = http.get("https://kayaposoft.com/enrico/json/v2.0/") {
            parameter("action", "getSupportedCountries")
        }
        check(response.status.value in 200..299)
        return Json.parseToJsonElement(response.bodyAsText()).jsonArray.mapNotNull { element ->
            val item = element.jsonObject
            val code = countryAlpha2[item.getValue("countryCode").jsonPrimitive.content] ?: return@mapNotNull null
            if (item.getValue("holidayTypes").jsonArray.none { it.jsonPrimitive.content == "public_holiday" }) return@mapNotNull null
            CountryCoverage(code, item.getValue("regions").jsonArray.map { it.jsonPrimitive.content }.toSet(),
                parseDate(item.getValue("fromDate")), parseDate(item.getValue("toDate")))
        }
    }

    private data class CountryCoverage(val countryCode: String, val regions: Set<String>, val fromDate: LocalDate, val throughDate: LocalDate)
}

// Enrico's coverage endpoint returns alpha-3 codes, while application locations
// and its monthly endpoint use alpha-2. Only known ISO/provider identities map;
// a newly added country remains unsupported until its identity is added here.
private val countryAlpha2 = "ago=AO arg=AR aus=AU aut=AT blr=BY bel=BE bih=BA bra=BR bgr=BG can=CA chl=CL chn=CN col=CO hrv=HR cyp=CY cze=CZ dnk=DK slv=SV est=EE fin=FI fra=FR deu=DE grc=GR hkg=HK hun=HU isl=IS irl=IE imn=IM isr=IL ita=IT jpn=JP kor=KR xkx=XK lva=LV ltu=LT lux=LU mkd=MK mex=MX mne=ME nld=NL nzl=NZ nor=NO per=PE phl=PH pol=PL prt=PT rou=RO rus=RU srb=RS sgp=SG svk=SK svn=SI zaf=ZA esp=ES swe=SE che=CH tur=TR ukr=UA gbr=GB usa=US"
    .split(' ').associate { entry -> entry.substringBefore('=') to entry.substringAfter('=') }


internal fun parseHolidays(value: String, location: HolidayLocation, month: YearMonth): List<PublicHoliday> {
    val root = Json.parseToJsonElement(value)
    if (root is JsonObject && root["error"] != null) {
        throw HolidayLocationException("Public holidays are not available for this location or month.")
    }
    return root.jsonArray.flatMap { element ->
        val item = element.jsonObject
        if (item.getValue("holidayType").jsonPrimitive.content != "public_holiday") return@flatMap emptyList()
        val flags = item["flags"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
        if ("REGIONAL_HOLIDAY" in flags) return@flatMap emptyList()
        val name = item.getValue("name").jsonArray.map { it.jsonObject }
            .firstOrNull { it["lang"]?.jsonPrimitive?.content == "en" }
            ?.get("text")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("Missing English public-holiday name")
        val original = parseDate(item.getValue("date"))
        val originalEnd = item["dateTo"]?.let(::parseDate) ?: original
        require(originalEnd >= original && originalEnd.toEpochDays() - original.toEpochDays() <= 366)
        val start = item["observedOn"]?.let(::parseDate) ?: original
        val length = (originalEnd.toEpochDays() - original.toEpochDays()).toInt()
        (0..length).mapNotNull { offset ->
            val date = start.plus(offset, DateTimeUnit.DAY)
            if (date.yearMonth != month) null else PublicHoliday(
                id = "${location.key}:$date:$name", date = date, name = name, countryCode = location.countryCode,
            )
        }
    }.distinctBy { it.id }.sortedWith(compareBy<PublicHoliday> { it.date }.thenBy { it.name }.thenBy { it.id })
}

private fun parseDate(element: JsonElement): LocalDate {
    val item = element.jsonObject
    return LocalDate(item.getValue("year").jsonPrimitive.int, item.getValue("month").jsonPrimitive.int,
        item.getValue("day").jsonPrimitive.int)
}
