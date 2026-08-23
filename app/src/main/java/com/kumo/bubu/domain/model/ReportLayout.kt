package com.kumo.bubu.domain.model

enum class ReportCard {
    TOTAL_COST,
    FUEL_ECONOMY,
    COST_PER_KM,
    CATEGORY,
    SERVICE_COST,
    MILEAGE,
}

data class ReportLayout(
    val orderedCards: List<ReportCard> = ReportCard.entries,
    val hiddenCards: Set<ReportCard> = emptySet(),
) {
    val visibleCards: List<ReportCard>
        get() = normalized().orderedCards.filterNot(hiddenCards::contains)

    fun normalized(): ReportLayout {
        val unique = orderedCards.distinct()
        return copy(orderedCards = unique + (ReportCard.entries - unique.toSet()))
    }

    fun move(card: ReportCard, offset: Int): ReportLayout {
        val cards = normalized().orderedCards.toMutableList()
        val from = cards.indexOf(card)
        val to = (from + offset).coerceIn(0, cards.lastIndex)
        if (from < 0 || from == to) return this
        cards.add(to, cards.removeAt(from))
        return copy(orderedCards = cards)
    }

    fun setVisible(card: ReportCard, visible: Boolean): ReportLayout = copy(
        hiddenCards = hiddenCards.toMutableSet().apply {
            if (visible) remove(card) else add(card)
        },
    )
}
