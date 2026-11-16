package com.wavecat.outline.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class Speech(context: Context) {
    private enum class State { INITIALIZING, READY, FAILED }

    private class Phrase(val id: String, val text: String, val flush: Boolean)

    private val handler = Handler(Looper.getMainLooper())
    private val pending = ArrayList<Phrase>()
    private val waiters = HashMap<String, () -> Unit>()
    private var state = State.INITIALIZING
    private var nextId = 0

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) = finished(utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finished(utteranceId)

        override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)

        override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
    }

    private val tts = TextToSpeech(context.applicationContext) { status -> onInit(status) }

    @Synchronized
    fun speak(text: String, flush: Boolean, onDone: (() -> Unit)? = null) {
        val phrase = Phrase("outline-${nextId++}", text, flush)

        if (onDone != null)
            waiters[phrase.id] = onDone

        when (state) {
            State.READY -> say(phrase)

            State.INITIALIZING -> {
                if (flush) {
                    pending.forEach { release(it.id) }
                    pending.clear()
                }

                pending += phrase
            }

            State.FAILED -> release(phrase.id)
        }
    }

    @Synchronized
    fun shutdown() {
        waiters.keys.toList().forEach(::release)
        pending.clear()
        tts.shutdown()
    }

    @Synchronized
    private fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
            tts.setOnUtteranceProgressListener(listener)
            state = State.READY
            pending.forEach(::say)
        } else {
            state = State.FAILED
            pending.forEach { release(it.id) }
        }

        pending.clear()
    }

    private fun say(phrase: Phrase) {
        if (phrase.flush)
            waiters.keys.filter { it != phrase.id }.forEach(::release)

        val mode = if (phrase.flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD

        if (tts.speak(phrase.text, mode, null, phrase.id) != TextToSpeech.SUCCESS)
            release(phrase.id)
    }

    @Synchronized
    private fun finished(id: String?) {
        if (id != null)
            release(id)
    }

    private fun release(id: String) {
        val waiter = waiters.remove(id) ?: return
        handler.post(waiter)
    }
}
