package com.kumo.bubu.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kumo.bubu.domain.model.ReportCard
import com.kumo.bubu.domain.model.ReportLayout
import com.kumo.bubu.domain.repository.ReportLayoutSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreReportLayoutSettings(
    private val preferences: DataStore<Preferences>,
) : ReportLayoutSettings {
    override fun observeLayout(): Flow<ReportLayout> = preferences.data.map { values ->
        ReportLayout(
            orderedCards = parse(values[ORDERED_CARDS]),
            hiddenCards = parse(values[HIDDEN_CARDS]).toSet(),
        ).normalized()
    }

    override suspend fun saveLayout(layout: ReportLayout) {
        val normalized = layout.normalized()
        preferences.edit { values ->
            values[ORDERED_CARDS] = normalized.orderedCards.joinToString(",") { it.name }
            values[HIDDEN_CARDS] = normalized.hiddenCards.joinToString(",") { it.name }
        }
    }

    private fun parse(value: String?): List<ReportCard> = value.orEmpty()
        .split(',')
        .mapNotNull { name -> runCatching { ReportCard.valueOf(name) }.getOrNull() }

    private companion object {
        val ORDERED_CARDS = stringPreferencesKey("report_layout_ordered_cards")
        val HIDDEN_CARDS = stringPreferencesKey("report_layout_hidden_cards")
    }
}
