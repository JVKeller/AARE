package net.prototux.aare.ui.car

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import net.prototux.aare.DiagLog
import net.prototux.aare.R
import net.prototux.aare.Settings
import net.prototux.aare.host.CarRuntime
import net.prototux.aare.media.Placeholder

/**
 * A plain web browser on the car screen: a [WebView] rendered inside AARE's own projection, so the
 * car's touches reach it like any other car screen and no extra app is launched. AARE is not a
 * certified Android Auto app, so this is a parked convenience — it is paused while driving, like the
 * phone-screen mirror. The page it opens is set on the phone ([Settings.browserUrl]).
 *
 * For head units without a usable touch screen (Mazda's commander knob) it has a pointer
 * ([BrowserCursor]): tilting the knob (D-pad keys) moves it, faster while held, and scrolls the
 * page at the edges; pressing the knob clicks where it points; turning it scrolls the page; a long
 * press switches the pointer off (the knob then moves the focus as on other screens) and on again.
 * Back goes back in the page history, then leaves.
 */
@Composable
internal fun BrowserScreen(runtime: CarRuntime, driving: Boolean, startUrl: String, modifier: Modifier) {
    var web by remember { mutableStateOf<WebView?>(null) }
    val cursor = remember { BrowserCursor(1, 1) }
    /** Bumped whenever the pointer moves or shows/hides: redraws it. */
    var moves by remember { mutableIntStateOf(0) }
    var pointerOn by remember { mutableStateOf(true) }
    var held by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val density = LocalDensity.current.density
    // The head unit / hardware Back key walks the page history before leaving the browser.
    DisposableEffect(Unit) {
        val back: () -> Boolean = { web?.takeIf { it.canGoBack() }?.also { it.goBack() } != null }
        runtime.screenBack = back
        onDispose { if (runtime.screenBack === back) runtime.screenBack = null }
    }
    if (!driving) DisposableEffect(Unit) {
        var selectDownAt = 0L
        var selectWasLong = false
        val input = object : CarRuntime.CarScreenInput {
            override fun onKey(code: Int, down: Boolean, longPress: Boolean): Boolean {
                val dir = BrowserKeys.direction(code)
                if (dir != null) {
                    if (!pointerOn) return false
                    // Moves for as long as it's held (the car sends down, then up): see the ticker below.
                    held = if (down) dir else held.takeIf { it != dir }
                    return true
                }
                if (!BrowserKeys.isSelect(code)) return false
                if (down) {
                    if (longPress) {
                        // Some cars flag the long press on a second down: toggle now, eat the up.
                        if (!selectWasLong) togglePointer(!pointerOn).also { pointerOn = it; cursor.visible = it; moves++ }
                        selectWasLong = true
                    } else if (selectDownAt == 0L) {
                        selectDownAt = SystemClock.uptimeMillis()
                    }
                    return pointerOn || selectWasLong
                }
                val heldFor = SystemClock.uptimeMillis() - selectDownAt
                val wasLong = selectWasLong || longPress
                selectDownAt = 0L
                selectWasLong = false
                when {
                    wasLong -> Unit // already toggled on the down
                    heldFor >= BrowserKeys.LONG_PRESS_MS -> { pointerOn = togglePointer(!pointerOn); cursor.visible = pointerOn; moves++ }
                    pointerOn -> {
                        cursor.visible = true; moves++
                        web?.let { tap(it, cursor.x, cursor.y) }
                    }
                    else -> return false
                }
                return true
            }

            override fun onRotary(delta: Int): Boolean {
                if (!pointerOn) return false
                val w = web ?: return false
                scroll(w, cursor.x, cursor.y, 0f, delta * BrowserKeys.SCROLL_PER_DETENT_DP * density)
                return true
            }
        }
        runtime.screenInput = input
        onDispose { if (runtime.screenInput === input) runtime.screenInput = null }
    }
    // While a direction is held: a step every tick, accelerating; pushing an edge scrolls the page.
    LaunchedEffect(held) {
        val dir = held ?: return@LaunchedEffect
        while (true) {
            val (sx, sy) = cursor.tick(dir.first, dir.second, SystemClock.uptimeMillis())
            moves++
            if (sx != 0f || sy != 0f) web?.let { scroll(it, cursor.x, cursor.y, sx, sy) }
            delay(BrowserCursor.TICK_MS)
        }
    }
    // Out of the way after a while without moving; the next tilt brings it back where it was.
    LaunchedEffect(moves) {
        if (!cursor.visible || held != null) return@LaunchedEffect
        delay(HIDE_AFTER_MS)
        if (held == null) { cursor.visible = false; moves++ }
    }
    Box(modifier.background(Color.Black)) {
        if (!driving) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Videos start from the pointer's click, which the page sees as a user gesture.
                        settings.mediaPlaybackRequiresUserGesture = true
                        // Keep navigation inside this WebView rather than handing off to a browser app.
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadUrl(startUrl.ifBlank { Settings.BROWSER_DEFAULT_URL })
                        web = this
                    }
                },
                onRelease = { it.destroy(); if (web === it) web = null },
                modifier = Modifier.fillMaxSize().onSizeChanged { cursor.resize(it.width, it.height); moves++ },
            )
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_EXPRESSION") moves // redraw on every move
                if (!cursor.visible) return@Canvas
                val s = cursor.size
                // An arrow pointer whose tip is the click point: white with a dark outline, visible on any page.
                val arrow = Path().apply {
                    moveTo(0f, 0f); lineTo(0f, s); lineTo(s * 0.28f, s * 0.75f); lineTo(s * 0.48f, s * 1.1f)
                    lineTo(s * 0.62f, s * 1.02f); lineTo(s * 0.43f, s * 0.68f); lineTo(s * 0.78f, s * 0.68f); close()
                }
                translate(cursor.x, cursor.y) {
                    drawPath(arrow, Color.White)
                    drawPath(arrow, Color.Black, style = Stroke(width = s * 0.07f, join = StrokeJoin.Round))
                    drawCircle(Color.Black, radius = s * 0.04f, center = Offset.Zero)
                }
            }
        } else {
            Placeholder(stringResource(R.string.browser_paused_driving), loading = false, icon = Icons.Rounded.PauseCircle)
        }
    }
}

