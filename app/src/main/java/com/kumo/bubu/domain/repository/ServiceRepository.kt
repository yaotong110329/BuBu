package com.kumo.bubu.domain.repository

import com.kumo.bubu.domain.model.ServiceRecord
import com.kumo.bubu.domain.model.ServiceRecordDetails
import com.kumo.bubu.domain.model.ServiceRecordInput
import com.kumo.bubu.domain.model.ServiceType
import com.kumo.bubu.domain.model.ServiceTypeInput
import com.kumo.bubu.domain.model.StagedServiceAttachment
import kotlinx.coroutines.flow.Flow

interface ServiceRepository {
    fun observeRecentServiceRecords(): Flow<List<ServiceRecord>>
    fun observeServiceRecordDetails(vehicleId: Long): Flow<List<ServiceRecordDetails>>
    suspend fun getServiceRecord(id: Long): ServiceRecordDetails?
    suspend fun createServiceRecord(input: ServiceRecordInput): Long
    suspend fun updateServiceRecord(id: Long, input: ServiceRecordInput)
    suspend fun deleteServiceRecord(id: Long)
    fun observeServiceTypes(): Flow<List<ServiceType>>
    suspend fun ensureDefaultServiceTypes()
    suspend fun createServiceType(input: ServiceTypeInput): Long
    suspend fun updateServiceType(id: Long, input: ServiceTypeInput)
    suspend fun setServiceTypeArchived(id: Long, archived: Boolean)
    suspend fun deleteCustomServiceType(id: Long)
    suspend fun reorderServiceTypes(orderedIds: List<Long>)
    suspend fun stageServiceAttachments(sourceUriStrings: List<String>): List<StagedServiceAttachment>
    suspend fun discardStagedServiceAttachment(relativePath: String)
    suspend fun readServiceAttachmentBytes(relativePath: String): ByteArray?
}

sealed class ServiceAttachmentException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class TooMany : ServiceAttachmentException("At most 10 service attachments are allowed.")
    class Unsupported : ServiceAttachmentException("Only JPEG, PNG, and WebP images are supported.")
    class TooLarge : ServiceAttachmentException("A service attachment cannot exceed 20 MB.")
    class CopyFailed(cause: Throwable? = null) : ServiceAttachmentException("The service attachment could not be copied.", cause)
}

enum class ServiceWriteStage { RECORD, ITEMS, REMINDERS, ATTACHMENTS, ODOMETER }

sealed class ServiceRecordException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class VehicleNotFound : ServiceRecordException("Vehicle does not exist.")
    class VehicleArchived : ServiceRecordException("Archived vehicle cannot receive service records.")
    class RecordNotFound : ServiceRecordException("Service record does not exist.")
    class WriteFailed(
        val stage: ServiceWriteStage,
        cause: Throwable,
    ) : ServiceRecordException("Service write failed during $stage.", cause)
}
