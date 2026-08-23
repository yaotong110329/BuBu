package com.kumo.bubu.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kumo.bubu.core.database.BuBuDatabase
import com.kumo.bubu.data.local.entity.VehicleEntity
import com.kumo.bubu.domain.model.VehicleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleDaoTest {
    private lateinit var database: BuBuDatabase
    private lateinit var dao: VehicleDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BuBuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.vehicleDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun insertUpdateAndDeleteAreVisibleInFlow() = runBlocking {
        val id = dao.insert(vehicleEntity("通勤車", "one"))
        val inserted = requireNotNull(dao.getById(id))
        assertEquals("通勤車", dao.observeAll().first().single().name)

        dao.update(inserted.copy(name = "日常用車", updatedAt = 2))
        assertEquals("日常用車", dao.observeAll().first().single().name)

        dao.delete(requireNotNull(dao.getById(id)))
        assertNull(dao.getById(id))
        assertEquals(emptyList<VehicleEntity>(), dao.observeAll().first())
    }

    @Test
    fun activeVehiclesSortBeforeArchivedVehicles() = runBlocking {
        dao.insert(vehicleEntity("A 封存", "archived").copy(isArchived = true))
        dao.insert(vehicleEntity("B 使用中", "active"))

        assertEquals(listOf("active", "archived"), dao.observeAll().first().map { it.publicId })
    }

    private fun vehicleEntity(name: String, publicId: String) = VehicleEntity(
        publicId = publicId,
        name = name,
        vehicleType = VehicleType.CAR,
        motorcycleClass = null,
        brand = null,
        model = null,
        manufactureYear = null,
        engineDisplacementCc = null,
        licensePlate = null,
        powertrainType = null,
        trackingStartDateEpochDay = 20_000,
        trackingStartOdometerKm = 100,
        currentOdometerKm = 100,
        note = null,
        isArchived = false,
        createdAt = 1,
        updatedAt = 1,
    )
}
