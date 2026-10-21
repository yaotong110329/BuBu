package com.kumo.bubu.data.backup

import com.kumo.bubu.data.backup.BackupArchiveTestData.completeData
import com.kumo.bubu.data.backup.BackupArchiveTestData.writeArchive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupArchiveCompatibilityTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun acceptsHistoricalMotorcycleItemsReferencingTheFormerSharedServiceType() {
        val original = completeData()
        val data = original.copy(
            vehicles = listOf(original.vehicles.single().copy(vehicleType = "MOTORCYCLE", motorcycleClass = "ORDINARY_HEAVY")),
            serviceReminderPreferences = emptyList(),
        )
        val encoded = BackupArchiveTestData.encodeWithDefaults(data)
        val legacy = JsonObject(encoded + ("serviceTypes" to JsonArray(encoded.getValue("serviceTypes").jsonArray.map {
            JsonObject(it.jsonObject - "vehicleType")
        })))
        listOf(encoded, legacy).forEachIndexed { index, json ->
            val restored = BackupArchiveReader.read(temporary.writeArchive(data, "historical-motorcycle-$index", json.toString().encodeToByteArray())).data
            assertEquals("MOTORCYCLE", restored.vehicles.single().vehicleType)
            assertEquals("CAR", restored.serviceTypes.single().vehicleType)
            assertEquals("type", restored.serviceItems.single().serviceTypePublicId)
            assertEquals("service", restored.serviceItems.single().serviceRecordPublicId)
        }
    }

    @Test fun acceptsSavedPoliciesAfterVehicleTypeIsEdited() {
        val valid = completeData()
        // Vehicle editing preserves the vehicle's existing policies and service history.
        val edited = valid.copy(vehicles = listOf(valid.vehicles.single().copy(
            vehicleType = "MOTORCYCLE", motorcycleClass = "ORDINARY_HEAVY",
        )))
        val restored = BackupArchiveReader.read(temporary.writeArchive(edited, "edited-vehicle-policy")).data

        assertEquals("MOTORCYCLE", restored.vehicles.single().vehicleType)
        assertEquals("CAR", restored.serviceTypes.single().vehicleType)
        assertEquals(valid.serviceReminderPreferences, restored.serviceReminderPreferences)
        assertEquals(valid.serviceItems, restored.serviceItems)
    }

    @Test fun legacyFormatOneMissingAdditiveFieldsKeepsItsEstablishedDefaults() {
        val data = completeData()
        val root = BackupArchiveTestData.encodeWithDefaults(data).toMutableMap()
        fun omit(collection: String, names: Set<String>) {
            root[collection] = JsonArray(root.getValue(collection).jsonArray.map { JsonObject(it.jsonObject.filterKeys { key -> key !in names }) })
        }
        omit("vehicles", setOf("useNewVehicleMaintenanceBaseline"))
        omit("serviceTypes", setOf("vehicleType"))
        omit("fuelRecords", setOf("fuelingMode", "fuelEconomyStatisticsStatus"))
        omit("serviceReminderPreferences", setOf("intervalMonths", "reminderType", "baseOdometerKm"))
        val legacy = temporary.writeArchive(data, "legacy-additive-defaults", JsonObject(root).toString().encodeToByteArray())
        val restored = BackupArchiveReader.read(legacy).data

        assertEquals(false, restored.vehicles.single().useNewVehicleMaintenanceBaseline)
        assertEquals("CAR", restored.serviceTypes.single().vehicleType)
        assertEquals("FULL_SERVICE", restored.fuelRecords.single().fuelingMode)
        assertEquals("UNREVIEWED", restored.fuelRecords.single().fuelEconomyStatisticsStatus)
        assertEquals(null, restored.serviceReminderPreferences.single().intervalMonths)
        assertEquals("REPLACE", restored.serviceReminderPreferences.single().reminderType)
        assertEquals(null, restored.serviceReminderPreferences.single().baseOdometerKm)

        root.remove("serviceReminderPreferences")
        val oldest = temporary.writeArchive(data, "legacy-without-policies", JsonObject(root).toString().encodeToByteArray())
        assertEquals(emptyList<BackupServiceReminderPreference>(), BackupArchiveReader.read(oldest).data.serviceReminderPreferences)
    }

}
