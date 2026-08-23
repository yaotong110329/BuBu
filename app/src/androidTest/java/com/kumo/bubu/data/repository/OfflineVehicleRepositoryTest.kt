package com.kumo.bubu.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kumo.bubu.core.database.BuBuDatabase
import com.kumo.bubu.domain.model.VehicleInput
import com.kumo.bubu.domain.model.VehicleType
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineVehicleRepositoryTest {
    private lateinit var database: BuBuDatabase
    private lateinit var dataStoreFile: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var repository: OfflineVehicleRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BuBuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataStoreFile = File(context.cacheDir, "vehicle-test-${UUID.randomUUID()}.preferences_pb")
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { dataStoreFile },
        )
        repository = OfflineVehicleRepository(database.vehicleDao(), database.fuelRecordDao(), database.serviceRecordDao(), dataStore)
    }

    @After
    fun tearDown() {
        database.close()
        dataStoreScope.cancel()
        dataStoreFile.delete()
    }

    @Test
    fun selectionPersistsAndInvalidCurrentFallsBackAcrossVehicleActions() = runBlocking {
        val firstId = repository.createVehicle(input("第一台"))
        val firstGarage = repository.observeGarage().first()
        val firstPublicId = firstGarage.currentVehiclePublicId
        assertNotNull(firstPublicId)

        val secondId = repository.createVehicle(input("第二台"))
        val secondPublicId = requireNotNull(repository.getVehicle(secondId)).publicId
        repository.selectCurrentVehicle(secondPublicId)

        dataStoreScope.coroutineContext[Job]?.cancelAndJoin()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val restartedRepository = OfflineVehicleRepository(
            database.vehicleDao(),
            database.fuelRecordDao(),
            database.serviceRecordDao(),
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = { dataStoreFile },
            ),
        )
        assertEquals(secondPublicId, restartedRepository.observeGarage().first().currentVehiclePublicId)

        restartedRepository.setVehicleArchived(secondId, true)
        assertEquals(firstPublicId, restartedRepository.observeGarage().first().currentVehiclePublicId)

        restartedRepository.setVehicleArchived(secondId, false)
        restartedRepository.deleteUnreferencedVehicle(firstId)
        assertEquals(secondPublicId, restartedRepository.observeGarage().first().currentVehiclePublicId)
    }

    private fun input(name: String) = VehicleInput(
        name = name,
        vehicleType = VehicleType.CAR,
        motorcycleClass = null,
        trackingStartDateEpochDay = 20_000,
        trackingStartOdometerKm = 0,
    )
}
