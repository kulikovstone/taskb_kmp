package com.mkulikov.taskb.feature.holiday.network

import com.mkulikov.taskb.feature.holiday.api.model.PublicHoliday
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth
import kotlinx.serialization.json.*

internal class EnricoApi(private val client: HttpClient?) {
    private val coverageMutex = Mutex()
    private var coverage: List<CountryCoverage>? = null

    suspend fun holidays(location: HolidayLocation, month: YearMonth): List<PublicHoliday> {
        val http = client
            ?: throw HolidayLocationException(
                message = "Public holidays are not available on this platform yet."
            )
        val countries = coverageMutex.withLock {
            coverage ?: loadCoverage(http = http).also { currentCoverage -> coverage = currentCoverage }
        }
        val country = countries.firstOrNull { coverage -> coverage.countryCode == location.countryCode }
            ?: throw HolidayLocationException(message = "Public holidays are not available for this country.")
        val lastDay = month.firstDay.plus(value = month.numberOfDays - 1, unit = DateTimeUnit.DAY)
        if (month.firstDay < country.fromDate || lastDay > country.throughDate) {
            throw HolidayLocationException(message = "Public holidays are not available for this month.")
        }
        if (country.regions.isNotEmpty() && location.region !in country.regions) {
            throw HolidayLocationException(message = "Public holidays are not available for the selected region.")
        }
        val response = http.get(urlString = "https://kayaposoft.com/enrico/json/v2.0/") {
            parameter(key = "action", value = "getHolidaysForMonth")
            parameter(key = "country", value = location.countryCode.lowercase())
            parameter(key = "year", value = month.year)
            parameter(key = "month", value = month.month.number)
            parameter(key = "holidayType", value = "public_holiday")
            location.region?.let { region -> parameter(key = "region", value = region) }
        }
        check(value = response.status.value in 200..299)
        return parseHolidays(value = response.bodyAsText(), location = location, month = month)
    }

    private suspend fun loadCoverage(http: HttpClient): List<CountryCoverage> {
        val response = http.get(urlString = "https://kayaposoft.com/enrico/json/v2.0/") {
            parameter(key = "action", value = "getSupportedCountries")
        }
        check(value = response.status.value in 200..299)
        return Json.parseToJsonElement(string = response.bodyAsText()).jsonArray.mapNotNull { element ->
            val item = element.jsonObject
            val code = countryAlpha2[item.getValue(key = "countryCode").jsonPrimitive.content] ?: return@mapNotNull null
            if (item.getValue(key = "holidayTypes").jsonArray.none { element ->
                element.jsonPrimitive.content == "public_holiday"
            })
                return@mapNotNull null
            CountryCoverage(
                countryCode = code,
                regions = item.getValue(key = "regions").jsonArray.map { element ->
                    element.jsonPrimitive.content
                }.toSet(),
                fromDate = parseDate(element = item.getValue(key = "fromDate")),
                throughDate = parseDate(element = item.getValue(key = "toDate")),
            )
        }
    }

    private data class CountryCoverage(
        val countryCode: String,
        val regions: Set<String>,
        val fromDate: LocalDate,
        val throughDate: LocalDate,
    )
}

// Enrico's coverage endpoint returns alpha-3 codes, while application locations
// and its monthly endpoint use alpha-2. Only known ISO/provider identities map;
// a newly added country remains unsupported until its identity is added here.
private val countryAlpha2 =
    ("ago=AO arg=AR aus=AU aut=AT blr=BY bel=BE bih=BA bra=BR bgr=BG can=CA chl=CL chn=CN col=CO " +
            "hrv=HR cyp=CY cze=CZ dnk=DK slv=SV est=EE fin=FI fra=FR deu=DE grc=GR hkg=HK hun=HU isl=IS " +
            "irl=IE imn=IM isr=IL ita=IT jpn=JP kor=KR xkx=XK lva=LV ltu=LT lux=LU mkd=MK mex=MX mne=ME " +
            "nld=NL nzl=NZ nor=NO per=PE phl=PH pol=PL prt=PT rou=RO rus=RU srb=RS sgp=SG svk=SK svn=SI " +
            "zaf=ZA esp=ES swe=SE che=CH tur=TR ukr=UA gbr=GB usa=US")
        .split(' ')
        .associate { entry -> entry.substringBefore(delimiter = '=') to entry.substringAfter(delimiter = '=') }

internal fun parseHolidays(value: String, location: HolidayLocation, month: YearMonth): List<PublicHoliday> {
    val root = Json.parseToJsonElement(string = value)
    if (root is JsonObject && root["error"] != null) {
        throw HolidayLocationException(message = "Public holidays are not available for this location or month.")
    }
    return root.jsonArray
        .flatMap { element ->
            val item = element.jsonObject
            if (item.getValue(key = "holidayType").jsonPrimitive.content != "public_holiday")
                return@flatMap emptyList()
            val flags = item["flags"]?.jsonArray.orEmpty().map { element -> element.jsonPrimitive.content }
            if ("REGIONAL_HOLIDAY" in flags) return@flatMap emptyList()
            val name = item
                .getValue(key = "name")
                .jsonArray
                .map(transform = JsonElement::jsonObject)
                .firstOrNull { item -> item["lang"]?.jsonPrimitive?.content == "en" }
                ?.get(key = "text")
                ?.jsonPrimitive
                ?.content
                ?.takeIf(predicate = String::isNotBlank) ?: error(message = "Missing English public-holiday name")
            val original = parseDate(element = item.getValue(key = "date"))
            val originalEnd = item["dateTo"]?.let(block = ::parseDate) ?: original
            require(value = originalEnd >= original && originalEnd.toEpochDays() - original.toEpochDays() <= 366)
            val start = item["observedOn"]?.let(block = ::parseDate) ?: original
            val length = (originalEnd.toEpochDays() - original.toEpochDays()).toInt()
            (0..length).mapNotNull { offset ->
                val date = start.plus(value = offset, unit = DateTimeUnit.DAY)
                if (date.yearMonth != month) {
                    null
                } else {
                    PublicHoliday(
                        id = "${location.key}:$date:$name",
                        date = date,
                        name = name,
                        countryCode = location.countryCode,
                    )
                }
            }
        }
        .distinctBy(selector = PublicHoliday::id)
        .sortedWith(
            comparator = compareBy<PublicHoliday> { holiday ->
                holiday.date
            }.thenBy(selector = PublicHoliday::name).thenBy(selector = PublicHoliday::id),
        )
}

private fun parseDate(element: JsonElement): LocalDate {
    val item = element.jsonObject
    return LocalDate(
        year = item.getValue(key = "year").jsonPrimitive.int,
        month = item.getValue(key = "month").jsonPrimitive.int,
        day = item.getValue(key = "day").jsonPrimitive.int,
    )
}
