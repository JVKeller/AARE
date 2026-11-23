package net.prototux.aare.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import net.prototux.aare.DiagLog

/**
 * Keeps the phone's media volume at 0 while the car plays a copy of its sound.
 *
 * Screen-capture audio copies what apps play before the phone's volume is applied (checked in the
 * car: media volume 0 leaves the car's sound unchanged), but it can't stop the phone playing it
 * too. Muting the phone's media volume silences the original only. The volume to restore is saved
 * first, so a drive that never ended cleanly (the app killed while connected) is undone by
 * [recover] at the next start, like [net.prototux.aare.car.DrivingMode].
 *
 * Apps can refuse to be captured (the Claude app's read-aloud does): the car gets silence from
 * them, so while one plays the phone's volume is put back and they're heard from the phone rather
 * than not at all. Muted again shortly after it stops. [onUncapturable] hears about those either
 * way (muting on or off), so the music can make room for them.
 */
class PhoneMute(context: Context, private val onUncapturable: (Boolean) -> Unit = {}) {
    private val am = context.getSystemService(AudioManager::class.java)
    private val prefs = state(context)

    private val main = Handler(Looper.getMainLooper())
    private var listening = false
    /** Muting is on. */
    private var wanted = false
    /** An app that can't be captured plays (or did a moment ago). */
    private var heldOpen = false

    private val playback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) = update(configs)
    }
    private val muteAgain = Runnable {
        if (!heldOpen) return@Runnable
        heldOpen = false
        onUncapturable(false)
        if (wanted) mute()
    }

    /**
     * Watches what plays on the phone while [listen] (the car gets a copy of its sound), and mutes
     * ([on], only while listening) or restores the phone's media volume. Idempotent. Main thread.
     */
    fun set(listen: Boolean, on: Boolean) {
        val mute = listen && on
        if (listen != listening) {
            listening = listen
            if (listen) {
                runCatching { am.registerAudioPlaybackCallback(playback, main) }
            } else {
                runCatching { am.unregisterAudioPlaybackCallback(playback) }
                main.removeCallbacks(muteAgain)
                if (heldOpen) { heldOpen = false; onUncapturable(false) }
            }
        }
        val changed = mute != wanted
        wanted = mute
        if (!mute) restore(am, prefs, RESTORED)
        else if (changed) update(runCatching { am.activePlaybackConfigurations }.getOrDefault(emptyList()))
        if (listen && !changed) update(runCatching { am.activePlaybackConfigurations }.getOrDefault(emptyList()))
    }

    private fun update(configs: List<AudioPlaybackConfiguration>) {
        if (!listening) return
        if (configs.any { blocksCapture(it.audioAttributes) }) {
            main.removeCallbacks(muteAgain)
            if (!heldOpen) {
                heldOpen = true
                if (wanted && restore(am, prefs, null)) DiagLog.add("car audio: an app that can't be captured is playing, phone volume back on")
                onUncapturable(true)
            }
        } else if (heldOpen) {
            // Not at once: a voice reading aloud can drop its player between sentences.
            main.removeCallbacks(muteAgain)
            main.postDelayed(muteAgain, MUTE_AGAIN_MS)
        } else if (wanted) {
            mute()
        }
    }

    private fun mute() {
        if (prefs.contains(KEY_VOLUME)) return
        val v = runCatching { am.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrNull() ?: return
        // Already silent: nothing to undo later.
        if (v == 0) return
        // Saved first (commit, not apply): if the app dies now, the next start puts it back.
        prefs.edit().putInt(KEY_VOLUME, v).commit()
        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }
            .onSuccess { DiagLog.add("car audio: phone media volume muted while the car plays (was $v)") }
            .onFailure { prefs.edit().remove(KEY_VOLUME).apply(); DiagLog.add("car audio: can't mute the phone: ${it.message}") }
    }

    companion object {
        private const val KEY_VOLUME = "media_volume_before_car"
        private const val RESTORED = "car audio: phone media volume restored"
        private const val MUTE_AGAIN_MS = 1_500L

        /** Media the car would get as silence: sound screen capture may take, from an app that refuses it. */
        internal fun blocksCapture(a: AudioAttributes): Boolean =
            a.usage in CAPTURABLE_USAGES && a.allowedCapturePolicy != AudioAttributes.ALLOW_CAPTURE_BY_ALL

        private val CAPTURABLE_USAGES = setOf(AudioAttributes.USAGE_MEDIA, AudioAttributes.USAGE_GAME, AudioAttributes.USAGE_UNKNOWN)

        /** Puts back a media volume left muted by a drive that never ended. Never while a session runs. */
        fun recover(context: Context) {
            restore(context.getSystemService(AudioManager::class.java), state(context),
                "car audio: phone media volume left muted by an earlier drive, restored")
        }

        private fun state(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences("phone_mute", Context.MODE_PRIVATE)

        /** Puts the saved volume back; true if it did. */
        private fun restore(am: AudioManager, prefs: SharedPreferences, why: String?): Boolean {
            if (!prefs.contains(KEY_VOLUME)) return false
            val v = prefs.getInt(KEY_VOLUME, 0)
            prefs.edit().remove(KEY_VOLUME).apply()
            // Only if it's still the 0 we set: the user turning it up meanwhile wins.
            return runCatching {
                if (am.getStreamVolume(AudioManager.STREAM_MUSIC) != 0) return@runCatching false
                am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                why?.let(DiagLog::add)
                true
            }.getOrDefault(false)
        }
    }
}
