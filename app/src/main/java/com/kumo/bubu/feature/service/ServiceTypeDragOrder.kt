package com.kumo.bubu.feature.service

/** Keeps the displayed ordering responsive while a service type is being dragged. */
internal fun List<Long>.moveServiceTypeTo(draggedId: Long, targetId: Long): List<Long> {
    val fromIndex = indexOf(draggedId)
    val targetIndex = indexOf(targetId)
    if (fromIndex < 0 || targetIndex < 0 || fromIndex == targetIndex) return this
    return toMutableList().apply {
        val draggedId = removeAt(fromIndex)
        add(targetIndex, draggedId)
    }
}
