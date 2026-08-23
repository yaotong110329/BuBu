package com.kumo.bubu.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kumo.bubu.core.database.BuBuDatabase
import com.kumo.bubu.data.local.entity.VehicleEntity
import com.kumo.bubu.data.local.entity.ServiceRecordEntity
import com.kumo.bubu.domain.model.FuelRecordInput
import com.kumo.bubu.domain.model.ServiceRecordType
import com.kumo.bubu.domain.model.VehicleType
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineFuelRepositoryTest {
    private lateinit var database: BuBuDatabase
    private lateinit var dataStoreFile: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var repository: OfflineFuelRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BuBuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataStoreFile = File(context.cacheDir, "fuel-test-${UUID.randomUUID()}.preferences_pb")
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = OfflineFuelRepository(
            database,
            database.vehicleDao(),
            database.fuelRecordDao(),
            database.serviceRecordDao(),
            PreferenceDataStoreFactory.create(scope = dataStoreScope, produceFile = { dataStoreFile }),
        )
    }

    @After
    fun closeDatabase() {
        database.close()
        dataStoreScope.cancel()
        dataStoreFile.delete()
    }

    @Test
    fun newHigherOdometerUpdatesVehicleButOlderEntryDoesNotLowerIt() = runBlocking {
        val vehicleId = database.vehicleDao().insert(vehicle())

        repository.createFuelRecord(input(vehicleId, date = 20_000, odometer = 1_500))
        repository.createFuelRecord(input(vehicleId, date = 19_000, odometer = 900))

        assertEquals(1_500L, requireNotNull(database.vehicleDao().getById(vehicleId)).currentOdometerKm)
        assertEquals(listOf(1_500L, 900L), repository.observeRecentFuelRecords().first().map { it.odometerKm })
        assertEquals(true, repository.getLastFullTankSetting("vehicle-public-id"))
    }

    @Test
    fun editingOrDeletingHighestRecordRebuildsCurrentOdometerFromRemainingFuelRecords() = runBlocking {
        val vehicleId = database.vehicleDao().insert(vehicle())
        val highestId = repository.createFuelRecord(input(vehicleId, date = 20_000, odometer = 1_500))
        repository.createFuelRecord(input(vehicleId, date = 19_000, odometer = 900))

        repository.updateFuelRecord(highestId, input(vehicleId, date = 20_000, odometer = 1_200))
        assertEquals(1_200L, requireNotNull(database.vehicleDao().getById(vehicleId)).currentOdometerKm)

        repository.deleteFuelRecord(highestId)
        assertEquals(900L, requireNotNull(database.vehicleDao().getById(vehicleId)).currentOdometerKm)

        val remainingId = requireNotNull(repository.observeRecentFuelRecords().first().singleOrNull()).id
        repository.deleteFuelRecord(remainingId)
        assertEquals(100L, requireNotNull(database.vehicleDao().getById(vehicleId)).currentOdometerKm)
    }

    @Test
    fun deletingHighestFuelRecordKeepsHigherServiceOdometer() = runBlocking {
        val vehicleId = database.vehicleDao().insert(vehicle())
        database.serviceRecordDao().insert(
            ServiceRecordEntity(
                publicId = "service-odometer-source",
                vehicleId = vehicleId,
                dateEpochDay = 19_500,
                timeMinuteOfDay = null,
                sequenceInDay = 0,
                odometerKm = 1_400,
                recordType = ServiceRecordType.MAINTENANCE,
                title = "保養",
                paymentMethod = null,
                totalCostTwd = 0,
                note = null,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        val fuelId = repository.createFuelRecord(input(vehicleId, date = 20_000, odometer = 1_500))

        repository.deleteFuelRecord(fuelId)

        assertEquals(1_400L, requireNotNull(database.vehicleDao().getById(vehicleId)).currentOdometerKm)
    }

    @Test
    fun odometerNeighborsUseTheDateAndConfirmedSameDaySequence() = runBlocking {
        val vehicleId = database.vehicleDao().insert(vehicle())
        repository.createFuelRecord(input(vehicleId, date = 20_000, odometer = 1_000))
        repository.createFuelRecord(input(vehicleId, date = 20_100, odometer = 1_500))

        val neighbors = repository.getOdometerNeighbors(input(vehicleId, date = 20_050, odometer = 900), null)

        assertEquals(1_000L, neighbors.previous?.odometerKm)
        assertEquals(1_500L, neighbors.next?.odometerKm)
        assertEquals(true, neighbors.breaksOrder(900))
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
        trackingStartDateEpochDay = 18_000,
        trackingStartOdometerKm = 100,
        currentOdometerKm = 100,
        note = null,
        isArchived = false,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun input(vehicleId: Long, date: Long, odometer: Long) = FuelRecordInput(
        vehicleId = vehicleId,
        dateEpochDay = date,
        timeMinuteOfDay = null,
        odometerKm = odometer,
        fuelVolumeMl = 4_270,
        pricePerLiterMilli = 31_700,
        totalCostTwd = 135,
        isFullTank = true,
    )
}
