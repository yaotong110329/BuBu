package com.kumo.bubu.core.database

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverters
import com.kumo.bubu.data.local.converter.VehicleTypeConverters
import com.kumo.bubu.data.local.dao.FuelRecordDao
import com.kumo.bubu.data.local.dao.PendingAttachmentDeletionDao
import com.kumo.bubu.data.local.dao.ServiceAttachmentDao
import com.kumo.bubu.data.local.dao.VehicleDao
import com.kumo.bubu.data.local.dao.ServiceItemDao
import com.kumo.bubu.data.local.dao.ServiceRecordDao
import com.kumo.bubu.data.local.dao.ServiceTypeDao
import com.kumo.bubu.data.local.dao.ExpenseRecordDao
import com.kumo.bubu.data.local.dao.ReportDao
import com.kumo.bubu.data.local.dao.VehicleReminderDao
import com.kumo.bubu.data.local.entity.PendingAttachmentDeletionEntity
import com.kumo.bubu.data.local.entity.ServiceAttachmentEntity
import com.kumo.bubu.data.local.entity.ServiceTypeEntity
import com.kumo.bubu.data.local.entity.ExpenseRecordEntity
import com.kumo.bubu.data.local.entity.FuelRecordEntity
import com.kumo.bubu.data.local.entity.ServiceItemEntity
import com.kumo.bubu.data.local.entity.ServiceRecordEntity
import com.kumo.bubu.data.local.entity.VehicleEntity
import com.kumo.bubu.data.local.entity.VehicleReminderEntity

data class ServiceMigrationLabels(
    val maintenanceTitle: String,
    val repairTitle: String,
    val inspectionTitle: String,
    val genericTitle: String,
    val laborItem: String,
    val legacyTotalAdjustmentItem: String,
)

