package com.mkulikov.taskb.feature.holiday

import com.mkulikov.taskb.feature.holiday.api.database.*
import com.mkulikov.taskb.feature.holiday.api.model.HolidayCalendar
import com.mkulikov.taskb.feature.holiday.network.*
import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class HolidayRepositoryTest {
    private val city = AppLocation("Yekaterinburg", 56.84, 60.61, "Asia/Yekaterinburg", "RU", "Sverdlovsk Oblast")
    private val from = LocalDate(2026, 11, 1)
    private val through = LocalDate(2026, 11, 30)

    @Test
    fun `the first opened month is read from DB before a single monthly request and survives restart offline`() = runTest {
        val saved = Holidays()
        var requests = 0
        var fail = false
        val client = holidayClient { request ->
            requests++
            assertTrue(saved.snapshotReads > 0)
            assertEquals("kayaposoft.com", request.url.host)
            assertEquals("getHolidaysForMonth", request.url.parameters["action"])
            assertEquals("ru", request.url.parameters["country"])
            assertEquals("2026", request.url.parameters["year"])
            assertEquals("11", request.url.parameters["month"])
            assertEquals("public_holiday", request.url.parameters["holidayType"])
            if (fail) error("Offline")
            respond(holidayJson("Unity Day", 4, 11))
        }
        try {
            fun repository() = HolidayRepository(EnricoApi(client), Locations(city), lazyOf(saved), dispatcher = StandardTestDispatcher(testScheduler))
            val first = repository().observeHolidays(from, through).first { !it.isLoading }
            assertNull(first.error)
            assertEquals("Unity Day", first.holidays.single().name)
            assertEquals(LocalDate(2026, 11, 4), first.holidays.single().date)
            fail = true
            val restarted = repository().observeHolidays(from, through).first { !it.isLoading }
            assertEquals(first.holidays, restarted.holidays)
            assertNull(restarted.error)
            assertEquals(1, requests)
        } finally { client.close() }
    }

    @Test
    fun `successful empty months are persisted while different months and locations load independently`() = runTest {
        val saved = Holidays()
        val locations = Locations(city)
        val months = mutableListOf<String>()
        val client = holidayClient { request ->
            months += "${request.url.parameters["country"]}:${request.url.parameters["month"]}"
            respond("[]")
        }
        try {
            fun repository() = HolidayRepository(EnricoApi(client), locations, lazyOf(saved), dispatcher = StandardTestDispatcher(testScheduler))
            assertTrue(repository().observeHolidays(from, through).first { !it.isLoading }.holidays.isEmpty())
            repository().observeHolidays(from, through).first { !it.isLoading }
            repository().observeHolidays(LocalDate(2026, 12, 1), LocalDate(2026, 12, 31)).first { !it.isLoading }
            locations.setLocation(city.copy(name = "Paris", countryCode = "FR", regionName = "Île-de-France"))
            repository().observeHolidays(from, through).first { !it.isLoading }
            assertEquals(listOf("ru:11", "ru:12", "fr:11"), months)
            assertEquals(3, saved.months.size)
            assertTrue(saved.rows.value.isEmpty())
        } finally { client.close() }
    }

    @Test
    fun `missing location and legacy location without country make no requests or database reads`() = runTest {
        val saved = Holidays()
        var requests = 0
        val client = holidayClient { requests++; error("Must not request holidays") }
        try {
            val locations = Locations(null)
            val repository = HolidayRepository(EnricoApi(client), locations, lazyOf(saved), dispatcher = StandardTestDispatcher(testScheduler))
            assertNull(repository.observeHolidays(from, through).first().location)
            locations.setLocation(city.copy(countryCode = null))
            val legacy = repository.observeHolidays(from, through).first()
            assertNotNull(legacy.error)
            assertTrue(legacy.holidays.isEmpty())
            assertEquals(0, requests)
            assertEquals(0, saved.snapshotReads)
        } finally { client.close() }
    }

    @Test
    fun `failures are not cached and retry leaves already fetched months available`() = runTest {
        val saved = Holidays()
        saved.replaceMonth(HolidayMonthEntity("RU", 2026, 11, 1), listOf(
            HolidayEntity("RU", 2026, 11, "cached", LocalDate(2026, 11, 4).toEpochDays(), "Unity Day", "RU")))
        var requests = 0
        var fail = true
        val client = holidayClient { request ->
            requests++
            assertEquals("12", request.url.parameters["month"])
            if (fail) respond("unavailable", HttpStatusCode.ServiceUnavailable) else respond("[]")
        }
        try {
            val repository = HolidayRepository(EnricoApi(client), Locations(city), lazyOf(saved), dispatcher = StandardTestDispatcher(testScheduler))
            val failed = repository.observeHolidays(from, LocalDate(2026, 12, 31)).first { !it.isLoading }
            assertNotNull(failed.error)
            assertEquals("Unity Day", failed.holidays.single().name)
            assertNull(saved.getMonth("RU", 2026, 12))
            fail = false
            val retried = repository.observeHolidays(from, LocalDate(2026, 12, 31)).first { !it.isLoading }
            assertNull(retried.error)
            assertEquals(failed.holidays, retried.holidays)
            assertNotNull(saved.getMonth("RU", 2026, 12))
            assertEquals(2, requests)
        } finally { client.close() }
    }

    @Test
    fun `clearing or switching application location cannot leak holidays from previous country`() = runTest {
        val saved = Holidays()
        val locations = Locations(city)
        val client = holidayClient { request ->
            respond(holidayJson(if (request.url.parameters["country"] == "ru") "Unity Day" else "Armistice Day", 4, 11))
        }
        try {
            val repository = HolidayRepository(EnricoApi(client), locations, lazyOf(saved), dispatcher = StandardTestDispatcher(testScheduler))
            val states = Channel<HolidayCalendar>(Channel.UNLIMITED)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observeHolidays(from, through).collect { states.send(it) } }
            suspend fun ready(name: String?): HolidayCalendar {
                while (true) {
                    val state = states.receive()
                    if (state.location?.name == name && !state.isLoading) return state
                }
            }
            assertEquals("Unity Day", ready(city.name).holidays.single().name)
            val paris = city.copy(name = "Paris", countryCode = "FR", regionName = "Île-de-France")
            locations.setLocation(paris)
            val french = ready(paris.name)
            assertEquals("Armistice Day", french.holidays.single().name)
            assertEquals("FR", french.holidays.single().countryCode)
            locations.setLocation(null)
            assertTrue(ready(null).holidays.isEmpty())
        } finally { client.close() }
    }

    @Test
    fun `exact subdivision mapping is used and ambiguous locations never pick another region`() = runTest {
        val germany = city.copy(countryCode = "DE", regionName = "Bavaria")
        assertEquals(HolidayLocation("DE", "by"), holidayLocation(germany))
        assertEquals(HolidayLocation("US", "ca"), holidayLocation(city.copy(countryCode = "US", regionName = "California")))
        assertFailsWith<HolidayLocationException> { holidayLocation(germany.copy(regionName = "Unknown region")) }
        var calls = 0
        val client = holidayClient { request ->
            calls++
            assertEquals("de", request.url.parameters["country"])
            assertEquals("by", request.url.parameters["region"])
            respond("[]")
        }
        try {
            val saved = Holidays()
            val locations = Locations(germany)
            val repository = HolidayRepository(EnricoApi(client), locations, lazyOf(saved), dispatcher = StandardTestDispatcher(testScheduler))
            assertNull(repository.observeHolidays(from, through).first { !it.isLoading }.error)
            assertNotNull(saved.getMonth("DE:by", 2026, 11))
            locations.setLocation(germany.copy(regionName = "Unknown region"))
            assertNotNull(repository.observeHolidays(from, through).first().error)
            assertEquals(1, calls)
        } finally { client.close() }
    }

    @Test
    fun `only public holidays covering the selected scope are accepted and date spans are expanded`() {
        val json = """[
            {"date":{"year":2026,"month":11,"day":1},"dateTo":{"year":2026,"month":11,"day":3},"name":[{"lang":"en","text":"Three-day holiday"}],"holidayType":"public_holiday"},
            {"date":{"year":2026,"month":11,"day":7},"observedOn":{"year":2026,"month":11,"day":9},"name":[{"lang":"en","text":"Observed holiday"}],"holidayType":"public_holiday"},
            {"date":{"year":2026,"month":11,"day":4},"name":[{"lang":"en","text":"Municipal-only holiday"}],"holidayType":"public_holiday","flags":["REGIONAL_HOLIDAY"]},
            {"date":{"year":2026,"month":11,"day":5},"name":[{"lang":"en","text":"Observance"}],"holidayType":"observance"}
        ]"""
        val days = parseHolidays(json, HolidayLocation("FR", null), YearMonth(2026, 11))
        assertEquals(listOf(1, 2, 3, 9), days.map { it.date.day })
        assertEquals(4, days.map { it.id }.distinct().size)
        assertFailsWith<HolidayLocationException> { parseHolidays("""{"error":"Unsupported country"}""", HolidayLocation("XX", null), YearMonth(2026, 11)) }
        assertFailsWith<IllegalArgumentException> { parseHolidays(holidayJson("Bad date", 32, 11), HolidayLocation("RU", null), YearMonth(2026, 11)) }
        assertEquals(emptyList(), parseHolidays(holidayJson("Other month", 1, 12), HolidayLocation("RU", null), YearMonth(2026, 11)))
    }

    @Test
    fun `closing observation cancels request without creating success marker`() = runTest {
        val saved = Holidays()
        val started = CompletableDeferred<Unit>()
        val client = holidayClient { started.complete(Unit); awaitCancellation() }
        try {
            val repository = HolidayRepository(EnricoApi(client), Locations(city), lazyOf(saved), dispatcher = StandardTestDispatcher(testScheduler))
            val observation = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observeHolidays(from, through).collect() }
            started.await()
            observation.cancelAndJoin()
            assertTrue(observation.isCancelled)
            assertTrue(saved.months.isEmpty())
        } finally { client.close() }
    }

    @Test
    fun `unsupported countries and dates never create an empty successful cache marker`() = runTest {
        var monthlyRequests = 0
        val client = holidayClient { monthlyRequests++; respond("[]") }
        try {
            val saved = Holidays()
            val locations = Locations(city.copy(countryCode = "ZZ"))
            val repository = HolidayRepository(EnricoApi(client), locations, lazyOf(saved), dispatcher = StandardTestDispatcher(testScheduler))
            assertNotNull(repository.observeHolidays(from, through).first { !it.isLoading }.error)
            locations.setLocation(city)
            assertNotNull(repository.observeHolidays(LocalDate(2000, 1, 1), LocalDate(2000, 1, 31)).first { !it.isLoading }.error)
            assertEquals(0, monthlyRequests)
            assertTrue(saved.months.isEmpty())
        } finally { client.close() }
    }

    @Test
    fun `country metadata is fetched only on a cache miss and reused for later uncached months`() = runTest {
        var metadataRequests = 0
        var monthRequests = 0
        val saved = Holidays()
        saved.replaceMonth(HolidayMonthEntity("RU", 2026, 11, 1), emptyList())
        val client = HttpClient(MockEngine { request ->
            assertTrue(saved.snapshotReads > 0)
            if (request.url.parameters["action"] == "getSupportedCountries") {
                metadataRequests++
                respond(coverageJson)
            } else {
                monthRequests++
                respond("[]")
            }
        })
        try {
            val api = EnricoApi(client)
            val repository = HolidayRepository(api, Locations(city), lazyOf(saved), dispatcher = StandardTestDispatcher(testScheduler))
            repository.observeHolidays(from, through).first { !it.isLoading }
            assertEquals(0, metadataRequests)
            assertEquals(0, monthRequests)
            repository.observeHolidays(LocalDate(2026, 12, 1), LocalDate(2026, 12, 31)).first { !it.isLoading }
            repository.observeHolidays(LocalDate(2027, 1, 1), LocalDate(2027, 1, 31)).first { !it.isLoading }
            assertEquals(1, metadataRequests)
            assertEquals(2, monthRequests)
        } finally { client.close() }
    }

    private fun holidayClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = HttpClient(MockEngine { request ->
        if (request.url.parameters["action"] == "getSupportedCountries") respond(coverageJson) else handler(request)
    })

    private val coverageJson = """[
        {"countryCode":"rus","regions":[],"holidayTypes":["public_holiday"],"fromDate":{"year":2011,"month":1,"day":1},"toDate":{"year":2100,"month":12,"day":31}},
        {"countryCode":"fra","regions":[],"holidayTypes":["public_holiday"],"fromDate":{"year":2011,"month":1,"day":1},"toDate":{"year":2100,"month":12,"day":31}},
        {"countryCode":"deu","regions":["by","bw"],"holidayTypes":["public_holiday"],"fromDate":{"year":2011,"month":1,"day":1},"toDate":{"year":2100,"month":12,"day":31}},
        {"countryCode":"usa","regions":["ca"],"holidayTypes":["public_holiday"],"fromDate":{"year":2011,"month":1,"day":1},"toDate":{"year":2100,"month":12,"day":31}}
    ]"""

    private fun holidayJson(name: String, day: Int, month: Int): String = """[{"date":{"year":2026,"month":$month,"day":$day},"name":[{"lang":"en","text":"$name"}],"holidayType":"public_holiday"}]"""

    private class Locations(initial: AppLocation?) : IAppLocationRepository {
        private val location = MutableStateFlow(initial)
        override fun observeLocation() = location
        override suspend fun searchLocations(query: String) = emptyList<AppLocation>()
        override suspend fun setLocation(location: AppLocation?) { this.location.value = location }
    }

    private class Holidays : IHolidayDao {
        val months = mutableMapOf<Triple<String, Int, Int>, HolidayMonthEntity>()
        val rows = MutableStateFlow<List<HolidayEntity>>(emptyList())
        var snapshotReads = 0
        override suspend fun getMonth(locationKey: String, year: Int, month: Int): HolidayMonthEntity? {
            snapshotReads++
            return months[Triple(locationKey, year, month)]
        }
        override suspend fun getHolidays(locationKey: String, fromDate: Long, throughDate: Long) =
            rows.value.filter { it.locationKey == locationKey && it.date in fromDate..throughDate }
        override fun observeHolidays(locationKey: String, fromDate: Long, throughDate: Long) =
            rows.map { entries -> entries.filter { it.locationKey == locationKey && it.date in fromDate..throughDate } }
        override suspend fun saveMonth(month: HolidayMonthEntity) { months[Triple(month.locationKey, month.year, month.month)] = month }
        override suspend fun deleteMonthHolidays(locationKey: String, year: Int, month: Int) {
            rows.value = rows.value.filterNot { it.locationKey == locationKey && it.year == year && it.month == month }
        }
        override suspend fun insertHolidays(holidays: List<HolidayEntity>) { rows.value += holidays }
        override suspend fun replaceMonth(month: HolidayMonthEntity, holidays: List<HolidayEntity>) {
            saveMonth(month)
            rows.value = rows.value.filterNot { it.locationKey == month.locationKey && it.year == month.year && it.month == month.month } + holidays
        }
    }
}
