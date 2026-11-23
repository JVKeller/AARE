package net.prototux.aare.ui.car

/** A card the dashboard can show. */
enum class DashCard(
    /** Stored in the settings: never change it once released. */
    val id: String,
) {
    MAP("map"),
    MEDIA("media"),
    CALENDAR("calendar"),
    NOTIFICATION("notification"),
    WEATHER("weather");

    companion object {
        /** The cards of a saved list of ids, in order: unknown ids dropped, each card once. */
        fun parse(ids: List<String>): List<DashCard> = ids.mapNotNull { id -> entries.firstOrNull { it.id == id } }.distinct()

        /** Before the dashboard was configurable: the map, the music, the next event, else the latest notification. */
        val DEFAULT = listOf(MAP, MEDIA, CALENDAR, NOTIFICATION)
    }
}

/**
 * The cards to draw, in slot order: the user's [order], skipping those with nothing to show now
 * ([shown]: no calendar event, no notification…), at most [slots]. The first slot is the big one.
 * The map if nothing is left, so the dashboard is never empty.
 */
internal fun dashboardSlots(order: List<DashCard>, slots: Int, shown: (DashCard) -> Boolean): List<DashCard> =
    order.filter(shown).take(slots).ifEmpty { listOf(DashCard.MAP) }

/** This order with [card] shown (added at the end) or hidden. The last card stays: an empty dashboard is no use. */
internal fun List<DashCard>.withCard(card: DashCard, shown: Boolean): List<DashCard> = when {
    shown -> if (card in this) this else this + card
    size <= 1 -> this
    else -> this - card
}

/** This order with the card at [index] moved [by] places (clamped to the list). */
internal fun List<DashCard>.moved(index: Int, by: Int): List<DashCard> {
    if (index !in indices) return this
    val list = toMutableList()
    list.add((index + by).coerceIn(0, lastIndex), list.removeAt(index))
    return list
}

/** How the dashboard arranges its cards for a screen. */
internal enum class DashLayout { PORTRAIT, NARROW, THREE_COLUMNS, BIG_AND_STACKED }

/**
 * The layout for a dashboard [width] x [height] (dp) with [gutter] between cards.
 *
 * Three cards side by side (like Android Auto on 1920x720) only when the narrowest of them still
 * gets a sensible card: at least [MIN_SIDE_CARD_WIDTH] wide and not much taller than wide.
 * Otherwise a wide screen keeps the big card with the others stacked beside it, which suits
 * screens that are wide but short in dp (a high density, or a tall bar).
 */
internal fun dashboardLayout(width: Float, height: Float, gutter: Float): DashLayout {
    val narrowest = (width - gutter * 2) * THIRD_WEIGHT / (BIG_WEIGHT + MEDIA_WEIGHT + THIRD_WEIGHT)
    return when {
        height > width * 1.2f && height >= 700f -> DashLayout.PORTRAIT
        width < 560f -> DashLayout.NARROW
        width > height * 2.1f && narrowest >= MIN_SIDE_CARD_WIDTH && narrowest >= height * MIN_SIDE_CARD_RATIO -> DashLayout.THREE_COLUMNS
        else -> DashLayout.BIG_AND_STACKED
    }
}

/** Weights of the three side-by-side cards. */
internal const val BIG_WEIGHT = 1.6f
internal const val MEDIA_WEIGHT = 1f
internal const val THIRD_WEIGHT = 0.9f
private const val MIN_SIDE_CARD_WIDTH = 280f
/** Narrowest card width over its height: below this, the cards are tall slivers. */
private const val MIN_SIDE_CARD_RATIO = 0.6f
