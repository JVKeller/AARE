package net.prototux.aare.audio

/**
 * 16-bit little-endian interleaved PCM, as sent to the car, with a model of what the car is playing now.
 * One writer (the capture thread) and one reader (the UI coroutine). write() must never allocate, lock, or block.
 * Allocate everything in the constructor. Use @Volatile positions; a reader racing a writer may see a slightly
 * torn window, which is fine, but it must never throw or read out of bounds.
 */
enum class Mix { MONO, LEFT, RIGHT }

class PcmRing(val sampleRate: Int, val channels: Int, capacityMs: Int = 2000) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(channels >= 1) { "channels must be >= 1" }
        require(capacityMs > 0) { "capacityMs must be positive" }
    }

    private val capacityFrames: Int = maxOf(1, (sampleRate.toLong() * capacityMs / 1000L).toInt())
    private val leftRing = ShortArray(capacityFrames)
    private val rightRing = ShortArray(capacityFrames)

    @Volatile private var written: Long = 0L
    @Volatile private var anchorPos: Long = 0L
    @Volatile private var anchorMs: Long = 0L
    @Volatile private var lastWriteMs: Long = 0L
    @Volatile private var hasData: Boolean = false
    @Volatile private var epoch: Long = 0L

    private fun playheadAt(nowMs: Long, w: Long, aPos: Long, aMs: Long): Long {
        val elapsedMs = maxOf(0L, nowMs - aMs)
        val advanced = elapsedMs * sampleRate / 1000L
        return minOf(w, aPos + advanced)
    }

    /** Capture thread: [length] bytes of PCM that just went to the car, at [nowMs] (elapsedRealtime). */
    fun write(buf: ByteArray, offset: Int, length: Int, nowMs: Long) {
        val bytesPerFrame = 2 * channels
        val frames = length / bytesPerFrame
        if (frames <= 0) return

        val w = written
        if (!hasData || nowMs - lastWriteMs > 500L || playheadAt(nowMs, w, anchorPos, anchorMs) >= w) {
            anchorPos = w
            anchorMs = nowMs
        }
        lastWriteMs = nowMs

        val cap = capacityFrames
        var idx = (w % cap).toInt()
        var b = offset
        if (channels == 1) {
            for (i in 0 until frames) {
                val s = ((buf[b].toInt() and 0xff) or (buf[b + 1].toInt() shl 8)).toShort()
                leftRing[idx] = s
                rightRing[idx] = s
                b += 2
                idx++
                if (idx == cap) idx = 0
            }
        } else {
            for (i in 0 until frames) {
                val l = ((buf[b].toInt() and 0xff) or (buf[b + 1].toInt() shl 8)).toShort()
                val r = ((buf[b + 2].toInt() and 0xff) or (buf[b + 3].toInt() shl 8)).toShort()
                leftRing[idx] = l
                rightRing[idx] = r
                b += bytesPerFrame
                idx++
                if (idx == cap) idx = 0
            }
        }
        written = w + frames
        hasData = true
    }

    /**
     * UI: fill [dst] (any length, normally FFT_SIZE) with the frames that END at playhead(nowMs) - delayMs, as [mix], scaled to -1..1 (divide by 32768).
     * Returns false (and leaves dst alone) when there's nothing to show: nothing written yet, or that window is older than the ring holds.
     */
    fun read(nowMs: Long, delayMs: Int, mix: Mix, dst: FloatArray): Boolean {
        val ep = epoch
        if (!hasData) return false
        val n = dst.size
        if (n == 0) return true
        val cap = capacityFrames
        if (n > cap) return false

        val w = written
        val aPos = anchorPos
        val aMs = anchorMs
        if (w <= 0L) return false

        val ph = playheadAt(nowMs, w, aPos, aMs)
        val delayFrames = maxOf(0, delayMs).toLong() * sampleRate / 1000L
        val endFrame = ph - delayFrames
        val startFrame = endFrame - n
        if (startFrame < 0L) return false
        if (endFrame > w) return false
        val oldestAvailable = maxOf(0L, w - cap)
        if (startFrame < oldestAvailable) return false

        var idx = (startFrame % cap).toInt()
        val scale = 1f / 32768f
        val halfScale = 0.5f / 32768f

        when {
            channels == 1 || mix == Mix.LEFT -> {
                for (i in 0 until n) {
                    dst[i] = leftRing[idx].toInt() * scale
                    idx++
                    if (idx == cap) idx = 0
                }
            }
            mix == Mix.RIGHT -> {
                for (i in 0 until n) {
                    dst[i] = rightRing[idx].toInt() * scale
                    idx++
                    if (idx == cap) idx = 0
                }
            }
            else -> {
                for (i in 0 until n) {
                    dst[i] = (leftRing[idx].toInt() + rightRing[idx].toInt()) * halfScale
                    idx++
                    if (idx == cap) idx = 0
                }
            }
        }

        if (epoch != ep || !hasData) {
            dst.fill(0f)
            return false
        }
        return true
    }

    /** Sent but (by the model) not yet played, in ms. For the diagnostics log. */
    fun leadMs(nowMs: Long): Long {
        if (!hasData) return 0L
        val w = written
        val ph = playheadAt(nowMs, w, anchorPos, anchorMs)
        return maxOf(0L, (w - ph) * 1000L / sampleRate)
    }

    fun clear() {
        epoch++
        hasData = false
        written = 0L
        anchorPos = 0L
        anchorMs = 0L
        lastWriteMs = 0L
    }
}
