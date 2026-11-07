package com.mkulikov.taskb.feature.holiday

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.feature.holiday.network.EnricoApi
import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

class HolidayPersistenceTest {
    @Test
    fun `a fetched month and an empty month are reused after closing the database without HTTP`() {
        runBlocking {
            val directory = Files.createTempDirectory("taskb-holiday-cache")
            val file = directory.resolve("taskb.db")
            val city = AppLocation(
                name = "Yekaterinburg",
                latitude = 56.84,
                longitude = 60.61,
                timeZone = "Asia/Yekaterinburg",
                countryCode = "RU",
                regionName = "Sverdlovsk Oblast",
            )
            val selected = MutableStateFlow<AppLocation?>(value = city)
            val locations =
                object : IAppLocationRepository {
                    override fun observeLocation() = selected

                    override suspend fun searchLocations(query: String) = emptyList<AppLocation>()

                    override suspend fun setLocation(location: AppLocation?) {
                        selected.value = location
                    }
                }
            var monthRequests = 0
            var metadataRequests = 0
            val online = HttpClient(
                engine =
                    MockEngine { request ->
                        if (request.url.parameters["action"] == "getSupportedCountries") {
                            metadataRequests++
                            respond(
                                content = """
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
                                            "year": 32767,
                                            "month": 12,
                                            "day": 31
                                        }
                                    }
                                ]
                                """
                                    .trimIndent()
                            )
                        } else {
                            monthRequests++
                            respond(
                                content = if (request.url.parameters["month"] == "11") {
                                    """
                                    [
                                        {
                                            "date": {
                                                "year": 2026,
                                                "month": 11,
                                                "day": 4
                                            },
                                            "name": [
                                                {
                                                    "lang": "en",
                                                    "text": "Unity Day"
                                                }
                                            ],
                                            "holidayType": "public_holiday"
                                        }
                                    ]
                                    """
                                        .trimIndent()
                                } else {
                                    "[]"
                                }
                            )
                        }
                    }
            )
            var offlineRequests = 0
            val offline = HttpClient(
                engine = MockEngine {
                    offlineRequests++
                    error(message = "Cached months must not use HTTP")
                }
            )
            try {
                val database = AppDatabase.open(file = file)
                try {
                    val repository = HolidayRepository(
                        api = EnricoApi(client = online),
                        locations = locations,
                        dao = lazy { database.holidayDao() },
                    )
                    val november = repository
                        .observeHolidays(
                            from = LocalDate(year = 2026, month = 11, day = 1),
                            through = LocalDate(year = 2026, month = 11, day = 30),
                        )
                        .first { calendar -> !calendar.isLoading }
                    assertNull(actual = november.error)
                    assertEquals(expected = "Unity Day", actual = november.holidays.single().name)
                    val december = repository
                        .observeHolidays(
                            from = LocalDate(year = 2026, month = 12, day = 1),
                            through = LocalDate(year = 2026, month = 12, day = 31),
                        )
                        .first { calendar -> !calendar.isLoading }
                    assertNull(actual = december.error)
                    assertTrue(actual = december.holidays.isEmpty())
                    assertEquals(expected = 1, actual = metadataRequests)
                    assertEquals(expected = 2, actual = monthRequests)
                } finally {
                    database.close()
                }

                val reopened = AppDatabase.open(file = file)
                try {
                    val repository = HolidayRepository(
                        api = EnricoApi(client = offline),
                        locations = locations,
                        dao = lazy { reopened.holidayDao() },
                    )
                    val november = repository
                        .observeHolidays(
                            from = LocalDate(year = 2026, month = 11, day = 1),
                            through = LocalDate(year = 2026, month = 11, day = 30),
                        )
                        .first { calendar -> !calendar.isLoading }
                    assertNull(actual = november.error)
                    assertEquals(
                        expected = LocalDate(year = 2026, month = 11, day = 4),
                        actual = november.holidays.single().date,
                    )
                    val december = repository
                        .observeHolidays(
                            from = LocalDate(year = 2026, month = 12, day = 1),
                            through = LocalDate(year = 2026, month = 12, day = 31),
                        )
                        .first { calendar -> !calendar.isLoading }
                    assertNull(actual = december.error)
                    assertTrue(actual = december.holidays.isEmpty())
                    assertNotNull(actual = reopened.holidayDao().getMonth(locationKey = "RU", year = 2026, month = 12))
                    assertEquals(expected = 0, actual = offlineRequests)
                } finally {
                    reopened.close()
                }
            } finally {
                online.close()
                offline.close()
                Files.walk(directory).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
                }
            }
        }
    }
}
