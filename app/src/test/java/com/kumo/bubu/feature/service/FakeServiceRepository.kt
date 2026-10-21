package com.kumo.bubu.feature.service

import com.kumo.bubu.domain.model.ServiceRecord
import com.kumo.bubu.domain.model.ServiceRecordDetails
import com.kumo.bubu.domain.model.ServiceRecordInput
import com.kumo.bubu.domain.model.ServiceType
import com.kumo.bubu.domain.model.ServiceTypeInput
import com.kumo.bubu.domain.model.StagedServiceAttachment
import com.kumo.bubu.domain.model.ServiceReminderPreference
import com.kumo.bubu.domain.model.ServiceReminderPreferenceInput
import com.kumo.bubu.domain.model.DerivedMaintenanceReminder
import com.kumo.bubu.domain.repository.ServiceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

class FakeServiceRepository : ServiceRepository {
    val records = MutableStateFlow<List<ServiceRecord>>(emptyList())
    val historyDetails = MutableStateFlow<List<ServiceRecordDetails>>(emptyList())
    val types = MutableStateFlow<List<ServiceType>>(emptyList())
    val reminderPreferences = MutableStateFlow<List<ServiceReminderPreference>>(emptyList())
    val maintenanceReminders = MutableStateFlow<List<DerivedMaintenanceReminder>>(emptyList())
    val details = mutableMapOf<Long, ServiceRecordDetails>()
    val createdInputs = mutableListOf<ServiceRecordInput>()
    val updatedInputs = mutableListOf<Pair<Long, ServiceRecordInput>>()
    val deletedIds = mutableListOf<Long>()
    val deletedTypeIds = mutableListOf<Long>()
    val deletedReminderPreferenceKeys = mutableListOf<Pair<Long, Long>>()
    val savedReminderPreferences = mutableListOf<ServiceReminderPreferenceInput>()
    val reorderedTypeIds = mutableListOf<List<Long>>()
    val discardedPaths = mutableListOf<String>()
    var beforeAttachmentStage: suspend () -> Unit = {}
    var nextTypeId = 100L
    private val drafts = mutableMapOf<String, Set<String>>()

    override fun observeRecentServiceRecords(): Flow<List<ServiceRecord>> = records

    override fun observeServiceRecordDetails(vehicleId: Long): Flow<List<ServiceRecordDetails>> = historyDetails

    override suspend fun getServiceRecord(id: Long): ServiceRecordDetails? = details[id]

    private val createdIds = mutableMapOf<String, Long>()

    override suspend fun findServiceRecordIdByPublicId(publicId: String): Long? =
        createdIds[publicId] ?: details.values.firstOrNull { it.record.publicId == publicId }?.record?.id

    override suspend fun createServiceRecord(input: ServiceRecordInput, creationId: String?): Long {
        creationId?.let { createdIds[it]?.let { id -> return id } }
        createdInputs += input
        return createdInputs.size.toLong().also { id -> creationId?.let { createdIds[it] = id } }
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

    override suspend fun saveServiceReminderPreference(input: ServiceReminderPreferenceInput) {
        savedReminderPreferences += input
    }

    override suspend fun deleteServiceReminderPreference(vehicleId: Long, serviceTypeId: Long) {
        deletedReminderPreferenceKeys += vehicleId to serviceTypeId
    }

    override fun observeMaintenanceReminders(today: LocalDate): Flow<List<DerivedMaintenanceReminder>> =
        maintenanceReminders

    override suspend fun stageServiceAttachments(sourceUriStrings: List<String>, draftId: String?): List<StagedServiceAttachment> {
        beforeAttachmentStage()
        return sourceUriStrings.mapIndexed { index, _ ->
            StagedServiceAttachment(
                relativePath = "service_attachments/staging/$index.jpg",
                displayName = "$index.jpg",
                mimeType = "image/jpeg",
            )
        }.also { staged ->
            if (draftId != null) drafts[draftId] = drafts[draftId].orEmpty() + staged.map { it.relativePath }
        }
    }

    override suspend fun discardStagedServiceAttachment(relativePath: String, draftId: String?) {
        discardedPaths += relativePath
        if (draftId != null) drafts[draftId] = drafts[draftId].orEmpty() - relativePath
    }

    override suspend fun recoverServiceDraftAttachments(draftId: String, relativePaths: Set<String>): Set<String> =
        relativePaths.intersect(drafts[draftId].orEmpty())

    override suspend fun discardServiceDraft(draftId: String) {
        discardedPaths += drafts.remove(draftId).orEmpty()
    }

    override suspend fun releaseServiceDraft(draftId: String) {
        drafts.remove(draftId)
    }

    override suspend fun readServiceAttachmentBytes(relativePath: String): ByteArray? =
        if (drafts.values.any { relativePath in it }) byteArrayOf(1, 2, 3) else null
}
