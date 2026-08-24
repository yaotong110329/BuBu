package com.kumo.bubu.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kumo.bubu.data.local.entity.FuelRecordEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BuBuDatabaseMigrationTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BuBuDatabase::class.java,
    )

    @Test
    fun migrationFromVehicleOnlyDatabasePreservesVehicleAndAddsFuelRecords() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DATABASE)
        createVersionOneDatabase(context)

        val database = Room.databaseBuilder(context, BuBuDatabase::class.java, TEST_DATABASE)
            .addMigrations(
                BuBuDatabase.MIGRATION_1_2,
                BuBuDatabase.MIGRATION_2_3,
                BuBuDatabase.MIGRATION_3_4,
                BuBuDatabase.migration4To5(TEST_MIGRATION_LABELS),
                BuBuDatabase.MIGRATION_5_6,
                BuBuDatabase.MIGRATION_6_7,
                BuBuDatabase.MIGRATION_7_8,
                BuBuDatabase.MIGRATION_8_9,
                BuBuDatabase.MIGRATION_9_10,
                BuBuDatabase.MIGRATION_10_11,
                BuBuDatabase.MIGRATION_11_12,
                BuBuDatabase.MIGRATION_12_13,
            )
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("家用車", database.vehicleDao().getById(1)?.name)
            database.fuelRecordDao().insert(
                FuelRecordEntity(
                    publicId = "fuel-public-id",
                    vehicleId = 1,
                    dateEpochDay = 20_001,
                    timeMinuteOfDay = null,
                    sequenceInDay = 0,
                    odometerKm = 150,
                    fuelVolumeMl = 4_270,
                    pricePerLiterMilli = null,
                    totalCostTwd = 135,
                    isFullTank = true,
                    fuelProduct = null,
                    note = null,
                    createdAt = 2,
                    updatedAt = 2,
                ),
            )
            assertEquals(1, database.fuelRecordDao().observeRecent().first().size)
        } finally {
            database.close()
            context.deleteDatabase(TEST_DATABASE)
        }
    }

    @Test
    fun migrationFromFourToFiveNormalizesItemsAndConvertsOnlyPositiveLabor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(V4_MIGRATION_DATABASE)
        migrationHelper.createDatabase(V4_MIGRATION_DATABASE, 4).use { database ->
            database.execSQL(
                """
                INSERT INTO vehicles (
                    id, publicId, name, vehicleType, motorcycleClass, brand, model,
                    manufactureYear, engineDisplacementCc, licensePlate, powertrainType,
                    trackingStartDateEpochDay, trackingStartOdometerKm, currentOdometerKm,
                    note, isArchived, createdAt, updatedAt
                ) VALUES (
                    1, 'vehicle-v4', '舊資料車', 'CAR', NULL, NULL, NULL,
                    NULL, NULL, NULL, NULL, 19000, 100, 1600, NULL, 0, 10, 10
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO service_records (
                    id, publicId, vehicleId, dateEpochDay, timeMinuteOfDay, sequenceInDay,
                    odometerKm, recordType, laborCostTwd, totalCostTwd, note, createdAt, updatedAt
                ) VALUES
                    (1, 'record-with-labor', 1, 20000, NULL, 0, 1500, 'MAINTENANCE', 350, 950, '舊工單', 20, 21),
                    (2, 'record-zero-labor', 1, 20001, NULL, 0, 1550, 'INSPECTION', 0, 200, NULL, 22, 23),
                    (3, 'record-null-labor', 1, 20002, NULL, 0, 1600, 'REPAIR', NULL, 77, '只有備註', 24, 25),
                    (4, 'record-later-oil', 1, 20003, NULL, 0, 1700, 'MAINTENANCE', NULL, 100, NULL, 26, 27)
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO service_items (
                    id, publicId, serviceRecordId, serviceTypeId, sequenceInRecord,
                    nameSnapshot, quantityMilli, quantityUnit, unitPriceTwd, subtotalTwd,
                    nextDueOdometerKm, nextDueDateEpochDay, note, createdAt, updatedAt
                ) VALUES
                    (11, 'legacy-oil-item', 1, NULL, 0, '機油', NULL, NULL, NULL, 600, 2500, NULL, '保留備註', 30, 31),
                    (12, 'legacy-inspection-item', 2, NULL, 0, '驗車', 1000, 'PIECE', 200, 200, NULL, 21000, NULL, 32, 33),
                    (13, 'legacy-later-oil-item', 4, NULL, 0, '機油', 1000, 'PIECE', 100, 100, NULL, NULL, NULL, 34, 35)
                """.trimIndent(),
            )
        }

        val database = migrationHelper.runMigrationsAndValidate(
            V4_MIGRATION_DATABASE,
            5,
            true,
            BuBuDatabase.migration4To5(TEST_MIGRATION_LABELS),
        )
        try {
            database.query("PRAGMA table_info(service_records)").use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                val columns = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(nameColumn))
                }
                assertTrue(columns.contains("title"))
                assertTrue(columns.contains("paymentMethod"))
                assertFalse(columns.contains("laborCostTwd"))
            }

            database.query(
                "SELECT title, paymentMethod, totalCostTwd FROM service_records WHERE id = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("機油", cursor.getString(0))
                assertNull(cursor.getString(1))
                assertEquals(950L, cursor.getLong(2))
            }
            database.query(
                "SELECT title, paymentMethod, totalCostTwd FROM service_records WHERE id = 3",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("維修工單", cursor.getString(0))
                assertNull(cursor.getString(1))
                assertEquals(77L, cursor.getLong(2))
            }

            database.query(
                """
                SELECT id, publicId, sequenceInRecord, nameSnapshot, quantityMilli,
                       quantityUnit, unitPriceTwd, subtotalTwd, nextDueOdometerKm, note
                FROM service_items
                WHERE serviceRecordId = 1
                ORDER BY sequenceInRecord ASC, id ASC
                """.trimIndent(),
            ).use { cursor ->
                assertEquals(2, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals(11L, cursor.getLong(0))
                assertEquals("legacy-oil-item", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
                assertEquals("機油", cursor.getString(3))
                assertEquals(1_000L, cursor.getLong(4))
                assertEquals("OTHER", cursor.getString(5))
                assertEquals(600L, cursor.getLong(6))
                assertEquals(600L, cursor.getLong(7))
                assertEquals(2_500L, cursor.getLong(8))
                assertEquals("保留備註", cursor.getString(9))

                assertTrue(cursor.moveToNext())
                assertEquals("migrated-labor-record-with-labor", cursor.getString(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals("工資", cursor.getString(3))
                assertEquals(1_000L, cursor.getLong(4))
                assertEquals("OTHER", cursor.getString(5))
                assertEquals(350L, cursor.getLong(6))
                assertEquals(350L, cursor.getLong(7))
            }

            assertEquals(1, scalarInt(database, "SELECT COUNT(*) FROM service_items WHERE serviceRecordId = 1 AND nameSnapshot = '工資'"))
            assertEquals(1, scalarInt(database, "SELECT COUNT(*) FROM service_items WHERE serviceRecordId = 2"))
            assertEquals(0, scalarInt(database, "SELECT COUNT(*) FROM service_items WHERE serviceRecordId = 2 AND nameSnapshot = '工資'"))
            assertEquals(1, scalarInt(database, "SELECT COUNT(*) FROM service_items WHERE serviceRecordId = 3"))
            assertEquals(
                1,
                scalarInt(
                    database,
                    "SELECT COUNT(*) FROM service_items WHERE serviceRecordId = 3 " +
                        "AND nameSnapshot = '舊工單金額調整' AND subtotalTwd = 77",
                ),
            )
            assertEquals(1, scalarInt(database, "SELECT COUNT(*) FROM vehicle_reminders WHERE sourceServiceItemId = 11 AND dueOdometerKm = 2500"))
            assertEquals(1, scalarInt(database, "SELECT COUNT(*) FROM vehicle_reminders WHERE sourceServiceItemId = 12 AND dueDateEpochDay = 21000"))
            database.query(
                "SELECT completedByServiceRecordId, completedAt FROM vehicle_reminders " +
                    "WHERE sourceServiceItemId = 11",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(4L, cursor.getLong(0))
                assertEquals(27L, cursor.getLong(1))
            }
            database.query("PRAGMA foreign_key_list(service_items)").use { cursor ->
                val tableColumn = cursor.getColumnIndexOrThrow("table")
                assertTrue(cursor.moveToFirst())
                assertEquals("service_records", cursor.getString(tableColumn))
            }
            database.query("PRAGMA foreign_key_check").use { cursor ->
                assertEquals(0, cursor.count)
            }
        } finally {
            database.close()
            context.deleteDatabase(V4_MIGRATION_DATABASE)
        }
    }

    @Test
    fun migrationFromFiveToSixPreservesServiceReminderAndAllowsManualReminder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(V5_MIGRATION_DATABASE)
        migrationHelper.createDatabase(V5_MIGRATION_DATABASE, 5).use { database ->
            database.execSQL(
                """
                INSERT INTO vehicles (
                    id, publicId, name, vehicleType, trackingStartDateEpochDay,
                    trackingStartOdometerKm, currentOdometerKm, isArchived, createdAt, updatedAt
                ) VALUES (1, 'vehicle-v5', 'Vehicle', 'CAR', 19000, 100, 5000, 0, 1, 1)
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO service_records (
                    id, publicId, vehicleId, dateEpochDay, timeMinuteOfDay, sequenceInDay,
                    odometerKm, recordType, title, paymentMethod, totalCostTwd, note, createdAt, updatedAt
                ) VALUES (1, 'record-v5', 1, 20000, NULL, 0, 5000, 'MAINTENANCE', 'Oil', NULL, 500, NULL, 1, 1)
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO service_items (
                    id, publicId, serviceRecordId, serviceTypeId, sequenceInRecord, nameSnapshot,
                    quantityMilli, quantityUnit, unitPriceTwd, subtotalTwd, nextDueOdometerKm,
                    nextDueDateEpochDay, note, createdAt, updatedAt
                ) VALUES (1, 'item-v5', 1, NULL, 0, 'Oil', 1000, 'PIECE', 500, 500, 7000, NULL, NULL, 1, 1)
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO vehicle_reminders (
                    id, publicId, vehicleId, sourceServiceItemId, title, dueOdometerKm,
                    dueDateEpochDay, completedByServiceRecordId, completedAt, isEnabled, createdAt, updatedAt
                ) VALUES (1, 'reminder-v5', 1, 1, 'Oil', 7000, NULL, NULL, NULL, 1, 1, 1)
                """.trimIndent(),
            )
        }

        val database = migrationHelper.runMigrationsAndValidate(
            V5_MIGRATION_DATABASE,
            6,
            true,
            BuBuDatabase.MIGRATION_5_6,
        )
        try {
            database.query(
                "SELECT source, sourceServiceItemId, snoozedUntilEpochDay FROM vehicle_reminders WHERE id = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("SERVICE_ITEM", cursor.getString(0))
                assertEquals(1L, cursor.getLong(1))
                assertTrue(cursor.isNull(2))
            }
            database.execSQL(
                """
                INSERT INTO vehicle_reminders (
                    publicId, vehicleId, source, sourceServiceItemId, title, dueOdometerKm,
                    dueDateEpochDay, completedByServiceRecordId, completedAt, snoozedUntilEpochDay,
                    isEnabled, createdAt, updatedAt
                ) VALUES ('manual-v6', 1, 'MANUAL', NULL, 'Inspection', NULL, 21000, NULL, NULL, NULL, 1, 2, 2)
                """.trimIndent(),
            )
            assertEquals(1, scalarInt(database, "SELECT COUNT(*) FROM vehicle_reminders WHERE source = 'MANUAL'"))
            database.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        } finally {
            database.close()
            context.deleteDatabase(V5_MIGRATION_DATABASE)
        }
    }

    @Test
    fun migrationFromSixToSevenAddsEmptyNotificationStatus() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(V6_MIGRATION_DATABASE)
        migrationHelper.createDatabase(V6_MIGRATION_DATABASE, 6).use { database ->
            database.execSQL(
                """
                INSERT INTO vehicles (
                    id, publicId, name, vehicleType, trackingStartDateEpochDay,
                    trackingStartOdometerKm, currentOdometerKm, isArchived, createdAt, updatedAt
                ) VALUES (1, 'vehicle-v6', 'Vehicle', 'CAR', 19000, 100, 5000, 0, 1, 1)
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO vehicle_reminders (
                    id, publicId, vehicleId, source, sourceServiceItemId, title, dueOdometerKm,
                    dueDateEpochDay, completedByServiceRecordId, completedAt, snoozedUntilEpochDay,
                    isEnabled, createdAt, updatedAt
                ) VALUES (1, 'reminder-v6', 1, 'MANUAL', NULL, 'Inspection', NULL, 21000, NULL, NULL, NULL, 1, 2, 2)
                """.trimIndent(),
            )
        }

        val database = migrationHelper.runMigrationsAndValidate(
            V6_MIGRATION_DATABASE,
            7,
            true,
            BuBuDatabase.MIGRATION_6_7,
        )
        try {
            database.query("SELECT lastNotifiedStatus FROM vehicle_reminders WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
            }
        } finally {
            database.close()
            context.deleteDatabase(V6_MIGRATION_DATABASE)
        }
    }

    @Test
    fun migrationFromSevenToEightAddsStatutoryAndForecastFieldsWithoutChangingExistingReminder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(V7_MIGRATION_DATABASE)
        migrationHelper.createDatabase(V7_MIGRATION_DATABASE, 7).use { database ->
            database.execSQL(
                """
                INSERT INTO vehicles (
                    id, publicId, name, vehicleType, trackingStartDateEpochDay,
                    trackingStartOdometerKm, currentOdometerKm, isArchived, createdAt, updatedAt
                ) VALUES (1, 'vehicle-v7', 'Vehicle', 'CAR', 19000, 100, 5000, 0, 1, 1)
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO vehicle_reminders (
                    id, publicId, vehicleId, source, sourceServiceItemId, title, dueOdometerKm,
                    dueDateEpochDay, completedByServiceRecordId, completedAt, snoozedUntilEpochDay,
                    lastNotifiedStatus, isEnabled, createdAt, updatedAt
                ) VALUES (1, 'reminder-v7', 1, 'MANUAL', NULL, 'Oil', 7000, NULL, NULL, NULL, NULL,
                    'DUE_SOON', 1, 2, 2)
                """.trimIndent(),
            )
        }

        val database = migrationHelper.runMigrationsAndValidate(
            V7_MIGRATION_DATABASE,
            8,
            true,
            BuBuDatabase.MIGRATION_7_8,
        )
        try {
            database.query(
                "SELECT title, lastNotifiedTrigger, automaticKey, estimatedNotificationEpochDay, " +
                    "completedByExpenseRecordId " +
                    "FROM vehicle_reminders WHERE id = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Oil", cursor.getString(0))
                assertEquals("status:DUE_SOON", cursor.getString(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
                assertTrue(cursor.isNull(4))
            }
            database.query(
                "SELECT primaryInspectionMonthDay, secondaryInspectionMonthDay FROM vehicles WHERE id = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertTrue(cursor.isNull(1))
            }
            database.query("SELECT completedReminderId FROM expense_records LIMIT 1").use { cursor ->
                assertFalse(cursor.moveToFirst())
            }
        } finally {
            database.close()
            context.deleteDatabase(V7_MIGRATION_DATABASE)
        }
    }

    @Test
    fun migrationFromEightToNinePreservesServiceTypesAndClassifiesLegacyRowsAsCars() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(V8_MIGRATION_DATABASE)
        migrationHelper.createDatabase(V8_MIGRATION_DATABASE, 8).use { database ->
            database.execSQL(
                """
                INSERT INTO service_types (
                    id, publicId, name, isBuiltIn, isArchived, sortOrder, createdAt, updatedAt
                ) VALUES (1, 'custom-oil', '機油', 0, 0, 3, 1, 1)
                """.trimIndent(),
            )
        }

        val database = migrationHelper.runMigrationsAndValidate(
            V8_MIGRATION_DATABASE,
            9,
            true,
            BuBuDatabase.MIGRATION_8_9,
        )
        try {
            database.query("SELECT name, vehicleType FROM service_types WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("機油", cursor.getString(0))
                assertEquals("CAR", cursor.getString(1))
            }
        } finally {
            database.close()
            context.deleteDatabase(V8_MIGRATION_DATABASE)
        }
    }

    @Test
    fun migrationFromNineToTenDefaultsExistingFuelRecordsToFullService() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(V9_MIGRATION_DATABASE)
        migrationHelper.createDatabase(V9_MIGRATION_DATABASE, 9).use { database ->
            database.execSQL("INSERT INTO vehicles (id, publicId, name, vehicleType, trackingStartDateEpochDay, trackingStartOdometerKm, currentOdometerKm, isArchived, createdAt, updatedAt) VALUES (1, 'vehicle-v9', 'Vehicle', 'CAR', 19000, 0, 0, 0, 1, 1)")
            database.execSQL("INSERT INTO fuel_records (id, publicId, vehicleId, dateEpochDay, timeMinuteOfDay, sequenceInDay, odometerKm, fuelVolumeMl, pricePerLiterMilli, totalCostTwd, isFullTank, fuelProduct, note, createdAt, updatedAt) VALUES (1, 'fuel-v9', 1, 20000, 600, 0, 1000, 1000, 28900, 29, 1, 'GASOLINE_95', NULL, 1, 1)")
        }
        val database = migrationHelper.runMigrationsAndValidate(
            V9_MIGRATION_DATABASE, 10, true, BuBuDatabase.MIGRATION_9_10,
        )
        try {
            database.query("SELECT fuelingMode FROM fuel_records WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("FULL_SERVICE", cursor.getString(0))
            }
        } finally {
            database.close()
            context.deleteDatabase(V9_MIGRATION_DATABASE)
        }
    }

    @Test
    fun migrationFromTwelveToThirteenKeepsExistingFuelRecordsIncludedByDefault() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(V12_MIGRATION_DATABASE)
        migrationHelper.createDatabase(V12_MIGRATION_DATABASE, 12).use { database ->
            database.execSQL("INSERT INTO vehicles (id, publicId, name, vehicleType, trackingStartDateEpochDay, trackingStartOdometerKm, currentOdometerKm, isArchived, createdAt, updatedAt) VALUES (1, 'vehicle-v12', 'Vehicle', 'CAR', 19000, 0, 0, 0, 1, 1)")
            database.execSQL("INSERT INTO fuel_records (id, publicId, vehicleId, dateEpochDay, timeMinuteOfDay, sequenceInDay, odometerKm, fuelVolumeMl, pricePerLiterMilli, totalCostTwd, isFullTank, fuelProduct, fuelingMode, note, createdAt, updatedAt) VALUES (1, 'fuel-v12', 1, 20000, 600, 0, 1000, 1000, 28900, 29, 1, 'GASOLINE_95', 'FULL_SERVICE', NULL, 1, 1)")
        }

        val database = migrationHelper.runMigrationsAndValidate(
            V12_MIGRATION_DATABASE, 13, true, BuBuDatabase.MIGRATION_12_13,
        )
        try {
            database.query("SELECT fuelEconomyStatisticsStatus FROM fuel_records WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("UNREVIEWED", cursor.getString(0))
            }
        } finally {
            database.close()
            context.deleteDatabase(V12_MIGRATION_DATABASE)
        }
    }

    private fun scalarInt(database: androidx.sqlite.db.SupportSQLiteDatabase, query: String): Int =
        database.query(query).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun createVersionOneDatabase(context: Context) {
        context.openOrCreateDatabase(TEST_DATABASE, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL(
                "CREATE TABLE vehicles (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "publicId TEXT NOT NULL, name TEXT NOT NULL, vehicleType TEXT NOT NULL, " +
                    "motorcycleClass TEXT, brand TEXT, model TEXT, manufactureYear INTEGER, " +
                    "engineDisplacementCc INTEGER, licensePlate TEXT, powertrainType TEXT, " +
                    "trackingStartDateEpochDay INTEGER NOT NULL, trackingStartOdometerKm INTEGER NOT NULL, " +
                    "currentOdometerKm INTEGER NOT NULL, note TEXT, isArchived INTEGER NOT NULL, " +
                    "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
            )
            database.execSQL("CREATE UNIQUE INDEX index_vehicles_publicId ON vehicles (publicId)")
            database.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            database.execSQL(
                "INSERT INTO room_master_table (id, identity_hash) VALUES(42, '70aa3e07a9960e808c84f157174e80cd')",
            )
            database.execSQL(
                "INSERT INTO vehicles (publicId, name, vehicleType, trackingStartDateEpochDay, " +
                    "trackingStartOdometerKm, currentOdometerKm, isArchived, createdAt, updatedAt) " +
                    "VALUES ('vehicle-public-id', '家用車', 'CAR', 20000, 100, 100, 0, 1, 1)",
            )
            database.version = 1
        }
    }

    private companion object {
        const val TEST_DATABASE = "bubu-migration-test"
        const val V4_MIGRATION_DATABASE = "bubu-v4-v5-migration-test"
        const val V5_MIGRATION_DATABASE = "bubu-v5-v6-migration-test"
        const val V6_MIGRATION_DATABASE = "bubu-v6-v7-migration-test"
        const val V7_MIGRATION_DATABASE = "bubu-v7-v8-migration-test"
        const val V8_MIGRATION_DATABASE = "bubu-v8-v9-migration-test"
        const val V9_MIGRATION_DATABASE = "bubu-v9-v10-migration-test"
        const val V12_MIGRATION_DATABASE = "bubu-v12-v13-migration-test"
        val TEST_MIGRATION_LABELS = ServiceMigrationLabels(
            maintenanceTitle = "保養工單",
            repairTitle = "維修工單",
            inspectionTitle = "檢驗工單",
            genericTitle = "服務工單",
            laborItem = "工資",
            legacyTotalAdjustmentItem = "舊工單金額調整",
        )
    }
}
