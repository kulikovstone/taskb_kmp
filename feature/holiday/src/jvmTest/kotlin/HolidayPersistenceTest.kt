package com.mkulikov.taskb.feature.holiday

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.feature.holiday.network.EnricoApi
import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HolidayPersistenceTest {
    @Test
    fun `a fetched month and an empty month are reused after closing the database without HTTP`() = runBlocking {
        val directory = Files.createTempDirectory("taskb-holiday-cache")
        val file = directory.resolve("taskb.db")
        val city = AppLocation("Yekaterinburg", 56.84, 60.61, "Asia/Yekaterinburg", "RU", "Sverdlovsk Oblast")
        val selected = MutableStateFlow<AppLocation?>(city)
        val locations = object : IAppLocationRepository {
            override fun observeLocation() = selected
            override suspend fun searchLocations(query: String) = emptyList<AppLocation>()
            override suspend fun setLocation(location: AppLocation?) { selected.value = location }
        }
        var monthRequests = 0
        var metadataRequests = 0
        val online = HttpClient(MockEngine { request ->
            if (request.url.parameters["action"] == "getSupportedCountries") {
                metadataRequests++
                respond("""[{"countryCode":"rus","regions":[],"holidayTypes":["public_holiday"],"fromDate":{"year":2011,"month":1,"day":1},"toDate":{"year":32767,"month":12,"day":31}}]""")
            } else {
                monthRequests++
                respond(if (request.url.parameters["month"] == "11")
                    """[{"date":{"year":2026,"month":11,"day":4},"name":[{"lang":"en","text":"Unity Day"}],"holidayType":"public_holiday"}]"""
                else "[]")
            }
        })
        var offlineRequests = 0
        val offline = HttpClient(MockEngine { offlineRequests++; error("Cached months must not use HTTP") })
        try {
            val database = AppDatabase.open(file)
            try {
                val repository = HolidayRepository(EnricoApi(online), locations, lazy { database.holidayDao() })
                val november = repository.observeHolidays(LocalDate(2026, 11, 1), LocalDate(2026, 11, 30)).first { !it.isLoading }
                assertNull(november.error)
                assertEquals("Unity Day", november.holidays.single().name)
                val december = repository.observeHolidays(LocalDate(2026, 12, 1), LocalDate(2026, 12, 31)).first { !it.isLoading }
                assertNull(december.error)
                assertTrue(december.holidays.isEmpty())
                assertEquals(1, metadataRequests)
                assertEquals(2, monthRequests)
            } finally { database.close() }

            val reopened = AppDatabase.open(file)
            try {
                val repository = HolidayRepository(EnricoApi(offline), locations, lazy { reopened.holidayDao() })
                val november = repository.observeHolidays(LocalDate(2026, 11, 1), LocalDate(2026, 11, 30)).first { !it.isLoading }
                assertNull(november.error)
                assertEquals(LocalDate(2026, 11, 4), november.holidays.single().date)
                val december = repository.observeHolidays(LocalDate(2026, 12, 1), LocalDate(2026, 12, 31)).first { !it.isLoading }
                assertNull(december.error)
                assertTrue(december.holidays.isEmpty())
                assertNotNull(reopened.holidayDao().getMonth("RU", 2026, 12))
                assertEquals(0, offlineRequests)
            } finally { reopened.close() }
        } finally {
            online.close()
            offline.close()
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