@Database(
    entities = [
        VehicleEntity::class,
        FuelRecordEntity::class,
        ServiceRecordEntity::class,
        ServiceItemEntity::class,
        ServiceTypeEntity::class,
        ExpenseRecordEntity::class,
        ServiceAttachmentEntity::class,
        VehicleReminderEntity::class,
        PendingAttachmentDeletionEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
@TypeConverters(VehicleTypeConverters::class)
abstract class BuBuDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao

    abstract fun fuelRecordDao(): FuelRecordDao
    abstract fun serviceRecordDao(): ServiceRecordDao
    abstract fun serviceItemDao(): ServiceItemDao
    abstract fun serviceTypeDao(): ServiceTypeDao
    abstract fun expenseRecordDao(): ExpenseRecordDao
    abstract fun reportDao(): ReportDao
    abstract fun serviceAttachmentDao(): ServiceAttachmentDao
    abstract fun vehicleReminderDao(): VehicleReminderDao
    abstract fun pendingAttachmentDeletionDao(): PendingAttachmentDeletionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fuel_records` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`publicId` TEXT NOT NULL, " +
                        "`vehicleId` INTEGER NOT NULL, " +
                        "`dateEpochDay` INTEGER NOT NULL, " +
                        "`timeMinuteOfDay` INTEGER, " +
                        "`sequenceInDay` INTEGER NOT NULL, " +
                        "`odometerKm` INTEGER NOT NULL, " +
                        "`fuelVolumeMl` INTEGER NOT NULL, " +
                        "`pricePerLiterMilli` INTEGER, " +
                        "`totalCostTwd` INTEGER NOT NULL, " +
                        "`isFullTank` INTEGER NOT NULL, " +
                        "`fuelProduct` TEXT, " +
                        "`note` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT" +
                        ")",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_fuel_records_publicId` ON `fuel_records` (`publicId`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_fuel_records_vehicleId_dateEpochDay_timeMinuteOfDay_sequenceInDay` " +
                        "ON `fuel_records` (`vehicleId`, `dateEpochDay`, `timeMinuteOfDay`, `sequenceInDay`)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `service_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `publicId` TEXT NOT NULL, `vehicleId` INTEGER NOT NULL, `dateEpochDay` INTEGER NOT NULL, `timeMinuteOfDay` INTEGER, `sequenceInDay` INTEGER NOT NULL, `odometerKm` INTEGER NOT NULL, `recordType` TEXT NOT NULL, `laborCostTwd` INTEGER, `totalCostTwd` INTEGER NOT NULL, `note` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_service_records_publicId` ON `service_records` (`publicId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_records_vehicleId_dateEpochDay_timeMinuteOfDay_sequenceInDay` ON `service_records` (`vehicleId`, `dateEpochDay`, `timeMinuteOfDay`, `sequenceInDay`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `service_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `publicId` TEXT NOT NULL, `serviceRecordId` INTEGER NOT NULL, `sequenceInRecord` INTEGER NOT NULL, `nameSnapshot` TEXT NOT NULL, `quantityMilli` INTEGER, `quantityUnit` TEXT, `unitPriceTwd` INTEGER, `subtotalTwd` INTEGER, `nextDueOdometerKm` INTEGER, `nextDueDateEpochDay` INTEGER, `note` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`serviceRecordId`) REFERENCES `service_records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_service_items_publicId` ON `service_items` (`publicId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_items_serviceRecordId_sequenceInRecord` ON `service_items` (`serviceRecordId`, `sequenceInRecord`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE service_items ADD COLUMN serviceTypeId INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS `service_types` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `publicId` TEXT NOT NULL, `name` TEXT NOT NULL, `isBuiltIn` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_service_types_publicId` ON `service_types` (`publicId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_service_types_name` ON `service_types` (`name`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `expense_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `publicId` TEXT NOT NULL, `vehicleId` INTEGER NOT NULL, `dateEpochDay` INTEGER NOT NULL, `timeMinuteOfDay` INTEGER, `sequenceInDay` INTEGER NOT NULL, `category` TEXT NOT NULL, `totalCostTwd` INTEGER NOT NULL, `note` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_expense_records_publicId` ON `expense_records` (`publicId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_records_vehicleId_dateEpochDay_timeMinuteOfDay_sequenceInDay` ON `expense_records` (`vehicleId`, `dateEpochDay`, `timeMinuteOfDay`, `sequenceInDay`)")
            }
        }

        fun migration4To5(labels: ServiceMigrationLabels): Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `service_types` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `service_types` SET `sortOrder` = `id`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `service_records_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `publicId` TEXT NOT NULL,
                        `vehicleId` INTEGER NOT NULL,
                        `dateEpochDay` INTEGER NOT NULL,
                        `timeMinuteOfDay` INTEGER,
                        `sequenceInDay` INTEGER NOT NULL,
                        `odometerKm` INTEGER NOT NULL,
                        `recordType` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `paymentMethod` TEXT,
                        `totalCostTwd` INTEGER NOT NULL,
                        `note` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `service_records_new` (
                        `id`, `publicId`, `vehicleId`, `dateEpochDay`, `timeMinuteOfDay`,
                        `sequenceInDay`, `odometerKm`, `recordType`, `title`, `paymentMethod`,
                        `totalCostTwd`, `note`, `createdAt`, `updatedAt`
                    )
                    SELECT
                        record.`id`, record.`publicId`, record.`vehicleId`, record.`dateEpochDay`,
                        record.`timeMinuteOfDay`, record.`sequenceInDay`, record.`odometerKm`,
                        record.`recordType`,
                        COALESCE(
                            (
                                SELECT NULLIF(TRIM(item.`nameSnapshot`), '')
                                FROM `service_items` AS item
                                WHERE item.`serviceRecordId` = record.`id`
                                ORDER BY item.`sequenceInRecord` ASC, item.`id` ASC
                                LIMIT 1
                            ),
                            CASE record.`recordType`
                                WHEN 'MAINTENANCE' THEN ?
                                WHEN 'REPAIR' THEN ?
                                WHEN 'INSPECTION' THEN ?
                                ELSE ?
                            END
                        ),
                        NULL,
                        record.`totalCostTwd`, record.`note`, record.`createdAt`, record.`updatedAt`
                    FROM `service_records` AS record
                    """.trimIndent(),
                    arrayOf(
                        labels.maintenanceTitle,
                        labels.repairTitle,
                        labels.inspectionTitle,
                        labels.genericTitle,
                    ),
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `service_items_staging` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `publicId` TEXT NOT NULL,
                        `serviceRecordId` INTEGER NOT NULL,
                        `serviceTypeId` INTEGER,
                        `sequenceInRecord` INTEGER NOT NULL,
                        `nameSnapshot` TEXT NOT NULL,
                        `quantityMilli` INTEGER NOT NULL,
                        `quantityUnit` TEXT NOT NULL,
                        `unitPriceTwd` INTEGER NOT NULL,
                        `subtotalTwd` INTEGER NOT NULL,
                        `nextDueOdometerKm` INTEGER,
                        `nextDueDateEpochDay` INTEGER,
                        `note` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `service_items_staging` (
                        `id`, `publicId`, `serviceRecordId`, `serviceTypeId`, `sequenceInRecord`,
                        `nameSnapshot`, `quantityMilli`, `quantityUnit`, `unitPriceTwd`,
                        `subtotalTwd`, `nextDueOdometerKm`, `nextDueDateEpochDay`, `note`,
                        `createdAt`, `updatedAt`
                    )
                    SELECT
                        item.`id`, item.`publicId`, item.`serviceRecordId`, item.`serviceTypeId`,
                        item.`sequenceInRecord`, item.`nameSnapshot`,
                        CASE WHEN
                            item.`quantityMilli` IS NOT NULL AND item.`quantityMilli` > 0 AND
                            item.`quantityUnit` IS NOT NULL AND
                            item.`unitPriceTwd` IS NOT NULL AND item.`unitPriceTwd` >= 0 AND
                            item.`subtotalTwd` IS NOT NULL AND item.`subtotalTwd` >= 0 AND
                            item.`subtotalTwd` = ((item.`quantityMilli` * item.`unitPriceTwd` + 500) / 1000)
                        THEN item.`quantityMilli` ELSE 1000 END,
                        CASE WHEN
                            item.`quantityMilli` IS NOT NULL AND item.`quantityMilli` > 0 AND
                            item.`quantityUnit` IS NOT NULL AND
                            item.`unitPriceTwd` IS NOT NULL AND item.`unitPriceTwd` >= 0 AND
                            item.`subtotalTwd` IS NOT NULL AND item.`subtotalTwd` >= 0 AND
                            item.`subtotalTwd` = ((item.`quantityMilli` * item.`unitPriceTwd` + 500) / 1000)
                        THEN item.`quantityUnit` ELSE 'OTHER' END,
                        CASE WHEN
                            item.`quantityMilli` IS NOT NULL AND item.`quantityMilli` > 0 AND
                            item.`quantityUnit` IS NOT NULL AND
                            item.`unitPriceTwd` IS NOT NULL AND item.`unitPriceTwd` >= 0 AND
                            item.`subtotalTwd` IS NOT NULL AND item.`subtotalTwd` >= 0 AND
                            item.`subtotalTwd` = ((item.`quantityMilli` * item.`unitPriceTwd` + 500) / 1000)
                        THEN item.`unitPriceTwd`
                        WHEN item.`subtotalTwd` IS NOT NULL AND item.`subtotalTwd` >= 0
                        THEN item.`subtotalTwd`
                        WHEN item.`unitPriceTwd` IS NOT NULL AND item.`unitPriceTwd` >= 0
                        THEN item.`unitPriceTwd`
                        ELSE 0 END,
                        CASE WHEN
                            item.`quantityMilli` IS NOT NULL AND item.`quantityMilli` > 0 AND
                            item.`quantityUnit` IS NOT NULL AND
                            item.`unitPriceTwd` IS NOT NULL AND item.`unitPriceTwd` >= 0 AND
                            item.`subtotalTwd` IS NOT NULL AND item.`subtotalTwd` >= 0 AND
                            item.`subtotalTwd` = ((item.`quantityMilli` * item.`unitPriceTwd` + 500) / 1000)
                        THEN item.`subtotalTwd`
                        WHEN item.`subtotalTwd` IS NOT NULL AND item.`subtotalTwd` >= 0
                        THEN item.`subtotalTwd`
                        WHEN item.`unitPriceTwd` IS NOT NULL AND item.`unitPriceTwd` >= 0
                        THEN item.`unitPriceTwd`
                        ELSE 0 END,
                        item.`nextDueOdometerKm`, item.`nextDueDateEpochDay`, item.`note`,
                        item.`createdAt`, item.`updatedAt`
                    FROM `service_items` AS item
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `service_items_staging` (
                        `publicId`, `serviceRecordId`, `serviceTypeId`, `sequenceInRecord`,
                        `nameSnapshot`, `quantityMilli`, `quantityUnit`, `unitPriceTwd`,
                        `subtotalTwd`, `nextDueOdometerKm`, `nextDueDateEpochDay`, `note`,
                        `createdAt`, `updatedAt`
                    )
                    SELECT
                        'migrated-labor-' || record.`publicId`, record.`id`, NULL,
                        COALESCE(
                            (
                                SELECT MAX(item.`sequenceInRecord`) + 1
                                FROM `service_items` AS item
                                WHERE item.`serviceRecordId` = record.`id`
                            ),
                            0
                        ),
                        ?, 1000, 'OTHER', record.`laborCostTwd`, record.`laborCostTwd`,
                        NULL, NULL, NULL, record.`createdAt`, record.`updatedAt`
                    FROM `service_records` AS record
                    WHERE record.`laborCostTwd` IS NOT NULL AND record.`laborCostTwd` > 0
                    """.trimIndent(),
                    arrayOf(labels.laborItem),
                )
                db.execSQL(
                    """
                    UPDATE `service_items_staging`
                    SET `unitPriceTwd` = 0, `subtotalTwd` = 0
                    WHERE `serviceRecordId` IN (
                        SELECT record.`id`
                        FROM `service_records` AS record
                        WHERE COALESCE(
                            (
                                SELECT SUM(item.`subtotalTwd`)
                                FROM `service_items_staging` AS item
                                WHERE item.`serviceRecordId` = record.`id`
                            ),
                            0
                        ) > MAX(record.`totalCostTwd`, 0)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `service_items_staging` (
                        `publicId`, `serviceRecordId`, `serviceTypeId`, `sequenceInRecord`,
                        `nameSnapshot`, `quantityMilli`, `quantityUnit`, `unitPriceTwd`,
                        `subtotalTwd`, `nextDueOdometerKm`, `nextDueDateEpochDay`, `note`,
                        `createdAt`, `updatedAt`
                    )
                    SELECT
                        'migrated-total-' || record.`publicId`, record.`id`, NULL,
                        COALESCE(
                            (
                                SELECT MAX(item.`sequenceInRecord`) + 1
                                FROM `service_items_staging` AS item
                                WHERE item.`serviceRecordId` = record.`id`
                            ),
                            0
                        ),
                        ?, 1000, 'OTHER',
                        MAX(record.`totalCostTwd`, 0) - COALESCE(
                            (
                                SELECT SUM(item.`subtotalTwd`)
                                FROM `service_items_staging` AS item
                                WHERE item.`serviceRecordId` = record.`id`
                            ),
                            0
                        ),
                        MAX(record.`totalCostTwd`, 0) - COALESCE(
                            (
                                SELECT SUM(item.`subtotalTwd`)
                                FROM `service_items_staging` AS item
                                WHERE item.`serviceRecordId` = record.`id`
                            ),
                            0
                        ),
                        NULL, NULL, NULL, record.`createdAt`, record.`updatedAt`
                    FROM `service_records` AS record
                    WHERE NOT EXISTS (
                        SELECT 1 FROM `service_items_staging` AS item
                        WHERE item.`serviceRecordId` = record.`id`
                    ) OR MAX(record.`totalCostTwd`, 0) != COALESCE(
                        (
                            SELECT SUM(item.`subtotalTwd`)
                            FROM `service_items_staging` AS item
                            WHERE item.`serviceRecordId` = record.`id`
                        ),
                        0
                    )
                    """.trimIndent(),
                    arrayOf(labels.legacyTotalAdjustmentItem),
                )
                db.execSQL(
                    """
                    UPDATE `service_records_new`
                    SET `totalCostTwd` = COALESCE(
                        (
                            SELECT SUM(item.`subtotalTwd`)
                            FROM `service_items_staging` AS item
                            WHERE item.`serviceRecordId` = `service_records_new`.`id`
                        ),
                        0
                    )
                    """.trimIndent(),
                )

                db.execSQL("DROP TABLE `service_items`")
                db.execSQL("DROP TABLE `service_records`")
                db.execSQL("ALTER TABLE `service_records_new` RENAME TO `service_records`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `service_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `publicId` TEXT NOT NULL,
                        `serviceRecordId` INTEGER NOT NULL,
                        `serviceTypeId` INTEGER,
                        `sequenceInRecord` INTEGER NOT NULL,
                        `nameSnapshot` TEXT NOT NULL,
                        `quantityMilli` INTEGER NOT NULL,
                        `quantityUnit` TEXT NOT NULL,
                        `unitPriceTwd` INTEGER NOT NULL,
                        `subtotalTwd` INTEGER NOT NULL,
                        `nextDueOdometerKm` INTEGER,
                        `nextDueDateEpochDay` INTEGER,
                        `note` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`serviceRecordId`) REFERENCES `service_records`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `service_items` (
                        `id`, `publicId`, `serviceRecordId`, `serviceTypeId`, `sequenceInRecord`,
                        `nameSnapshot`, `quantityMilli`, `quantityUnit`, `unitPriceTwd`,
                        `subtotalTwd`, `nextDueOdometerKm`, `nextDueDateEpochDay`, `note`,
                        `createdAt`, `updatedAt`
                    )
                    SELECT
                        `id`, `publicId`, `serviceRecordId`, `serviceTypeId`, `sequenceInRecord`,
                        `nameSnapshot`, `quantityMilli`, `quantityUnit`, `unitPriceTwd`,
                        `subtotalTwd`, `nextDueOdometerKm`, `nextDueDateEpochDay`, `note`,
                        `createdAt`, `updatedAt`
                    FROM `service_items_staging`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `service_items_staging`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_service_records_publicId` " +
                        "ON `service_records` (`publicId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_service_records_vehicleId_dateEpochDay_timeMinuteOfDay_sequenceInDay` " +
                        "ON `service_records` (`vehicleId`, `dateEpochDay`, `timeMinuteOfDay`, `sequenceInDay`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_service_items_publicId` " +
                        "ON `service_items` (`publicId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_service_items_serviceRecordId_sequenceInRecord` " +
                        "ON `service_items` (`serviceRecordId`, `sequenceInRecord`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `service_attachments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `publicId` TEXT NOT NULL,
                        `serviceRecordId` INTEGER NOT NULL,
                        `sequenceInRecord` INTEGER NOT NULL,
                        `relativePath` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `mimeType` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`serviceRecordId`) REFERENCES `service_records`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_service_attachments_publicId` ON `service_attachments` (`publicId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_service_attachments_relativePath` ON `service_attachments` (`relativePath`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_attachments_serviceRecordId_sequenceInRecord` ON `service_attachments` (`serviceRecordId`, `sequenceInRecord`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vehicle_reminders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `publicId` TEXT NOT NULL,
                        `vehicleId` INTEGER NOT NULL,
                        `sourceServiceItemId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `dueOdometerKm` INTEGER,
                        `dueDateEpochDay` INTEGER,
                        `completedByServiceRecordId` INTEGER,
                        `completedAt` INTEGER,
                        `isEnabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`sourceServiceItemId`) REFERENCES `service_items`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`completedByServiceRecordId`) REFERENCES `service_records`(`id`)
                            ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicle_reminders_publicId` ON `vehicle_reminders` (`publicId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vehicle_reminders_vehicleId` ON `vehicle_reminders` (`vehicleId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicle_reminders_sourceServiceItemId` ON `vehicle_reminders` (`sourceServiceItemId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vehicle_reminders_completedByServiceRecordId` ON `vehicle_reminders` (`completedByServiceRecordId`)")
                db.execSQL(
                    """
                    INSERT INTO `vehicle_reminders` (
                        `publicId`, `vehicleId`, `sourceServiceItemId`, `title`,
                        `dueOdometerKm`, `dueDateEpochDay`, `isEnabled`, `createdAt`, `updatedAt`
                    )
                    SELECT
                        'migrated-reminder-' || item.`publicId`, record.`vehicleId`, item.`id`,
                        item.`nameSnapshot`, item.`nextDueOdometerKm`, item.`nextDueDateEpochDay`,
                        1, item.`createdAt`, item.`updatedAt`
                    FROM `service_items` AS item
                    INNER JOIN `service_records` AS record ON record.`id` = item.`serviceRecordId`
                    WHERE item.`nextDueOdometerKm` IS NOT NULL OR item.`nextDueDateEpochDay` IS NOT NULL
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    UPDATE `vehicle_reminders`
                    SET `completedByServiceRecordId` = (
                        SELECT laterRecord.`id`
                        FROM `service_items` AS sourceItem
                        INNER JOIN `service_records` AS sourceRecord
                            ON sourceRecord.`id` = sourceItem.`serviceRecordId`
                        INNER JOIN `service_items` AS laterItem
                            ON (
                                (
                                    sourceItem.`serviceTypeId` IS NOT NULL
                                    AND laterItem.`serviceTypeId` = sourceItem.`serviceTypeId`
                                )
                                OR (
                                    sourceItem.`serviceTypeId` IS NULL
                                    AND laterItem.`serviceTypeId` IS NULL
                                    AND laterItem.`nameSnapshot` = sourceItem.`nameSnapshot`
                                )
                            )
                        INNER JOIN `service_records` AS laterRecord
                            ON laterRecord.`id` = laterItem.`serviceRecordId`
                        WHERE sourceItem.`id` = `vehicle_reminders`.`sourceServiceItemId`
                          AND laterRecord.`vehicleId` = `vehicle_reminders`.`vehicleId`
                          AND (
                              laterRecord.`dateEpochDay` > sourceRecord.`dateEpochDay`
                              OR (
                                  laterRecord.`dateEpochDay` = sourceRecord.`dateEpochDay`
                                  AND COALESCE(laterRecord.`timeMinuteOfDay`, -1) >
                                      COALESCE(sourceRecord.`timeMinuteOfDay`, -1)
                              )
                              OR (
                                  laterRecord.`dateEpochDay` = sourceRecord.`dateEpochDay`
                                  AND COALESCE(laterRecord.`timeMinuteOfDay`, -1) =
                                      COALESCE(sourceRecord.`timeMinuteOfDay`, -1)
                                  AND laterRecord.`sequenceInDay` > sourceRecord.`sequenceInDay`
                              )
                              OR (
                                  laterRecord.`dateEpochDay` = sourceRecord.`dateEpochDay`
                                  AND COALESCE(laterRecord.`timeMinuteOfDay`, -1) =
                                      COALESCE(sourceRecord.`timeMinuteOfDay`, -1)
                                  AND laterRecord.`sequenceInDay` = sourceRecord.`sequenceInDay`
                                  AND laterRecord.`id` > sourceRecord.`id`
                              )
                          )
                        ORDER BY laterRecord.`dateEpochDay` ASC,
                                 COALESCE(laterRecord.`timeMinuteOfDay`, -1) ASC,
                                 laterRecord.`sequenceInDay` ASC,
                                 laterRecord.`id` ASC
                        LIMIT 1
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    UPDATE `vehicle_reminders`
                    SET `completedAt` = (
                        SELECT completionRecord.`updatedAt`
                        FROM `service_records` AS completionRecord
                        WHERE completionRecord.`id` =
                            `vehicle_reminders`.`completedByServiceRecordId`
                    )
                    WHERE `completedByServiceRecordId` IS NOT NULL
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_attachment_deletions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `relativePath` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_attachment_deletions_relativePath` ON `pending_attachment_deletions` (`relativePath`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vehicle_reminders_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `publicId` TEXT NOT NULL,
                        `vehicleId` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `sourceServiceItemId` INTEGER,
                        `title` TEXT NOT NULL,
                        `dueOdometerKm` INTEGER,
                        `dueDateEpochDay` INTEGER,
                        `completedByServiceRecordId` INTEGER,
                        `completedAt` INTEGER,
                        `snoozedUntilEpochDay` INTEGER,
                        `isEnabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`sourceServiceItemId`) REFERENCES `service_items`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`completedByServiceRecordId`) REFERENCES `service_records`(`id`)
                            ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `vehicle_reminders_new` (
                        `id`, `publicId`, `vehicleId`, `source`, `sourceServiceItemId`, `title`,
                        `dueOdometerKm`, `dueDateEpochDay`, `completedByServiceRecordId`,
                        `completedAt`, `snoozedUntilEpochDay`, `isEnabled`, `createdAt`, `updatedAt`
                    )
                    SELECT
                        `id`, `publicId`, `vehicleId`, 'SERVICE_ITEM', `sourceServiceItemId`, `title`,
                        `dueOdometerKm`, `dueDateEpochDay`, `completedByServiceRecordId`,
                        `completedAt`, NULL, `isEnabled`, `createdAt`, `updatedAt`
                    FROM `vehicle_reminders`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `vehicle_reminders`")
                db.execSQL("ALTER TABLE `vehicle_reminders_new` RENAME TO `vehicle_reminders`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicle_reminders_publicId` ON `vehicle_reminders` (`publicId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vehicle_reminders_vehicleId` ON `vehicle_reminders` (`vehicleId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicle_reminders_sourceServiceItemId` ON `vehicle_reminders` (`sourceServiceItemId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vehicle_reminders_completedByServiceRecordId` ON `vehicle_reminders` (`completedByServiceRecordId`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `vehicle_reminders` ADD COLUMN `lastNotifiedStatus` TEXT")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `primaryInspectionMonthDay` TEXT")
                db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `secondaryInspectionMonthDay` TEXT")
                db.execSQL("ALTER TABLE `vehicle_reminders` ADD COLUMN `automaticKey` TEXT")
                db.execSQL("ALTER TABLE `vehicle_reminders` ADD COLUMN `ruleVersion` INTEGER")
                db.execSQL("ALTER TABLE `vehicle_reminders` ADD COLUMN `ruleVerifiedEpochDay` INTEGER")
                db.execSQL("ALTER TABLE `vehicle_reminders` ADD COLUMN `estimatedNotificationEpochDay` INTEGER")
                db.execSQL("ALTER TABLE `vehicle_reminders` ADD COLUMN `lastNotifiedTrigger` TEXT")
                db.execSQL("ALTER TABLE `vehicle_reminders` ADD COLUMN `referenceDateEpochDay` INTEGER")
                db.execSQL("ALTER TABLE `vehicle_reminders` ADD COLUMN `completedByExpenseRecordId` INTEGER")
                db.execSQL("ALTER TABLE `expense_records` ADD COLUMN `completedReminderId` INTEGER")
                db.execSQL(
                    "UPDATE `vehicle_reminders` SET `lastNotifiedTrigger` = " +
                        "'status:' || `lastNotifiedStatus` WHERE `lastNotifiedStatus` IS NOT NULL",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicle_reminders_automaticKey` " +
                        "ON `vehicle_reminders` (`automaticKey`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_expense_records_completedReminderId` " +
                        "ON `expense_records` (`completedReminderId`)",
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `service_types` ADD COLUMN `vehicleType` TEXT NOT NULL DEFAULT 'CAR'")
                db.execSQL("DROP INDEX IF EXISTS `index_service_types_name`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_service_types_vehicleType_name` ON `service_types` (`vehicleType`, `name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_types_vehicleType` ON `service_types` (`vehicleType`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `fuel_records` ADD COLUMN `fuelingMode` TEXT NOT NULL DEFAULT 'FULL_SERVICE'",
                )
            }
        }
    }
}
