package net.prototux.aare.ui.car

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserCursorTest {
    private fun shown(w: Int = 1000, h: Int = 600) = BrowserCursor(w, h).also { it.visible = true }

    @Test fun startsHiddenInTheMiddleAndTheFirstTiltOnlyShowsIt() {
        val c = BrowserCursor(1000, 600)
        assertFalse(c.visible)
        assertEquals(500f, c.x); assertEquals(300f, c.y)
        c.tick(1, 0, 0)
        assertTrue(c.visible)
        assertEquals(500f, c.x) // not moved yet
    }

    @Test fun stepIsAThirdOfThePointerSize() {
        val c = shown()
        assertEquals(30f, c.size) // 5% of 600
        c.tick(1, 0, 0)
        assertEquals(510f, c.x)
        c.tick(0, -1, 1000) // after a pause: one step again
        assertEquals(290f, c.y)
    }

    @Test fun holdingAccelerates() {
        val c = shown()
        var t = 0L
        c.tick(1, 0, t)
        repeat(3) { t += BrowserCursor.TICK_MS; c.tick(1, 0, t) }
        assertEquals(4, c.speed)
        assertEquals(500f + 10f * (1 + 2 + 3 + 4), c.x)
    }

    @Test fun accelerationIsCapped() {
        val c = shown(100_000, 100_000)
        var t = 0L
        repeat(100) { c.tick(1, 0, t); t += BrowserCursor.TICK_MS }
        assertEquals(BrowserCursor.MAX_ACCEL, c.speed)
    }

    @Test fun aPauseResetsTheSpeed() {
        val c = shown()
        c.tick(1, 0, 0); c.tick(1, 0, 50); c.tick(1, 0, 100)
        assertEquals(3, c.speed)
        c.tick(1, 0, 100 + BrowserCursor.RESET_MS + 1)
        assertEquals(1, c.speed)
    }

    @Test fun staysOnScreenAndScrollsAtTheEdges() {
        val c = shown()
        var t = 0L
        var scrolled = 0f
        repeat(200) { scrolled += c.tick(0, 1, t).second; t += BrowserCursor.TICK_MS }
        assertEquals(599f, c.y)
        assertTrue(scrolled > 0f)
        val (sx, sy) = c.tick(-1, 0, t + 1000)
        assertEquals(0f, sx); assertEquals(0f, sy) // moving left, away from the bottom edge: no scroll
        repeat(300) { t += BrowserCursor.TICK_MS; c.tick(-1, -1, t) }
        assertEquals(0f, c.x); assertEquals(0f, c.y)
        val (ex, ey) = c.tick(-1, -1, t + BrowserCursor.TICK_MS)
        assertTrue(ex < 0f && ey < 0f)
    }

    @Test fun resizeKeepsThePointerInside() {
        val c = shown()
        repeat(100) { c.tick(1, 1, it * 50L) }
        c.resize(400, 200)
        assertEquals(399f, c.x); assertEquals(199f, c.y)
    }

    @Test fun keyCodesMatchAndroid() {
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, BrowserKeys.DPAD_UP)
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, BrowserKeys.DPAD_DOWN)
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, BrowserKeys.DPAD_LEFT)
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, BrowserKeys.DPAD_RIGHT)
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, BrowserKeys.DPAD_CENTER)
        assertEquals(KeyEvent.KEYCODE_ENTER, BrowserKeys.ENTER)
        assertEquals(KeyEvent.KEYCODE_NUMPAD_ENTER, BrowserKeys.NUMPAD_ENTER)
        assertEquals(KeyEvent.KEYCODE_DPAD_UP_LEFT, BrowserKeys.DPAD_UP_LEFT)
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN_LEFT, BrowserKeys.DPAD_DOWN_LEFT)
        assertEquals(KeyEvent.KEYCODE_DPAD_UP_RIGHT, BrowserKeys.DPAD_UP_RIGHT)
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN_RIGHT, BrowserKeys.DPAD_DOWN_RIGHT)
        assertEquals(-1 to 1, BrowserKeys.direction(KeyEvent.KEYCODE_DPAD_DOWN_LEFT))
        assertEquals(null, BrowserKeys.direction(KeyEvent.KEYCODE_TAB))
        assertTrue(BrowserKeys.isSelect(KeyEvent.KEYCODE_DPAD_CENTER))
    }
}
