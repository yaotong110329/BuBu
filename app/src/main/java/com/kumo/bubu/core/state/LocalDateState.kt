package com.kumo.bubu.core.state

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onSubscription

/** Keeps a retained screen's local date current while it is in the foreground. */
internal class LocalDateState(
    private val scope: CoroutineScope,
    private val todayProvider: () -> LocalDate,
) {
    private val current = MutableStateFlow(todayProvider())
    private var ticker: Job? = null
    val dates = current.onSubscription { refresh() }

    fun onForeground() {
        refresh()
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                val now = LocalDateTime.now()
                val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
                // Midnight refresh, with a bounded recheck for wall-clock changes.
                delay(Duration.between(now, midnight).toMillis().coerceIn(1, 60_000))
                refresh()
            }
        }
    }

    fun onBackground() {
        ticker?.cancel()
        ticker = null
    }

    private fun refresh() {
        current.value = todayProvider()
    }
}
