package net.prototux.aare.ui.car

import kotlin.math.max
import kotlin.math.min

/**
 * The browser's pointer for head units without a usable touch screen (Mazda's commander knob,
 * D-pads): tilting moves it, faster the longer the tilt is held; at a page edge it scrolls the
 * page instead. Pure arithmetic, no Android, so it is unit-tested; [BrowserScreen] runs [tick]
 * every [TICK_MS] while a direction is held and draws the pointer at [x], [y].
 *
 * Like Fermata Auto's D-pad cursor (a design reference, not its code): a step of a third of the
 * pointer's size, sped up on every repeat, back to one step after a pause.
 */
class BrowserCursor(width: Int, height: Int) {
    var width = width; private set
    var height = height; private set
    /** The pointer's size: 5% of the shorter side, as Fermata's. */
    val size get() = max(MIN_SIZE, min(width, height) * 0.05f)
    var x = width / 2f; private set
    var y = height / 2f; private set
    /** Hidden until a tilt or a toggle shows it; touches and focus navigation work while hidden. */
    var visible = false
    private var accel = 1
    private var lastMoveMs = Long.MIN_VALUE

    fun resize(w: Int, h: Int) {
        if (w <= 0 || h <= 0 || (w == width && h == height)) return
        width = w; height = h
        x = x.coerceIn(0f, (w - 1).toFloat()); y = y.coerceIn(0f, (h - 1).toFloat())
    }

    /**
     * One step in direction ([dx], [dy]) (each -1, 0 or 1) at time [nowMs]. Returns how far the
     * page should scroll, in pixels, when the pointer is pushed against an edge (0, 0 otherwise).
     */
    fun tick(dx: Int, dy: Int, nowMs: Long): Pair<Float, Float> {
        if (!visible) { visible = true; lastMoveMs = nowMs; return 0f to 0f } // the first tilt shows it where it was
        accel = if (lastMoveMs != Long.MIN_VALUE && nowMs - lastMoveMs <= RESET_MS) min(accel + 1, MAX_ACCEL) else 1
        lastMoveMs = nowMs
        val step = size / 3f * accel
        val nx = x + dx * step
        val ny = y + dy * step
        val maxX = (width - 1).toFloat()
        val maxY = (height - 1).toFloat()
        // Past an edge: the overshoot (at least one step) scrolls the page in that direction.
        val sx = if ((dx < 0 && nx < 0f) || (dx > 0 && nx > maxX)) dx * step else 0f
        val sy = if ((dy < 0 && ny < 0f) || (dy > 0 && ny > maxY)) dy * step else 0f
        x = nx.coerceIn(0f, maxX)
        y = ny.coerceIn(0f, maxY)
        return sx to sy
    }

    /** The current speed multiplier (for tests). */
    val speed get() = accel

    companion object {
        /** Repeat interval while a direction is held. */
        const val TICK_MS = 50L
        /** A pause longer than this starts again at one step. */
        const val RESET_MS = 200L
        /** Keeps a long hold controllable: at most this many steps per tick. */
        const val MAX_ACCEL = 16
        private const val MIN_SIZE = 12f
    }
}

/** What a car key does to the browser in pointer mode (see [BrowserScreen]). */
object BrowserKeys {
    /** The direction of a D-pad key, or null for other keys. */
    fun direction(code: Int): Pair<Int, Int>? = when (code) {
        DPAD_UP -> 0 to -1
        DPAD_DOWN -> 0 to 1
        DPAD_LEFT -> -1 to 0
        DPAD_RIGHT -> 1 to 0
        DPAD_UP_LEFT -> -1 to -1
        DPAD_UP_RIGHT -> 1 to -1
        DPAD_DOWN_LEFT -> -1 to 1
        DPAD_DOWN_RIGHT -> 1 to 1
        else -> null
    }

    fun isSelect(code: Int) = code == DPAD_CENTER || code == ENTER || code == NUMPAD_ENTER

    /** A press held this long toggles the pointer instead of clicking (if the car doesn't flag it as a long press). */
    const val LONG_PRESS_MS = 600L
    /** Page scroll per knob detent, in pixels at the car screen's density (scaled by the caller). */
    const val SCROLL_PER_DETENT_DP = 64f

    // android.view.KeyEvent codes, repeated here so this file stays plain Kotlin for the tests.
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23
    const val ENTER = 66
    const val NUMPAD_ENTER = 160
    const val DPAD_UP_LEFT = 268
    const val DPAD_DOWN_LEFT = 269
    const val DPAD_UP_RIGHT = 270
    const val DPAD_DOWN_RIGHT = 271
}
