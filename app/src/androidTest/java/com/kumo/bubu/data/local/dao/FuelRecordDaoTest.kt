package com.kumo.bubu.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kumo.bubu.core.database.BuBuDatabase
import com.kumo.bubu.data.local.entity.FuelRecordEntity
import com.kumo.bubu.data.local.entity.VehicleEntity
import com.kumo.bubu.domain.model.VehicleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FuelRecordDaoTest {
    private lateinit var database: BuBuDatabase
    private lateinit var fuelDao: FuelRecordDao
    private lateinit var vehicleDao: VehicleDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BuBuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fuelDao = database.fuelRecordDao()
        vehicleDao = database.vehicleDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun recordsAreObservedInRecentOrderWithStableSameDaySequence() = runBlocking {
        val vehicleId = vehicleDao.insert(vehicle())
        fuelDao.insert(record(vehicleId, publicId = "first", date = 20_000, sequence = 0))
        fuelDao.insert(record(vehicleId, publicId = "second", date = 20_000, sequence = 1))
        fuelDao.insert(record(vehicleId, publicId = "older", date = 19_999, sequence = 0))

        assertEquals(listOf("second", "first", "older"), fuelDao.observeRecent().first().map { it.publicId })
        assertEquals(2, fuelDao.nextSequenceInDay(vehicleId, 20_000))
        assertEquals(1_234L, fuelDao.maxOdometerKm(vehicleId))
    }

    private fun vehicle() = VehicleEntity(
        publicId = "vehicle-public-id",
        name = "家用車",
        vehicleType = VehicleType.CAR,
        motorcycleClass = null,
        brand = null,
        model = null,
        manufactureYear = null,
        engineDisplacementCc = null,
        licensePlate = null,
        powertrainType = null,
        trackingStartDateEpochDay = 19_000,
        trackingStartOdometerKm = 100,
        currentOdometerKm = 100,
        note = null,
        isArchived = false,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun record(vehicleId: Long, publicId: String, date: Long, sequence: Int) = FuelRecordEntity(
        publicId = publicId,
        vehicleId = vehicleId,
        dateEpochDay = date,
        timeMinuteOfDay = null,
        sequenceInDay = sequence,
        odometerKm = 1_234,
        fuelVolumeMl = 4_270,
        pricePerLiterMilli = 31_700,
        totalCostTwd = 135,
        isFullTank = true,
        fuelProduct = null,
        note = null,
        createdAt = 1,
        updatedAt = 1,
    )
}