private const val HIDE_AFTER_MS = 8_000L

private fun togglePointer(on: Boolean): Boolean {
    DiagLog.add(if (on) "browser: pointer on" else "browser: pointer off, the knob moves the focus")
    return on
}

/** A tap at ([x], [y]) in the WebView's own coordinates, as a touch screen would send it. */
private fun tap(web: WebView, x: Float, y: Float) {
    val t = SystemClock.uptimeMillis()
    val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
    val up = MotionEvent.obtain(t, t + TAP_MS, MotionEvent.ACTION_UP, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
    web.dispatchTouchEvent(down)
    web.dispatchTouchEvent(up)
    down.recycle(); up.recycle()
}

private const val TAP_MS = 60L

/**
 * Scrolls the page under ([x], [y]) by ([dx], [dy]) pixels (positive: content moves up/left, as
 * when reading on), like a mouse wheel there: inner scrolling boxes scroll too. Falls back to
 * scrolling the whole page if the WebView ignores the wheel event.
 */
private fun scroll(web: WebView, x: Float, y: Float, dx: Float, dy: Float) {
    val cfg = ViewConfiguration.get(web.context)
    val props = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
    val coords = MotionEvent.PointerCoords().apply {
        this.x = x; this.y = y
        // Wheel units: one unit scrolls the platform's scroll factor; a wheel turned "up" is positive.
        setAxisValue(MotionEvent.AXIS_VSCROLL, -dy / cfg.scaledVerticalScrollFactor)
        setAxisValue(MotionEvent.AXIS_HSCROLL, dx / cfg.scaledHorizontalScrollFactor)
    }
    val t = SystemClock.uptimeMillis()
    val ev = MotionEvent.obtain(t, t, MotionEvent.ACTION_SCROLL, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
    val handled = web.dispatchGenericMotionEvent(ev)
    ev.recycle()
    if (!handled) web.scrollBy(dx.toInt(), dy.toInt())
}
