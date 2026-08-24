package com.example.tvbrowser.input

import android.content.Context
import android.media.AudioManager

class AudioFocusController(
    context: Context,
    private val mediaKeys: MediaKeyInjector
) : AudioManager.OnAudioFocusChangeListener {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Suppress("DEPRECATION")
    fun request(): Boolean =
        audioManager.requestAudioFocus(
            this,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    @Suppress("DEPRECATION")
    fun abandon() {
        audioManager.abandonAudioFocus(this)
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> mediaKeys.pauseIfPlaying()
        }
    }
}
