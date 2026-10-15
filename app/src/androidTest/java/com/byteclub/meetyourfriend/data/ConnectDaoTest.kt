package com.byteclub.meetyourfriend.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ConnectDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: ConnectDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = database.connectDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun upsertFindByMobileAndObserveNewestFirst() = runBlocking {
        val older = Connect(
            name = "First",
            organization = "One",
            role = "",
            mobileE164 = "+15550000001",
            metAt = Instant.parse("2024-01-01T00:00:00Z"),
        )
        val newer = Connect(
            name = "Second",
            organization = "Two",
            role = "",
            mobileE164 = "+15550000002",
            metAt = Instant.parse("2024-02-01T00:00:00Z"),
        )
        dao.upsert(older)
        dao.upsert(newer)

        assertNotNull(dao.findByMobile("+15550000001"))
        assertEquals(listOf("Second", "First"), dao.observeAll().first().map { it.name })
    }
}
