package com.kumo.bubu.data.backup

import com.kumo.bubu.data.local.entity.ServiceAttachmentEntity
import com.kumo.bubu.data.local.entity.ServiceRecordEntity
import com.kumo.bubu.data.local.entity.VehicleEntity
import com.kumo.bubu.domain.model.ServiceRecordType
import com.kumo.bubu.domain.model.VehicleType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupDataBuilderTest {
    @Test
    fun convertsInternalRelationshipsToStablePublicReferencesWithoutPrivatePaths() {
        val snapshot = BackupDataBuilder.build(
            BackupDataSource(
                vehicles = listOf(vehicle()),
                fuelRecords = emptyList(),
                serviceTypes = emptyList(),
                serviceRecords = listOf(serviceRecord()),
                serviceItems = emptyList(),
                expenseRecords = emptyList(),
                reminders = emptyList(),
                attachments = listOf(attachment()),
            ),
        )

        assertEquals("vehicle-public", snapshot.data.serviceRecords.single().vehiclePublicId)
        assertEquals("service-public", snapshot.data.attachments.single().serviceRecordPublicId)
        assertEquals("attachments/attachment-public", snapshot.data.attachments.single().archivePath)
        assertEquals("attachments/attachment-public", snapshot.attachmentSources.single().archivePath)
        assertFalse(Json.encodeToString(BackupData.serializer(), snapshot.data).contains("attachments/service/secret.jpg"))
    }

    private fun vehicle() = VehicleEntity(
        id = 1,
        publicId = "vehicle-public",
        name = "測試車",
        vehicleType = VehicleType.CAR,
        motorcycleClass = null,
        brand = null,
        model = null,
        manufactureYear = null,
        engineDisplacementCc = null,
        licensePlate = null,
        powertrainType = null,
        trackingStartDateEpochDay = 0,
        trackingStartOdometerKm = 0,
        currentOdometerKm = 0,
        note = null,
        isArchived = false,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun serviceRecord() = ServiceRecordEntity(
        id = 2,
        publicId = "service-public",
        vehicleId = 1,
        dateEpochDay = 0,
        timeMinuteOfDay = null,
        sequenceInDay = 0,
        odometerKm = 0,
        recordType = ServiceRecordType.MAINTENANCE,
        title = "保養",
        paymentMethod = null,
        totalCostTwd = 0,
        note = null,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun attachment() = ServiceAttachmentEntity(
        id = 3,
        publicId = "attachment-public",
        serviceRecordId = 2,
        sequenceInRecord = 0,
        relativePath = "attachments/service/secret.jpg",
        displayName = "invoice.jpg",
        mimeType = "image/jpeg",
        createdAt = 1,
        updatedAt = 1,
    )
}
