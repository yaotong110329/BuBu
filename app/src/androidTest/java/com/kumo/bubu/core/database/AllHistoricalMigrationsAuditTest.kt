package com.kumo.bubu.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AllHistoricalMigrationsAuditTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BuBuDatabase::class.java)

    @Test fun everyHistoricalSchemaReachesCurrentWithStableIdentityAndValidForeignKeys() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val labels = ServiceMigrationLabels("Maintenance", "Repair", "Inspection", "Service", "Labor", "Legacy adjustment")
        val migrations = arrayOf(BuBuDatabase.MIGRATION_1_2, BuBuDatabase.MIGRATION_2_3,
            BuBuDatabase.MIGRATION_3_4, BuBuDatabase.migration4To5(labels), BuBuDatabase.MIGRATION_5_6,
            BuBuDatabase.MIGRATION_6_7, BuBuDatabase.MIGRATION_7_8, BuBuDatabase.MIGRATION_8_9,
            BuBuDatabase.MIGRATION_9_10, BuBuDatabase.MIGRATION_10_11, BuBuDatabase.MIGRATION_11_12,
            BuBuDatabase.MIGRATION_12_13, BuBuDatabase.MIGRATION_13_14, BuBuDatabase.MIGRATION_14_15)
        for (from in 1..14) {
            val name = "audit-migration-$from-15"
            context.deleteDatabase(name)
            try {
                helper.createDatabase(name, from).use { db ->
                    db.execSQL("INSERT INTO vehicles (id,publicId,name,vehicleType,trackingStartDateEpochDay,trackingStartOdometerKm,currentOdometerKm,isArchived,createdAt,updatedAt) VALUES (1,'00000000-0000-0000-0000-000000000001','Vehicle','CAR',20000,100,200,0,1,2)")
                    if (from >= 2) {
                        val fuelColumns = (if (from >= 10) ",fuelingMode" else "") + (if (from >= 13) ",fuelEconomyStatisticsStatus" else "")
                        val fuelValues = (if (from >= 10) ",'SELF_SERVICE'" else "") + (if (from >= 13) ",'EXCLUDED'" else "")
                        db.execSQL("INSERT INTO fuel_records (id,publicId,vehicleId,dateEpochDay,sequenceInDay,odometerKm,fuelVolumeMl,totalCostTwd,isFullTank,createdAt,updatedAt$fuelColumns) VALUES (1,'00000000-0000-0000-0000-000000000002',1,20000,0,200,4270,135,1,1,2$fuelValues)")
                    }
                    if (from >= 3) {
                        val titleColumn = if (from >= 5) ",title" else ""
                        val titleValue = if (from >= 5) ",'Service'" else ""
                        db.execSQL("INSERT INTO service_records (id,publicId,vehicleId,dateEpochDay,sequenceInDay,odometerKm,recordType,totalCostTwd,createdAt,updatedAt$titleColumn) VALUES (1,'00000000-0000-0000-0000-000000000003',1,20000,1,200,'MAINTENANCE',600,1,2$titleValue)")
                        db.execSQL("INSERT INTO service_items (id,publicId,serviceRecordId,sequenceInRecord,nameSnapshot,quantityMilli,quantityUnit,unitPriceTwd,subtotalTwd,createdAt,updatedAt) VALUES (1,'00000000-0000-0000-0000-000000000004',1,0,'Item',1500,'LITER',400,600,1,2)")
                    }
                    if (from >= 4) db.execSQL("INSERT INTO expense_records (id,publicId,vehicleId,dateEpochDay,sequenceInDay,category,totalCostTwd,createdAt,updatedAt) VALUES (1,'00000000-0000-0000-0000-000000000005',1,20000,0,'INSURANCE',0,1,2)")
                    if (from >= 5) {
                        val sourceColumn = if (from >= 6) ",source" else ""
                        val sourceValue = if (from >= 6) ",'SERVICE_ITEM'" else ""
                        db.execSQL("INSERT INTO vehicle_reminders (id,publicId,vehicleId,sourceServiceItemId,title,dueOdometerKm,isEnabled,createdAt,updatedAt$sourceColumn) VALUES (1,'00000000-0000-0000-0000-000000000006',1,1,'Reminder',5000,1,1,2$sourceValue)")
                        db.execSQL("INSERT INTO service_attachments (id,publicId,serviceRecordId,sequenceInRecord,relativePath,displayName,mimeType,createdAt,updatedAt) VALUES (1,'00000000-0000-0000-0000-000000000007',1,0,'attachments/service/a.png','a.png','image/png',1,2)")
                    }
                    if (from >= 11) {
                        db.execSQL("INSERT INTO service_types (id,publicId,name,vehicleType,isBuiltIn,isArchived,sortOrder,createdAt,updatedAt) VALUES (1,'00000000-0000-0000-0000-000000000008','Custom','CAR',0,0,1,1,2)")
                        db.execSQL("UPDATE service_items SET serviceTypeId=1 WHERE id=1")
                        val additionalColumn = if (from >= 14) ",intervalMonths,reminderType" else ""
                        val additionalValue = if (from >= 14) ",3,'INSPECT'" else ""
                        db.execSQL("INSERT INTO vehicle_service_reminder_preferences (id,publicId,vehicleId,serviceTypeId,isEnabled,intervalKm,sortOrder,createdAt,updatedAt$additionalColumn) VALUES (1,'00000000-0000-0000-0000-000000000009',1,1,0,5000,2,1,2$additionalValue)")
                    }
                }
                helper.runMigrationsAndValidate(name, 15, true, *migrations).use { db ->
                    db.query("PRAGMA foreign_key_check").use { assertEquals("from=$from", 0, it.count) }
                    db.query("SELECT publicId,currentOdometerKm,useNewVehicleMaintenanceBaseline FROM vehicles WHERE id=1").use {
                        assertTrue(it.moveToFirst()); assertEquals("00000000-0000-0000-0000-000000000001", it.getString(0)); assertEquals(200L,it.getLong(1)); assertEquals(0,it.getInt(2))
                    }
                    val tables = listOf("fuel_records" to 2, "service_records" to 3, "service_items" to 3,
                        "expense_records" to 4, "vehicle_reminders" to 5, "service_attachments" to 5,
                        "service_types" to 11, "vehicle_service_reminder_preferences" to 11)
                    val uuidEnds = mapOf("fuel_records" to 2, "service_records" to 3, "service_items" to 4,
                        "expense_records" to 5, "vehicle_reminders" to 6, "service_attachments" to 7,
                        "service_types" to 8, "vehicle_service_reminder_preferences" to 9)
                    tables.filter { from >= it.second }.forEach { (table, _) ->
                        db.query("SELECT publicId,createdAt,updatedAt FROM $table WHERE id=1").use {
                            assertTrue("from=$from table=$table",it.moveToFirst())
                            assertEquals("00000000-0000-0000-0000-00000000000${uuidEnds.getValue(table)}",it.getString(0))
                            assertEquals(1L,it.getLong(1)); assertEquals(2L,it.getLong(2))
                        }
                    }
                    if (from >= 11) db.query("SELECT intervalKm,isEnabled,intervalMonths,reminderType FROM vehicle_service_reminder_preferences WHERE id=1").use {
                        assertTrue(it.moveToFirst()); assertEquals(5000L,it.getLong(0)); assertEquals(0,it.getInt(1))
                        if (from >= 14) { assertEquals(3L,it.getLong(2)); assertEquals("INSPECT",it.getString(3)) }
                    }
                    if (from >= 3) db.query("SELECT totalCostTwd FROM service_records WHERE id=1").use {
                        assertTrue(it.moveToFirst()); assertEquals(600L,it.getLong(0))
                    }
                }
            } finally { context.deleteDatabase(name) }
        }
    }
}
