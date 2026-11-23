package net.prototux.aare.media

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import net.prototux.aare.Settings
import net.prototux.aare.audio.Spectrum

/**
 * Spectrum bars rising from the bottom: LED-style segments with a gap, or solid; a peak mark over
 * each bar. Bar height is linear in dB from [Spectrum.FLOOR_DB] (empty) to 0 dBFS (full).
 */
object SpectrumVisual : NowPlayingVisual {
    override val id = Settings.VISUAL_SPECTRUM

    @Composable
    override fun Content(frames: SpectrumFrames, stereo: Boolean, style: VisualStyle, modifier: Modifier) {
        val cache = remember { PaletteCache() }
        Canvas(modifier) {
            @Suppress("UNUSED_EXPRESSION") frames.tick // redraw on every frame (draw phase only)
            // Whole pixels, and not too thin: the car gets this as compressed video, and stripes a
            // pixel or two high lose their color (the encoder keeps color at half resolution).
            val seg = SEGMENT.roundToPx().toFloat()
            val gap = SEGMENT_GAP.roundToPx().coerceAtLeast(1).toFloat()
            val g = Geometry(seg, seg + gap, ((size.height + gap) / (seg + gap)).toInt().coerceAtLeast(1), PEAK.roundToPx().toFloat())
            val palette = cache.palette(g.segments, style)
            val brush = if (style.solid && style.stops != null) cache.brush(size.height, style) else null
            if (stereo && frames.channels == 2) {
                val half = size.width / 2
                // Left channel: low at the left edge, high at the middle. Right: mirrored.
                drawBars(frames.bars[0], frames.caps[0], 0f, half, mirrored = false, g, palette, brush, style)
                drawBars(frames.bars[1], frames.caps[1], half, half, mirrored = true, g, palette, brush, style)
            } else {
                drawBars(frames.bars[0], frames.caps[0], 0f, size.width, mirrored = false, g, palette, brush, style)
            }
        }
    }

    private class Geometry(val seg: Float, val pitch: Float, val segments: Int, val peak: Float)

    private fun DrawScope.drawBars(
        bars: FloatArray, caps: FloatArray, left: Float, width: Float, mirrored: Boolean,
        g: Geometry, palette: Array<Color>, brush: Brush?, style: VisualStyle,
    ) {
        val n = bars.size
        val slot = width / n
        val barGap = (slot * BAR_GAP_FRACTION).coerceAtLeast(1f)
        val barWidth = slot - barGap
        val bottom = size.height
        val a = style.alpha
        for (i in 0 until n) {
            val x = left + (if (mirrored) n - 1 - i else i) * slot + barGap / 2
            if (style.solid) {
                val h = Math.round(fraction(bars[i]) * size.height).toFloat()
                if (h > 0f) {
                    if (brush != null) drawRect(brush, Offset(x, bottom - h), Size(barWidth, h), alpha = a)
                    else drawRect(style.accent, Offset(x, bottom - h), Size(barWidth, h), alpha = a)
                }
                if (style.peaks) {
                    val top = Math.round(fraction(caps[i]) * size.height).toFloat()
                    if (top > h + g.peak) {
                        val color = if (style.stops != null) palette[((top / size.height) * (palette.size - 1)).toInt().coerceIn(0, palette.size - 1)] else style.dim
                        drawRect(color, Offset(x, bottom - top), Size(barWidth, g.peak), alpha = a)
                    }
                }
            } else {
                val lit = segmentsFor(bars[i], g.segments)
                for (s in 0 until lit) {
                    drawRect(palette[s], Offset(x, bottom - s * g.pitch - g.seg), Size(barWidth, g.seg), alpha = a)
                }
                if (style.peaks) {
                    val cap = segmentsFor(caps[i], g.segments) - 1
                    if (cap >= lit && cap >= 0) {
                        drawRect(if (style.stops != null) palette[cap] else style.dim, Offset(x, bottom - cap * g.pitch - g.seg), Size(barWidth, g.seg), alpha = a)
                    }
                }
            }
        }
    }

    /** 0 (floor) to 1 (0 dBFS), linear in dB. */
    private fun fraction(db: Float): Float = ((db - Spectrum.FLOOR_DB) / -Spectrum.FLOOR_DB).coerceIn(0f, 1f)

    /** Lit segments for [db]. */
    private fun segmentsFor(db: Float, segments: Int): Int = (fraction(db) * segments + 0.5f).toInt()

    /** Colors made again only when the height or the style changes. */
    private class PaletteCache {
        private var palette: Array<Color> = emptyArray()
        private var paletteFor: Pair<Int, Any>? = null
        private var brush: Brush? = null
        private var brushFor: Pair<Float, Any?>? = null

        /** One color per segment, bottom first. */
        fun palette(segments: Int, style: VisualStyle): Array<Color> {
            val key = segments to (style.stops ?: style.accent)
            if (key != paletteFor) {
                paletteFor = key
                palette = Array(segments) { s -> colorAt(if (segments == 1) 0f else s.toFloat() / (segments - 1), style) }
            }
            return palette
        }

        /** The stops over [height], bottom to top. */
        fun brush(height: Float, style: VisualStyle): Brush {
            val key = height to style.stops
            val made = brush
            if (key == brushFor && made != null) return made
            brushFor = key
            // verticalGradient runs top (0) to bottom (1); the stops count from the bottom.
            val stops = style.stops!!.map { (f, c) -> (1f - f) to c }.sortedBy { it.first }.toTypedArray()
            return Brush.verticalGradient(*stops, startY = 0f, endY = height).also { brush = it }
        }
    }

    /** The color at [f] of the height (0 bottom, 1 top), blended between stops. */
    private fun colorAt(f: Float, style: VisualStyle): Color {
        val stops = style.stops ?: return style.accent
        if (f <= stops.first().first) return stops.first().second
        for (k in 1 until stops.size) {
            val (f1, c1) = stops[k]
            if (f <= f1) {
                val (f0, c0) = stops[k - 1]
                return if (f1 == f0) c1 else lerp(c0, c1, (f - f0) / (f1 - f0))
            }
        }
        return stops.last().second
    }

    private val SEGMENT = 6.dp
    private val SEGMENT_GAP = 3.dp
    private val PEAK = 3.dp
    private const val BAR_GAP_FRACTION = 0.22f
}
