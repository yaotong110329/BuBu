package com.kumo.bubu.domain.repository

interface FormDraftRepository {
    suspend fun readDraft(draftId: String): String?
    suspend fun writeDraft(draftId: String, contents: String)
    suspend fun deleteDraft(draftId: String)
}
