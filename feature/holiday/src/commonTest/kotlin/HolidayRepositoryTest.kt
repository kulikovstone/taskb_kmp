package com.mkulikov.taskb.feature.holiday

import com.mkulikov.taskb.feature.holiday.api.database.*
import com.mkulikov.taskb.feature.holiday.api.model.HolidayCalendar
import com.mkulikov.taskb.feature.holiday.api.model.PublicHoliday
import com.mkulikov.taskb.feature.holiday.network.*
import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlin.test.*
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

@OptIn(ExperimentalCoroutinesApi::class)
class HolidayRepositoryTest {
    private val city = AppLocation(
        name = "Yekaterinburg",
        latitude = 56.84,
        longitude = 60.61,
        timeZone = "Asia/Yekaterinburg",
        countryCode = "RU",
        regionName = "Sverdlovsk Oblast",
    )
    private val from = LocalDate(year = 2026, month = 11, day = 1)
    private val through = LocalDate(year = 2026, month = 11, day = 30)

    @Test
    fun `the first opened month is read from DB before a single monthly request and survives restart offline`():
        TestResult {
        return runTest {
            val saved = Holidays()
            var requests = 0
            var fail = false
            val client = holidayClient { request ->
                requests++
                assertTrue(actual = saved.snapshotReads > 0)
                assertEquals(expected = "kayaposoft.com", actual = request.url.host)
                assertEquals(expected = "getHolidaysForMonth", actual = request.url.parameters["action"])
                assertEquals(expected = "ru", actual = request.url.parameters["country"])
                assertEquals(expected = "2026", actual = request.url.parameters["year"])
                assertEquals(expected = "11", actual = request.url.parameters["month"])
                assertEquals(expected = "public_holiday", actual = request.url.parameters["holidayType"])
                if (fail) error(message = "Offline")
                respond(content = holidayJson(name = "Unity Day", day = 4, month = 11))
            }
            try {
                fun repository(): HolidayRepository {
                    return HolidayRepository(
                        api = EnricoApi(client = client),
                        locations = Locations(initial = city),
                        dao = lazyOf(value = saved),
                        dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    )
                }
                val first = repository().observeHolidays(
                    from = from,
                    through = through,
                ).first { calendar -> !calendar.isLoading }
                assertNull(actual = first.error)
                assertEquals(expected = "Unity Day", actual = first.holidays.single().name)
                assertEquals(
                    expected = LocalDate(year = 2026, month = 11, day = 4),
                    actual = first.holidays.single().date,
                )
                fail = true
                val restarted = repository().observeHolidays(
                    from = from,
                    through = through,
                ).first { calendar -> !calendar.isLoading }
                assertEquals(expected = first.holidays, actual = restarted.holidays)
                assertNull(actual = restarted.error)
                assertEquals(expected = 1, actual = requests)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `successful empty months are persisted while different months and locations load independently`(): TestResult {
        return runTest {
            val saved = Holidays()
            val locations = Locations(initial = city)
            val months = mutableListOf<String>()
            val client = holidayClient { request ->
                months += "${request.url.parameters["country"]}:${request.url.parameters["month"]}"
                respond(content = "[]")
            }
            try {
                fun repository(): HolidayRepository {
                    return HolidayRepository(
                        api = EnricoApi(client = client),
                        locations = locations,
                        dao = lazyOf(value = saved),
                        dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    )
                }
                assertTrue(
                    actual = repository()
                        .observeHolidays(from = from, through = through)
                        .first { calendar -> !calendar.isLoading }
                        .holidays
                        .isEmpty()
                )
                repository().observeHolidays(from = from, through = through).first { calendar -> !calendar.isLoading }
                repository()
                    .observeHolidays(
                        from = LocalDate(year = 2026, month = 12, day = 1),
                        through = LocalDate(year = 2026, month = 12, day = 31),
                    )
                    .first { calendar -> !calendar.isLoading }
                locations.setLocation(
                    location = city.copy(name = "Paris", countryCode = "FR", regionName = "Île-de-France")
                )
                repository().observeHolidays(from = from, through = through).first { calendar -> !calendar.isLoading }
                assertEquals(expected = listOf("ru:11", "ru:12", "fr:11"), actual = months)
                assertEquals(expected = 3, actual = saved.months.size)
                assertTrue(actual = saved.rows.value.isEmpty())
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `missing location and legacy location without country make no requests or database reads`(): TestResult {
        return runTest {
            val saved = Holidays()
            var requests = 0
            val client = holidayClient {
                requests++
                error(message = "Must not request holidays")
            }
            try {
                val locations = Locations(initial = null)
                val repository = HolidayRepository(
                    api = EnricoApi(client = client),
                    locations = locations,
                    dao = lazyOf(value = saved),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                assertNull(actual = repository.observeHolidays(from = from, through = through).first().location)
                locations.setLocation(location = city.copy(countryCode = null))
                val legacy = repository.observeHolidays(from = from, through = through).first()
                assertNotNull(actual = legacy.error)
                assertTrue(actual = legacy.holidays.isEmpty())
                assertEquals(expected = 0, actual = requests)
                assertEquals(expected = 0, actual = saved.snapshotReads)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `failures are not cached and retry leaves already fetched months available`(): TestResult {
        return runTest {
            val saved = Holidays()
            saved.replaceMonth(
                month = HolidayMonthEntity(locationKey = "RU", year = 2026, month = 11, fetchedAt = 1),
                holidays = listOf(
                    element = HolidayEntity(
                        locationKey = "RU",
                        year = 2026,
                        month = 11,
                        id = "cached",
                        date = LocalDate(year = 2026, month = 11, day = 4).toEpochDays(),
                        name = "Unity Day",
                        countryCode = "RU",
                    )
                ),
            )
            var requests = 0
            var fail = true
            val client = holidayClient { request ->
                requests++
                assertEquals(expected = "12", actual = request.url.parameters["month"])
                if (fail) {
                    respond(content = "unavailable", status = HttpStatusCode.ServiceUnavailable)
                } else {
                    respond(content = "[]")
                }
            }
            try {
                val repository = HolidayRepository(
                    api = EnricoApi(client = client),
                    locations = Locations(initial = city),
                    dao = lazyOf(value = saved),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                val failed = repository
                    .observeHolidays(from = from, through = LocalDate(year = 2026, month = 12, day = 31))
                    .first { calendar ->
                        !calendar.isLoading
                    }
                assertNotNull(actual = failed.error)
                assertEquals(expected = "Unity Day", actual = failed.holidays.single().name)
                assertNull(actual = saved.getMonth(locationKey = "RU", year = 2026, month = 12))
                fail = false
                val retried = repository
                    .observeHolidays(from = from, through = LocalDate(year = 2026, month = 12, day = 31))
                    .first { calendar ->
                        !calendar.isLoading
                    }
                assertNull(actual = retried.error)
                assertEquals(expected = failed.holidays, actual = retried.holidays)
                assertNotNull(actual = saved.getMonth(locationKey = "RU", year = 2026, month = 12))
                assertEquals(expected = 2, actual = requests)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `clearing or switching application location cannot leak holidays from previous country`(): TestResult {
        return runTest {
            val saved = Holidays()
            val locations = Locations(initial = city)
            val client = holidayClient { request ->
                respond(
                    content = holidayJson(
                        name = if (request.url.parameters["country"] == "ru") "Unity Day" else "Armistice Day",
                        day = 4,
                        month = 11,
                    )
                )
            }
            try {
                val repository = HolidayRepository(
                    api = EnricoApi(client = client),
                    locations = locations,
                    dao = lazyOf(value = saved),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                val states = Channel<HolidayCalendar>(capacity = Channel.UNLIMITED)
                backgroundScope.launch(context = UnconfinedTestDispatcher(scheduler = testScheduler)) {
                    repository.observeHolidays(
                        from = from,
                        through = through,
                    ).collect(collector = states::send)
                }
                suspend fun ready(name: String?): HolidayCalendar {
                    while (true) {
                        val state = states.receive()
                        if (state.location?.name == name && !state.isLoading) return state
                    }
                }
                assertEquals(expected = "Unity Day", actual = ready(name = city.name).holidays.single().name)
                val paris = city.copy(name = "Paris", countryCode = "FR", regionName = "Île-de-France")
                locations.setLocation(location = paris)
                val french = ready(name = paris.name)
                assertEquals(expected = "Armistice Day", actual = french.holidays.single().name)
                assertEquals(expected = "FR", actual = french.holidays.single().countryCode)
                locations.setLocation(location = null)
                assertTrue(actual = ready(name = null).holidays.isEmpty())
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `exact subdivision mapping is used and ambiguous locations never pick another region`(): TestResult {
        return runTest {
            val germany = city.copy(countryCode = "DE", regionName = "Bavaria")
            assertEquals(
                expected = HolidayLocation(countryCode = "DE", region = "by"),
                actual = holidayLocation(location = germany),
            )
            assertEquals(
                expected = HolidayLocation(countryCode = "US", region = "ca"),
                actual = holidayLocation(location = city.copy(countryCode = "US", regionName = "California")),
            )
            assertFailsWith<HolidayLocationException> {
                holidayLocation(location = germany.copy(regionName = "Unknown region"))
            }
            var calls = 0
            val client = holidayClient { request ->
                calls++
                assertEquals(expected = "de", actual = request.url.parameters["country"])
                assertEquals(expected = "by", actual = request.url.parameters["region"])
                respond(content = "[]")
            }
            try {
                val saved = Holidays()
                val locations = Locations(initial = germany)
                val repository = HolidayRepository(
                    api = EnricoApi(client = client),
                    locations = locations,
                    dao = lazyOf(value = saved),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                assertNull(
                    actual = repository.observeHolidays(
                        from = from,
                        through = through,
                    ).first { calendar -> !calendar.isLoading }.error
                )
                assertNotNull(actual = saved.getMonth(locationKey = "DE:by", year = 2026, month = 11))
                locations.setLocation(location = germany.copy(regionName = "Unknown region"))
                assertNotNull(actual = repository.observeHolidays(from = from, through = through).first().error)
                assertEquals(expected = 1, actual = calls)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `only public holidays covering the selected scope are accepted and date spans are expanded`() {
        val json = """
        [
            {
                "date": {
                    "year": 2026,
                    "month": 11,
                    "day": 1
                },
                "dateTo": {
                    "year": 2026,
                    "month": 11,
                    "day": 3
                },
                "name": [
                    {
                        "lang": "en",
                        "text": "Three-day holiday"
                    }
                ],
                "holidayType": "public_holiday"
            },
            {
                "date": {
                    "year": 2026,
                    "month": 11,
                    "day": 7
                },
                "observedOn": {
                    "year": 2026,
                    "month": 11,
                    "day": 9
                },
                "name": [
                    {
                        "lang": "en",
                        "text": "Observed holiday"
                    }
                ],
                "holidayType": "public_holiday"
            },
            {
                "date": {
                    "year": 2026,
                    "month": 11,
                    "day": 4
                },
                "name": [
                    {
                        "lang": "en",
                        "text": "Municipal-only holiday"
                    }
                ],
                "holidayType": "public_holiday",
                "flags": [
                    "REGIONAL_HOLIDAY"
                ]
            },
            {
                "date": {
                    "year": 2026,
                    "month": 11,
                    "day": 5
                },
                "name": [
                    {
                        "lang": "en",
                        "text": "Observance"
                    }
                ],
                "holidayType": "observance"
            }
        ]
        """
            .trimIndent()
        val days = parseHolidays(
            value = json,
            location = HolidayLocation(countryCode = "FR", region = null),
            month = YearMonth(year = 2026, month = 11),
        )
        assertEquals(expected = listOf(1, 2, 3, 9), actual = days.map { holiday -> holiday.date.day })
        assertEquals(expected = 4, actual = days.map(transform = PublicHoliday::id).distinct().size)
        assertFailsWith<HolidayLocationException> {
            parseHolidays(
                value = """{"error":"Unsupported country"}""",
                location = HolidayLocation(countryCode = "XX", region = null),
                month = YearMonth(year = 2026, month = 11),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            parseHolidays(
                value = holidayJson(name = "Bad date", day = 32, month = 11),
                location = HolidayLocation(countryCode = "RU", region = null),
                month = YearMonth(year = 2026, month = 11),
            )
        }
        assertEquals(
            expected = emptyList(),
            actual = parseHolidays(
                value = holidayJson(name = "Other month", day = 1, month = 12),
                location = HolidayLocation(countryCode = "RU", region = null),
                month = YearMonth(year = 2026, month = 11),
            ),
        )
    }

    @Test
    fun `closing observation cancels request without creating success marker`(): TestResult {
        return runTest {
            val saved = Holidays()
            val started = CompletableDeferred<Unit>()
            val client = holidayClient {
                started.complete(value = Unit)
                awaitCancellation()
            }
            try {
                val repository = HolidayRepository(
                    api = EnricoApi(client = client),
                    locations = Locations(initial = city),
                    dao = lazyOf(value = saved),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                val observation = backgroundScope.launch(
                    context = UnconfinedTestDispatcher(scheduler = testScheduler),
                ) {
                    repository.observeHolidays(from = from, through = through).collect()
                }
                started.await()
                observation.cancelAndJoin()
                assertTrue(actual = observation.isCancelled)
                assertTrue(actual = saved.months.isEmpty())
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `unsupported countries and dates never create an empty successful cache marker`(): TestResult {
        return runTest {
            var monthlyRequests = 0
            val client = holidayClient {
                monthlyRequests++
                respond(content = "[]")
            }
            try {
                val saved = Holidays()
                val locations = Locations(initial = city.copy(countryCode = "ZZ"))
                val repository = HolidayRepository(
                    api = EnricoApi(client = client),
                    locations = locations,
                    dao = lazyOf(value = saved),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                assertNotNull(
                    actual = repository.observeHolidays(
                        from = from,
                        through = through,
                    ).first { calendar -> !calendar.isLoading }.error
                )
                locations.setLocation(location = city)
                assertNotNull(
                    actual = repository
                        .observeHolidays(
                            from = LocalDate(year = 2000, month = 1, day = 1),
                            through = LocalDate(year = 2000, month = 1, day = 31),
                        )
                        .first { calendar -> !calendar.isLoading }
                        .error
                )
                assertEquals(expected = 0, actual = monthlyRequests)
                assertTrue(actual = saved.months.isEmpty())
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `country metadata is fetched only on a cache miss and reused for later uncached months`(): TestResult {
        return runTest {
            var metadataRequests = 0
            var monthRequests = 0
            val saved = Holidays()
            saved.replaceMonth(
                month = HolidayMonthEntity(locationKey = "RU", year = 2026, month = 11, fetchedAt = 1),
                holidays = emptyList(),
            )
            val client = HttpClient(
                engine = MockEngine { request ->
                    assertTrue(actual = saved.snapshotReads > 0)
                    if (request.url.parameters["action"] == "getSupportedCountries") {
                        metadataRequests++
                        respond(content = coverageJson)
                    } else {
                        monthRequests++
                        respond(content = "[]")
                    }
                }
            )
            try {
                val api = EnricoApi(client = client)
                val repository = HolidayRepository(
                    api = api,
                    locations = Locations(initial = city),
                    dao = lazyOf(value = saved),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                repository.observeHolidays(from = from, through = through).first { calendar -> !calendar.isLoading }
                assertEquals(expected = 0, actual = metadataRequests)
                assertEquals(expected = 0, actual = monthRequests)
                repository
                    .observeHolidays(
                        from = LocalDate(year = 2026, month = 12, day = 1),
                        through = LocalDate(year = 2026, month = 12, day = 31),
                    )
                    .first { calendar -> !calendar.isLoading }
                repository
                    .observeHolidays(
                        from = LocalDate(year = 2027, month = 1, day = 1),
                        through = LocalDate(year = 2027, month = 1, day = 31),
                    )
                    .first { calendar -> !calendar.isLoading }
                assertEquals(expected = 1, actual = metadataRequests)
                assertEquals(expected = 2, actual = monthRequests)
            } finally {
                client.close()
            }
        }
    }

    private fun holidayClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData):
        HttpClient {
        return HttpClient(
            engine = MockEngine { request ->
                if (request.url.parameters["action"] == "getSupportedCountries") {
                    respond(content = coverageJson)
                } else {
                    handler(request)
                }
            }
        )
    }

    private val coverageJson = """
    [
        {
            "countryCode": "rus",
            "regions": [

            ],
            "holidayTypes": [
                "public_holiday"
            ],
            "fromDate": {
                "year": 2011,
                "month": 1,
                "day": 1
            },
            "toDate": {
                "year": 2100,
                "month": 12,
                "day": 31
            }
        },
        {
            "countryCode": "fra",
            "regions": [

            ],
            "holidayTypes": [
                "public_holiday"
            ],
            "fromDate": {
                "year": 2011,
                "month": 1,
                "day": 1
            },
            "toDate": {
                "year": 2100,
                "month": 12,
                "day": 31
            }
        },
        {
            "countryCode": "deu",
            "regions": [
                "by",
                "bw"
            ],
            "holidayTypes": [
                "public_holiday"
            ],
            "fromDate": {
                "year": 2011,
                "month": 1,
                "day": 1
            },
            "toDate": {
                "year": 2100,
                "month": 12,
                "day": 31
            }
        },
        {
            "countryCode": "usa",
            "regions": [
                "ca"
            ],
            "holidayTypes": [
                "public_holiday"
            ],
            "fromDate": {
                "year": 2011,
                "month": 1,
                "day": 1
            },
            "toDate": {
                "year": 2100,
                "month": 12,
                "day": 31
            }
        }
    ]
    """
        .trimIndent()

    private fun holidayJson(name: String, day: Int, month: Int): String {
        return """
            [
                {
                    "date": {
                        "year": 2026,
                        "month": $month,
                        "day": $day
                    },
                    "name": [
                        {
                            "lang": "en",
                            "text": "$name"
                        }
                    ],
                    "holidayType": "public_holiday"
                }
            ]
        """
            .trimIndent()
    }

    private class Locations(initial: AppLocation?) : IAppLocationRepository {
        private val location = MutableStateFlow(value = initial)

        override fun observeLocation() = location

        override suspend fun searchLocations(query: String) = emptyList<AppLocation>()

        override suspend fun setLocation(location: AppLocation?) {
            this.location.value = location
        }
    }

    private class Holidays : IHolidayDao {
        val months = mutableMapOf<Triple<String, Int, Int>, HolidayMonthEntity>()
        val rows = MutableStateFlow<List<HolidayEntity>>(value = emptyList())
        var snapshotReads = 0

        override suspend fun getMonth(locationKey: String, year: Int, month: Int): HolidayMonthEntity? {
            snapshotReads++
            return months[Triple(first = locationKey, second = year, third = month)]
        }

        override suspend fun getHolidays(locationKey: String, fromDate: Long, throughDate: Long): List<HolidayEntity> {
            return rows.value.filter { holiday ->
                holiday.locationKey == locationKey && holiday.date in fromDate..throughDate
            }
        }

        override fun observeHolidays(locationKey: String, fromDate: Long, throughDate: Long):
            Flow<List<HolidayEntity>> {
            return rows.map { entries ->
                entries.filter { holiday ->
                    holiday.locationKey == locationKey && holiday.date in fromDate..throughDate
                }
            }
        }

        override suspend fun saveMonth(month: HolidayMonthEntity) {
            months[Triple(first = month.locationKey, second = month.year, third = month.month)] = month
        }

        override suspend fun deleteMonthHolidays(locationKey: String, year: Int, month: Int) {
            rows.value = rows.value.filterNot { holiday ->
                holiday.locationKey == locationKey && holiday.year == year && holiday.month == month
            }
        }

        override suspend fun insertHolidays(holidays: List<HolidayEntity>) {
            rows.value += holidays
        }

        override suspend fun replaceMonth(month: HolidayMonthEntity, holidays: List<HolidayEntity>) {
            saveMonth(month = month)
            rows.value = rows.value.filterNot { holiday ->
                holiday.locationKey == month.locationKey && holiday.year == month.year && holiday.month == month.month
            } + holidays
        }
    }
}
