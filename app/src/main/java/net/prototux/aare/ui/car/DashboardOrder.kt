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
    WEATHER("weather"),
    /** Smart-home values and buttons; shown only once some are set up. */
    HOME("home");

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

/**
 * The cards to draw, in drawing order, and the index in [cards] of the one that gets the big size.
 * [bigFirst]: the big card comes before the others in the user's order, so in the default row its slot is drawn first.
 */
internal data class DashSlots(val cards: List<DashCard>, val big: Int, val bigFirst: Boolean)

/** 0 for the big card, then 1, 2… for the others in their order: the position in the size list. */
internal fun DashSlots.rank(i: Int): Int = when {
    i == big -> 0
    i < big -> i + 1
    else -> i
}

/**
 * [dashboardSlots] and which card is big: the first, or with [mapBig] the map wherever it is in [order].
 * The map then takes a slot even past the [slots] limit; the other cards fill the rest, in order.
 * Its side follows [order], not the cards drawn: [DashSlots.bigFirst] only when the map is first in [order].
 */
internal fun dashboardLayout(order: List<DashCard>, slots: Int, mapBig: Boolean, shown: (DashCard) -> Boolean): DashSlots {
    if (!mapBig || DashCard.MAP !in order) return DashSlots(dashboardSlots(order, slots, shown), 0, bigFirst = true)
    val others = order.filter { it != DashCard.MAP && shown(it) }.take(slots - 1).toSet()
    val cards = order.filter { it == DashCard.MAP || it in others }
    return DashSlots(cards, cards.indexOf(DashCard.MAP), bigFirst = order.first() == DashCard.MAP)
}

/** This order with [card] shown (added at the end) or hidden. The last card stays: an empty dashboard is no use. */
internal fun List<DashCard>.withCard(card: DashCard, shown: Boolean): List<DashCard> = when {
    shown -> if (card in this) this else this + card
    size <= 1 -> this
    else -> this - card
}

/**
 * This list with the item at [index] moved [by] places (clamped to the list); out of range, the
 * list unchanged. Generic on purpose: every user-ordered list (the dashboard's cards, the rail's
 * apps, and any order added later) moves its items with this one helper.
 */
internal fun <T> List<T>.movedBy(index: Int, by: Int): List<T> {
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
