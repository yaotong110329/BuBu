package com.kumo.bubu.data.repository

import android.util.Log
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.kumo.bubu.core.database.BuBuDatabase
import com.kumo.bubu.data.attachment.PrivateAttachmentStore
import com.kumo.bubu.data.local.dao.FuelRecordDao
import com.kumo.bubu.data.local.dao.PendingAttachmentDeletionDao
import com.kumo.bubu.data.local.dao.ServiceAttachmentDao
import com.kumo.bubu.data.local.dao.ServiceItemDao
import com.kumo.bubu.data.local.dao.ServiceRecordDao
import com.kumo.bubu.data.local.dao.ServiceTypeDao
import com.kumo.bubu.data.local.dao.VehicleDao
import com.kumo.bubu.data.local.dao.VehicleReminderDao
import com.kumo.bubu.data.local.dao.VehicleServiceReminderPreferenceDao
import com.kumo.bubu.data.local.entity.PendingAttachmentDeletionEntity
import com.kumo.bubu.data.local.entity.ServiceAttachmentEntity
import com.kumo.bubu.data.local.entity.ServiceItemEntity
import com.kumo.bubu.data.local.entity.ServiceTypeEntity
import com.kumo.bubu.data.local.entity.VehicleEntity
import com.kumo.bubu.data.local.entity.VehicleReminderEntity
import com.kumo.bubu.data.local.entity.VehicleServiceReminderPreferenceEntity
import com.kumo.bubu.data.mapper.toDomain
import com.kumo.bubu.data.mapper.toNewEntity
import com.kumo.bubu.data.mapper.toUpdatedEntity
import com.kumo.bubu.domain.model.ServiceAttachmentInput
import com.kumo.bubu.domain.model.BuiltInServiceTypeSeed
import com.kumo.bubu.domain.model.ServiceRecord
import com.kumo.bubu.domain.model.ServiceRecordDetails
import com.kumo.bubu.domain.model.ServiceRecordInput
import com.kumo.bubu.domain.model.StagedServiceAttachment
import com.kumo.bubu.domain.model.ServiceType
import com.kumo.bubu.domain.model.ServiceTypeInput
import com.kumo.bubu.domain.model.ServiceReminderPreference
import com.kumo.bubu.domain.model.ServiceReminderPreferenceInput
import com.kumo.bubu.domain.model.defaultReminderIntervalKm
import com.kumo.bubu.domain.model.MAX_SERVICE_ATTACHMENTS_PER_RECORD
import com.kumo.bubu.domain.model.validated
import com.kumo.bubu.domain.repository.ServiceRepository
import com.kumo.bubu.domain.repository.ServiceAttachmentException
import com.kumo.bubu.domain.repository.ServiceRecordException
import com.kumo.bubu.domain.repository.ServiceWriteStage
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OfflineServiceRepository(
    private val database: BuBuDatabase,
    private val vehicleDao: VehicleDao,
    private val fuelRecordDao: FuelRecordDao,
    private val serviceRecordDao: ServiceRecordDao,
    private val serviceItemDao: ServiceItemDao,
    private val serviceTypeDao: ServiceTypeDao,
    private val serviceAttachmentDao: ServiceAttachmentDao = database.serviceAttachmentDao(),
    private val vehicleReminderDao: VehicleReminderDao = database.vehicleReminderDao(),
    private val serviceReminderPreferenceDao: VehicleServiceReminderPreferenceDao =
        database.vehicleServiceReminderPreferenceDao(),
    private val pendingAttachmentDeletionDao: PendingAttachmentDeletionDao =
        database.pendingAttachmentDeletionDao(),
    private val attachmentStore: PrivateAttachmentStore? = null,
    private val builtInServiceTypeSeeds: List<BuiltInServiceTypeSeed> = emptyList(),
) : ServiceRepository {
    private val attachmentOperationMutex = Mutex()

    override fun observeRecentServiceRecords(): Flow<List<ServiceRecord>> =
        serviceRecordDao.observeRecent().map { records -> records.map { it.toDomain() } }

    override fun observeServiceRecordDetails(vehicleId: Long): Flow<List<ServiceRecordDetails>> =
        combine(
            serviceRecordDao.observeForVehicle(vehicleId),
            serviceItemDao.observeForVehicle(vehicleId),
        ) { records, items ->
            val itemsByRecordId = items.groupBy { it.serviceRecordId }
            records.map { record ->
                ServiceRecordDetails(
                    record = record.toDomain(),
                    items = itemsByRecordId[record.id].orEmpty().map { it.toDomain() },
                )
            }
        }

    override suspend fun getServiceRecord(id: Long): ServiceRecordDetails? = database.withTransaction {
        val record = serviceRecordDao.getById(id) ?: return@withTransaction null
        ServiceRecordDetails(
            record = record.toDomain(),
            items = serviceItemDao.getForRecord(id).map { it.toDomain() },
            attachments = serviceAttachmentDao.getForRecord(id).map { it.toDomain() },
        )
    }

    override suspend fun createServiceRecord(input: ServiceRecordInput): Long =
        attachmentOperationMutex.withLock {
        val recordId = database.withTransaction {
            val valid = input.validated()
            val vehicle = vehicleDao.getById(valid.vehicleId)
                ?: throw ServiceRecordException.VehicleNotFound()
            if (vehicle.isArchived) throw ServiceRecordException.VehicleArchived()
            val now = System.currentTimeMillis()
            val newRecord = valid.toNewEntity(
                publicId = UUID.randomUUID().toString(),
                sequence = serviceRecordDao.nextSequenceInDay(
                    valid.vehicleId,
                    valid.dateEpochDay,
                ),
                now = now,
            )
            val createdRecordId = serviceWrite(ServiceWriteStage.RECORD) {
                serviceRecordDao.insert(newRecord)
            }
            val newItems = valid.items.mapIndexed { index, item ->
                item.toNewEntity(
                    serviceRecordId = createdRecordId,
                    sequence = index,
                    publicId = UUID.randomUUID().toString(),
                    now = now,
                )
            }
            val itemIds = serviceWrite(ServiceWriteStage.ITEMS) {
                serviceItemDao.insertAll(newItems).also { insertedIds ->
                    require(insertedIds.size == newItems.size) {
                        "Not all service items were inserted."
                    }
                }
            }
            serviceWrite(ServiceWriteStage.REMINDERS) {
                newItems.zip(itemIds).forEach { (item, itemId) ->
                    val persistedItem = item.copy(id = itemId)
                    syncReminder(valid.vehicleId, persistedItem, now)
                }
                rebuildReminderCompletions(valid.vehicleId, now)
            }
            serviceWrite(ServiceWriteStage.ATTACHMENTS) {
                syncAttachments(
                    serviceRecordId = createdRecordId,
                    previous = emptyMap(),
                    requested = valid.attachments,
                    now = now,
                )
            }
            serviceWrite(ServiceWriteStage.ODOMETER) { rebuildCurrentOdometer(vehicle, now) }
            createdRecordId
        }
        drainPendingAttachmentDeletionsSafely()
            recordId
        }

    override suspend fun updateServiceRecord(id: Long, input: ServiceRecordInput) =
        attachmentOperationMutex.withLock {
        database.withTransaction {
            val existing = serviceRecordDao.getById(id)
                ?: throw ServiceRecordException.RecordNotFound()
            val valid = input.validated()
            val originalVehicle = vehicleDao.getById(existing.vehicleId)
                ?: throw ServiceRecordException.VehicleNotFound()
            val targetVehicle = vehicleDao.getById(valid.vehicleId)
                ?: throw ServiceRecordException.VehicleNotFound()
            if (targetVehicle.isArchived && targetVehicle.id != originalVehicle.id) {
                throw ServiceRecordException.VehicleArchived()
            }
            val now = System.currentTimeMillis()
            val sequence = if (
                existing.vehicleId == valid.vehicleId &&
                existing.dateEpochDay == valid.dateEpochDay
            ) {
                existing.sequenceInDay
            } else {
                serviceRecordDao.nextSequenceInDay(valid.vehicleId, valid.dateEpochDay)
            }
            val updatedRecord = valid.toNewEntity(existing.publicId, sequence, now).copy(
                id = existing.id,
                createdAt = existing.createdAt,
            )
            serviceWrite(ServiceWriteStage.RECORD) { serviceRecordDao.update(updatedRecord) }
            val oldItems = serviceItemDao.getForRecord(id).associateBy { it.id }
            val retainedItemIds = mutableSetOf<Long>()
            valid.items.forEachIndexed { index, item ->
                val persisted = serviceWrite(ServiceWriteStage.ITEMS) {
                    val old = item.id?.let { itemId ->
                        requireNotNull(oldItems[itemId]) {
                            "Service item does not belong to the record."
                        }
                    }
                    if (old != null) {
                        require(old.serviceRecordId == id) {
                            "Service item does not belong to the record."
                        }
                        val updated = item.toUpdatedEntity(old, index, now)
                        serviceItemDao.update(updated)
                        updated
                    } else {
                        val newItem = item.toNewEntity(
                            serviceRecordId = id,
                            sequence = index,
                            publicId = UUID.randomUUID().toString(),
                            now = now,
                        )
                        val insertedId = serviceItemDao.insertAll(listOf(newItem)).single()
                        newItem.copy(id = insertedId)
                    }
                }
                retainedItemIds += persisted.id
                serviceWrite(ServiceWriteStage.REMINDERS) {
                    syncReminder(valid.vehicleId, persisted, now)
                }
            }
            serviceWrite(ServiceWriteStage.ITEMS) {
                oldItems.keys.filterNot(retainedItemIds::contains).forEach { itemId ->
                    serviceItemDao.deleteById(itemId)
                }
            }
            serviceWrite(ServiceWriteStage.REMINDERS) {
                rebuildReminderCompletions(originalVehicle.id, now)
                if (targetVehicle.id != originalVehicle.id) {
                    rebuildReminderCompletions(targetVehicle.id, now)
                }
            }
            serviceWrite(ServiceWriteStage.ATTACHMENTS) {
                syncAttachments(
                    serviceRecordId = id,
                    previous = serviceAttachmentDao.getForRecord(id).associateBy { it.id },
                    requested = valid.attachments,
                    now = now,
                )
            }
            serviceWrite(ServiceWriteStage.ODOMETER) {
                rebuildCurrentOdometer(originalVehicle, now)
                if (targetVehicle.id != originalVehicle.id) rebuildCurrentOdometer(targetVehicle, now)
            }
        }
        drainPendingAttachmentDeletionsSafely()
        }

    override suspend fun deleteServiceRecord(id: Long) = attachmentOperationMutex.withLock {
        database.withTransaction {
            val existing = serviceRecordDao.getById(id)
                ?: throw ServiceRecordException.RecordNotFound()
            val vehicle = vehicleDao.getById(existing.vehicleId)
                ?: throw ServiceRecordException.VehicleNotFound()
            val now = System.currentTimeMillis()
            serviceAttachmentDao.getForRecord(id).forEach { attachment ->
                queueAttachmentDeletion(attachment.relativePath, now)
            }
            serviceRecordDao.deleteById(id)
            rebuildReminderCompletions(vehicle.id, now)
            rebuildCurrentOdometer(vehicle, now)
        }
        drainPendingAttachmentDeletionsSafely()
    }

    override fun observeServiceTypes(): Flow<List<ServiceType>> = serviceTypeDao.observeAll().map { types -> types.map { it.toDomain() } }

    override suspend fun ensureDefaultServiceTypes() = database.withTransaction {
        val now = System.currentTimeMillis()
        val carStart = serviceTypeDao.maxSortOrder(com.kumo.bubu.domain.model.VehicleType.CAR) + 1
        val motorcycleStart = serviceTypeDao.maxSortOrder(com.kumo.bubu.domain.model.VehicleType.MOTORCYCLE) + 1
        val seedsByPublicId = builtInServiceTypeSeeds.associateBy { seed ->
            "builtin-${seed.vehicleType.name.lowercase()}-${seed.key}"
        }
        serviceTypeDao.insertIgnoreAll(
            builtInServiceTypeSeeds.groupBy(BuiltInServiceTypeSeed::vehicleType).flatMap { (vehicleType, seeds) -> seeds.mapIndexed { index, seed ->
            ServiceTypeEntity(
                publicId = "builtin-${vehicleType.name.lowercase()}-${seed.key}",
                name = seed.displayName,
                vehicleType = vehicleType,
                isBuiltIn = true,
                isArchived = false,
                sortOrder = (if (vehicleType == com.kumo.bubu.domain.model.VehicleType.CAR) carStart else motorcycleStart) + index,
                createdAt = now,
                updatedAt = now,
            )
                } },
        )
        val allTypes = serviceTypeDao.getAll()
        val occupiedNames = allTypes
            .groupBy { type -> type.vehicleType to type.name }
            .mapValues { (_, types) -> types.map(ServiceTypeEntity::id).toMutableSet() }
            .toMutableMap()
        allTypes.filter(ServiceTypeEntity::isBuiltIn).forEach { type ->
            val seed = seedsByPublicId[type.publicId]
            when {
                seed == null && type.publicId.startsWith("builtin-") && !type.isArchived -> {
                    serviceTypeDao.update(type.copy(isArchived = true, updatedAt = now))
                }
                seed != null && type.name != seed.displayName -> {
                    val oldKey = type.vehicleType to type.name
                    val newKey = type.vehicleType to seed.displayName
                    val hasNameConflict = occupiedNames[newKey].orEmpty().any { id -> id != type.id }
                    if (!hasNameConflict) {
                        serviceTypeDao.update(type.copy(name = seed.displayName, updatedAt = now))
                        occupiedNames[oldKey]?.remove(type.id)
                        occupiedNames.getOrPut(newKey) { mutableSetOf() }.add(type.id)
                    }
                }
            }
        }
    }

    override suspend fun createServiceType(input: ServiceTypeInput): Long {
        val valid = input.validated()
        return serviceTypeDao.insert(
            valid.toNewEntity(
                publicId = UUID.randomUUID().toString(),
                sortOrder = serviceTypeDao.maxSortOrder(valid.vehicleType) + 1,
                now = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateServiceType(id: Long, input: ServiceTypeInput) {
        val existing = requireNotNull(serviceTypeDao.getById(id)) { "Service type does not exist." }
        require(!existing.isBuiltIn) { "Built-in service type cannot be renamed." }
        serviceTypeDao.update(existing.copy(name = input.validated().name, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun setServiceTypeArchived(id: Long, archived: Boolean) {
        val existing = requireNotNull(serviceTypeDao.getById(id)) { "Service type does not exist." }
        serviceTypeDao.update(existing.copy(isArchived = archived, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteCustomServiceType(id: Long) = database.withTransaction {
        val existing = requireNotNull(serviceTypeDao.getById(id)) { "Service type does not exist." }
        require(!existing.isBuiltIn) { "Built-in service type cannot be deleted." }
        require(serviceItemDao.countForServiceType(id) == 0) {
            "A service type used by a work order cannot be deleted."
        }
        serviceTypeDao.deleteById(id)
    }

    override suspend fun reorderServiceTypes(orderedIds: List<Long>) = database.withTransaction {
        require(orderedIds.distinct().size == orderedIds.size) {
            "Service type order contains duplicate IDs."
        }
        val all = serviceTypeDao.getAll()
        val byId = all.associateBy { it.id }
        require(orderedIds.all(byId::containsKey)) { "Service type does not exist." }
        val vehicleTypes = orderedIds.map { requireNotNull(byId[it]).vehicleType }.distinct()
        require(vehicleTypes.size <= 1) { "Service type order must not mix vehicle types." }
        val now = System.currentTimeMillis()
        orderedIds.forEachIndexed { sortOrder, id ->
            serviceTypeDao.update(requireNotNull(byId[id]).copy(sortOrder = sortOrder, updatedAt = now))
        }
    }

    override fun observeServiceReminderPreferences(vehicleId: Long): Flow<List<ServiceReminderPreference>> =
        serviceReminderPreferenceDao.observeForVehicle(vehicleId).map { preferences ->
            preferences.map(VehicleServiceReminderPreferenceEntity::toDomain)
        }

    override fun observeAllServiceReminderPreferences(): Flow<List<ServiceReminderPreference>> =
        serviceReminderPreferenceDao.observeAll().map { preferences ->
            preferences.map(VehicleServiceReminderPreferenceEntity::toDomain)
        }

    override suspend fun saveServiceReminderPreference(input: ServiceReminderPreferenceInput) =
        database.withTransaction {
            val valid = input.validated()
            requireNotNull(vehicleDao.getById(valid.vehicleId)) { "Vehicle does not exist." }
            requireNotNull(serviceTypeDao.getById(valid.serviceTypeId)) { "Service type does not exist." }
            val now = System.currentTimeMillis()
            val existing = serviceReminderPreferenceDao.get(valid.vehicleId, valid.serviceTypeId)
            if (existing == null) {
                serviceReminderPreferenceDao.insert(
                    VehicleServiceReminderPreferenceEntity(
                        publicId = UUID.randomUUID().toString(),
                        vehicleId = valid.vehicleId,
                        serviceTypeId = valid.serviceTypeId,
                        isEnabled = valid.isEnabled,
                        intervalKm = valid.intervalKm,
                        baseOdometerKm = valid.baseOdometerKm,
                        sortOrder = valid.sortOrder,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            } else {
                serviceReminderPreferenceDao.update(
                    existing.copy(
                        isEnabled = valid.isEnabled,
                        intervalKm = valid.intervalKm,
                        baseOdometerKm = valid.baseOdometerKm,
                        sortOrder = valid.sortOrder,
                        updatedAt = now,
                    ),
                )
            }
            serviceItemDao.getForVehicleInTimelineOrder(valid.vehicleId)
                .lastOrNull { it.serviceTypeId == valid.serviceTypeId }
                ?.let { item -> syncReminder(valid.vehicleId, item, now) }
            rebuildReminderCompletions(valid.vehicleId, now)
        }

    override suspend fun stageServiceAttachments(
        sourceUriStrings: List<String>,
    ): List<StagedServiceAttachment> {
        if (sourceUriStrings.size > MAX_SERVICE_ATTACHMENTS_PER_RECORD) {
            throw ServiceAttachmentException.TooMany()
        }
        val store = requireNotNull(attachmentStore) {
            "App-private attachment storage is unavailable."
        }
        val staged = mutableListOf<StagedServiceAttachment>()
        try {
            sourceUriStrings.forEach { sourceUri ->
                staged += store.stage(sourceUri.toUri())
            }
            return staged
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                staged.forEach { attachment ->
                    runCatching { discardStagedServiceAttachment(attachment.relativePath) }
                        .onFailure { cleanupError ->
                            Log.w(
                                LOG_TAG,
                                "Failed to clean a partially staged attachment.",
                                cleanupError,
                            )
                        }
                }
            }
            throw when (error) {
                is ServiceAttachmentException -> error
                is CancellationException -> error
                else -> ServiceAttachmentException.CopyFailed(error)
            }
        }
    }

    override suspend fun discardStagedServiceAttachment(relativePath: String) =
        attachmentOperationMutex.withLock {
        requireNotNull(attachmentStore) {
            "App-private attachment storage is unavailable."
        }
        database.withTransaction {
            require(serviceAttachmentDao.countByRelativePath(relativePath) == 0) {
                "A saved service attachment cannot be discarded as a draft."
            }
            queueAttachmentDeletion(relativePath, System.currentTimeMillis())
        }
        drainPendingAttachmentDeletionsSafely()
        }

    override suspend fun readServiceAttachmentBytes(relativePath: String): ByteArray? =
        attachmentStore?.readManagedBytes(relativePath)

    suspend fun retryPendingAttachmentDeletions() = attachmentOperationMutex.withLock {
        reconcileUnreferencedAttachmentFiles()
        drainPendingAttachmentDeletionsSafely()
    }

    private suspend fun <T> serviceWrite(
        stage: ServiceWriteStage,
        block: suspend () -> T,
    ): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: ServiceRecordException) {
        throw error
    } catch (error: Throwable) {
        throw ServiceRecordException.WriteFailed(stage, error)
    }

    private suspend fun rebuildReminderCompletions(vehicleId: Long, now: Long) {
        val reminders = vehicleReminderDao.getForVehicle(vehicleId)
            .filter { it.source == com.kumo.bubu.domain.model.ReminderSource.SERVICE_ITEM }
        val remindersBySourceItem = reminders
            .associateBy(VehicleReminderEntity::sourceServiceItemId)
        val completedByRecord = mutableMapOf<Long, Long>()
        val activeByType = mutableMapOf<ServiceReminderKey, MutableList<VehicleReminderEntity>>()
        serviceItemDao.getForVehicleInTimelineOrder(vehicleId)
            .groupBy(ServiceItemEntity::serviceRecordId)
            .forEach { (serviceRecordId, items) ->
                items.map(::serviceReminderKey).distinct().forEach { key ->
                    activeByType.remove(key).orEmpty().forEach { previous ->
                        completedByRecord[previous.id] = serviceRecordId
                    }
                }
                items.forEach { item ->
                    remindersBySourceItem[item.id]?.let { reminder ->
                        activeByType.getOrPut(serviceReminderKey(item), ::mutableListOf) += reminder
                    }
                }
            }
        reminders.forEach { reminder ->
            val desiredCompletionRecordId = completedByRecord[reminder.id]
            when {
                desiredCompletionRecordId == null &&
                    reminder.completedByServiceRecordId == null &&
                    reminder.completedAt == null -> Unit

                desiredCompletionRecordId == null ->
                    vehicleReminderDao.clearCompletion(reminder.id, now)

                desiredCompletionRecordId == reminder.completedByServiceRecordId &&
                    reminder.completedAt != null -> Unit

                else -> vehicleReminderDao.markCompleted(
                    reminderId = reminder.id,
                    serviceRecordId = desiredCompletionRecordId,
                    completedAt = now,
                )
            }
        }
    }

    private fun serviceReminderKey(item: ServiceItemEntity): ServiceReminderKey =
        ServiceReminderKey(
            serviceTypeId = item.serviceTypeId,
            customName = item.nameSnapshot.takeIf { item.serviceTypeId == null },
        )

    private suspend fun syncReminder(
        vehicleId: Long,
        item: ServiceItemEntity,
        now: Long,
    ) {
        val preference = item.serviceTypeId?.let { serviceTypeId ->
            serviceReminderPreferenceDao.get(vehicleId, serviceTypeId)
        }
        val serviceType = item.serviceTypeId?.let { serviceTypeId ->
            serviceTypeDao.getById(serviceTypeId)
        }
        val intervalKm = preference?.intervalKm ?: serviceType?.toDomain()?.defaultReminderIntervalKm()
        val preferenceDueOdometerKm = intervalKm?.let { intervalKm ->
            serviceRecordDao.getById(item.serviceRecordId)?.odometerKm?.plus(intervalKm)
        }
        val dueOdometerKm = preferenceDueOdometerKm ?: item.nextDueOdometerKm
        val isEnabled = preference?.isEnabled ?: true
        val existing = vehicleReminderDao.getBySourceServiceItemId(item.id)
        if (dueOdometerKm == null && item.nextDueDateEpochDay == null) {
            if (existing != null) {
                vehicleReminderDao.deleteBySourceServiceItemId(item.id)
            }
            return
        }
        if (existing == null) {
            vehicleReminderDao.insert(
                VehicleReminderEntity(
                    publicId = UUID.randomUUID().toString(),
                    vehicleId = vehicleId,
                    source = com.kumo.bubu.domain.model.ReminderSource.SERVICE_ITEM,
                    sourceServiceItemId = item.id,
                    title = item.nameSnapshot,
                    dueOdometerKm = dueOdometerKm,
                    dueDateEpochDay = item.nextDueDateEpochDay,
                    isEnabled = isEnabled,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            vehicleReminderDao.update(
                existing.copy(
                    vehicleId = vehicleId,
                    title = item.nameSnapshot,
                    dueOdometerKm = dueOdometerKm,
                    dueDateEpochDay = item.nextDueDateEpochDay,
                    isEnabled = isEnabled,
                    updatedAt = now,
                ),
            )
        }
    }

    private suspend fun syncAttachments(
        serviceRecordId: Long,
        previous: Map<Long, ServiceAttachmentEntity>,
        requested: List<ServiceAttachmentInput>,
        now: Long,
    ) {
        require(requested.size <= MAX_SERVICE_ATTACHMENTS_PER_RECORD) {
            "A service record supports at most 10 attachments."
        }
        val retainedIds = mutableSetOf<Long>()
        requested.forEachIndexed { sequence, input ->
            val existing = input.id?.let(previous::get)
            if (input.id != null) {
                requireNotNull(existing) { "Service attachment does not belong to the record." }
                require(!input.isStaged) { "An existing service attachment cannot be staged again." }
                serviceAttachmentDao.update(
                    existing.copy(
                        sequenceInRecord = sequence,
                        displayName = input.displayName,
                        mimeType = input.mimeType,
                        updatedAt = now,
                    ),
                )
                retainedIds += existing.id
            } else {
                require(input.isStaged) { "A new service attachment must be staged first." }
                val store = requireNotNull(attachmentStore) {
                    "App-private attachment storage is unavailable."
                }
                store.requireManagedFile(input.relativePath)
                serviceAttachmentDao.insert(
                    input.toNewEntity(
                        serviceRecordId = serviceRecordId,
                        sequence = sequence,
                        publicId = UUID.randomUUID().toString(),
                        now = now,
                    ),
                )
            }
        }
        previous.values.filterNot { it.id in retainedIds }.forEach { removed ->
            queueAttachmentDeletion(removed.relativePath, now)
            serviceAttachmentDao.deleteById(removed.id)
        }
    }

    private suspend fun queueAttachmentDeletion(relativePath: String, now: Long) {
        pendingAttachmentDeletionDao.insertIgnore(
            PendingAttachmentDeletionEntity(relativePath = relativePath, createdAt = now),
        )
    }

    private suspend fun drainPendingAttachmentDeletions() {
        val store = attachmentStore ?: return
        pendingAttachmentDeletionDao.getAll().forEach { pending ->
            if (serviceAttachmentDao.countByRelativePath(pending.relativePath) > 0) {
                pendingAttachmentDeletionDao.deleteById(pending.id)
                return@forEach
            }
            val deleted = runCatching { store.deleteManagedFile(pending.relativePath) }
                .getOrElse { error ->
                    Log.w(LOG_TAG, "Private attachment deletion will be retried.", error)
                    false
                }
            if (deleted) {
                pendingAttachmentDeletionDao.deleteById(pending.id)
            }
        }
    }

    private suspend fun drainPendingAttachmentDeletionsSafely() {
        withContext(NonCancellable) {
            try {
                drainPendingAttachmentDeletions()
            } catch (error: Throwable) {
                Log.w(LOG_TAG, "Pending private attachment deletion will be retried.", error)
            }
        }
    }

    private suspend fun reconcileUnreferencedAttachmentFiles() {
        val store = attachmentStore ?: return
        try {
            val now = System.currentTimeMillis()
            val managedPaths = store.listManagedRelativePaths(
                lastModifiedAtOrBefore = now - UNREFERENCED_ATTACHMENT_GRACE_MILLIS,
            )
            database.withTransaction {
                val referencedPaths = serviceAttachmentDao.getAllRelativePaths().toSet()
                managedPaths.filterNot(referencedPaths::contains).forEach { relativePath ->
                    queueAttachmentDeletion(relativePath, now)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.w(LOG_TAG, "Unreferenced private attachments will be reconciled later.", error)
        }
    }

    private suspend fun rebuildCurrentOdometer(vehicle: VehicleEntity, now: Long) {
        vehicleDao.updateCurrentOdometer(
            vehicle.id,
            maxOf(vehicle.trackingStartOdometerKm, fuelRecordDao.maxOdometerKm(vehicle.id) ?: 0, serviceRecordDao.maxOdometerKm(vehicle.id) ?: 0),
            now,
        )
    }

    private companion object {
        const val LOG_TAG = "OfflineServiceRepo"
        const val UNREFERENCED_ATTACHMENT_GRACE_MILLIS = 24L * 60L * 60L * 1_000L
    }

    private data class ServiceReminderKey(
        val serviceTypeId: Long?,
        val customName: String?,
    )
}

private fun VehicleServiceReminderPreferenceEntity.toDomain() = ServiceReminderPreference(
    id = id,
    publicId = publicId,
    vehicleId = vehicleId,
    serviceTypeId = serviceTypeId,
    isEnabled = isEnabled,
    intervalKm = intervalKm,
    baseOdometerKm = baseOdometerKm,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
