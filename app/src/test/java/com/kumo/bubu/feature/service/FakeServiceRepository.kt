package com.kumo.bubu.feature.service

import com.kumo.bubu.domain.model.ServiceRecord
import com.kumo.bubu.domain.model.ServiceRecordDetails
import com.kumo.bubu.domain.model.ServiceRecordInput
import com.kumo.bubu.domain.model.ServiceType
import com.kumo.bubu.domain.model.ServiceTypeInput
import com.kumo.bubu.domain.model.StagedServiceAttachment
import com.kumo.bubu.domain.model.ServiceReminderPreference
import com.kumo.bubu.domain.model.ServiceReminderPreferenceInput
import com.kumo.bubu.domain.repository.ServiceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeServiceRepository : ServiceRepository {
    val records = MutableStateFlow<List<ServiceRecord>>(emptyList())
    val historyDetails = MutableStateFlow<List<ServiceRecordDetails>>(emptyList())
    val types = MutableStateFlow<List<ServiceType>>(emptyList())
    val reminderPreferences = MutableStateFlow<List<ServiceReminderPreference>>(emptyList())
    val details = mutableMapOf<Long, ServiceRecordDetails>()
    val createdInputs = mutableListOf<ServiceRecordInput>()
    val updatedInputs = mutableListOf<Pair<Long, ServiceRecordInput>>()
    val deletedIds = mutableListOf<Long>()
    val deletedTypeIds = mutableListOf<Long>()
    val reorderedTypeIds = mutableListOf<List<Long>>()
    val discardedPaths = mutableListOf<String>()
    var nextTypeId = 100L

    override fun observeRecentServiceRecords(): Flow<List<ServiceRecord>> = records

    override fun observeServiceRecordDetails(vehicleId: Long): Flow<List<ServiceRecordDetails>> = historyDetails

    override suspend fun getServiceRecord(id: Long): ServiceRecordDetails? = details[id]

    override suspend fun createServiceRecord(input: ServiceRecordInput): Long {
        createdInputs += input
        return createdInputs.size.toLong()
    }

    override suspend fun updateServiceRecord(id: Long, input: ServiceRecordInput) {
        updatedInputs += id to input
    }

    override suspend fun deleteServiceRecord(id: Long) {
        deletedIds += id
    }

    override fun observeServiceTypes(): Flow<List<ServiceType>> = types

    override suspend fun ensureDefaultServiceTypes() = Unit

    override suspend fun createServiceType(input: ServiceTypeInput): Long = nextTypeId++

    override suspend fun updateServiceType(id: Long, input: ServiceTypeInput) = Unit

    override suspend fun setServiceTypeArchived(id: Long, archived: Boolean) = Unit

    override suspend fun deleteCustomServiceType(id: Long) {
        deletedTypeIds += id
    }

    override suspend fun reorderServiceTypes(orderedIds: List<Long>) {
        reorderedTypeIds += orderedIds
    }

    override fun observeServiceReminderPreferences(vehicleId: Long): Flow<List<ServiceReminderPreference>> =
        reminderPreferences

    override fun observeAllServiceReminderPreferences(): Flow<List<ServiceReminderPreference>> = reminderPreferences

    override suspend fun saveServiceReminderPreference(input: ServiceReminderPreferenceInput) = Unit

    override suspend fun stageServiceAttachments(sourceUriStrings: List<String>): List<StagedServiceAttachment> =
        sourceUriStrings.mapIndexed { index, _ ->
            StagedServiceAttachment(
                relativePath = "service_attachments/staging/$index.jpg",
                displayName = "$index.jpg",
                mimeType = "image/jpeg",
            )
        }

    override suspend fun discardStagedServiceAttachment(relativePath: String) {
        discardedPaths += relativePath
    }

    override suspend fun readServiceAttachmentBytes(relativePath: String): ByteArray? = null
}
