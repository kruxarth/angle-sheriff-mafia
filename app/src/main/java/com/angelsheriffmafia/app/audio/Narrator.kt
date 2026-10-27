package com.angelsheriffmafia.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * The fixed narrator lines. Each can ship as a recorded clip in
 * `assets/narrator/<key>.ogg` (or .mp3). Until a clip exists, the phone's
 * built-in voice reads [text] instead, so the game always has a narrator.
 * See docs/narrator-audio.md for the recording list.
 */
enum class NarratorLine(val key: String, val text: String) {
    DEAL_ROLES("deal_roles", "Welcome to Dry Gulch. Pass the phone around, and each of you check your role in secret."),
    NIGHT_FALLS("night_falls", "Night falls on Dry Gulch. Pass the phone around, and keep your eyes to yourself."),
    MORNING("morning", "The sun is up, and the rooster is crowing."),
    WAS_KILLED("was_killed", "was found dead this morning."),
    SAVED("saved", "Someone was attacked in the night, but the Angel saved them."),
    WAS_MAFIA("was_mafia", "They were Mafia."),
    WAS_ANGEL("was_angel", "They were the Angel."),
    WAS_SHERIFF("was_sheriff", "They were the Sheriff."),
    WAS_VILLAGER("was_villager", "They were a villager."),
    DISCUSS("discuss", "Talk it over. Who among you is Mafia?"),
    TIME_UP("time_up", "Time's up. It's time to vote."),
    TIE("tie", "It's a tie. Vote again between the tied players."),
    RUN_OUT("run_out", "has been run out of town."),
    NOBODY_OUT("nobody_out", "Nobody leaves town today."),
    TOWN_WINS("town_wins", "The Mafia are gone. The town of Dry Gulch is safe."),
    MAFIA_WINS("mafia_wins", "The Mafia have taken over Dry Gulch."),
}

sealed interface Say {
    data class Line(val line: NarratorLine) : Say

    /** Free text, such as a player's name, always read by the on-device voice. */
    data class Text(val text: String) : Say
}

class Narrator(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val clips: Set<String> = runCatching { appContext.assets.list("narrator")?.toSet() }.getOrNull().orEmpty()
    private val tts = TextToSpeech(appContext, this)
    private var ready = false
    private var queue = ArrayDeque<Say>()
    private var player: MediaPlayer? = null
    private var generation = 0

    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) stop()
        }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        // English everywhere. A phone set to an English variant (for example en-IN) keeps its
        // own accent, which tends to say local names better; anything else falls back to US English.
        val device = Locale.getDefault()
        val locale = if (device.language == "en" && tts.isLanguageAvailable(device) >= TextToSpeech.LANG_AVAILABLE) device else Locale.US
        tts.language = locale
        tts.setSpeechRate(0.95f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = advance(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = advance(utteranceId)
        })
        main.post {
            ready = true
            playNext()
        }
    }

    /** Replaces whatever is being said with [parts], spoken in order. */
    fun say(parts: List<Say>) {
        stop()
        if (!enabled) return
        queue = ArrayDeque(parts)
        if (ready) playNext()
    }

    fun say(vararg parts: Say) = say(parts.toList())

    /** Reads a name straight away, for the pronunciation preview in setup. */
    fun preview(text: String) {
        val wasEnabled = enabled
        enabled = true
        say(Say.Text(text))
        enabled = wasEnabled
    }

    fun stop() {
        generation++
        queue.clear()
        player?.release()
        player = null
        if (ready) tts.stop()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private fun advance(utteranceId: String?) {
        main.post { if (utteranceId == generation.toString()) playNext() }
    }

    private fun playNext() {
        val next = queue.removeFirstOrNull() ?: return
        when (next) {
            is Say.Line -> {
                val clip = clips.firstOrNull { it.substringBeforeLast('.') == next.line.key }
                if (clip != null && playClip("narrator/$clip")) return
                speak(next.line.text)
            }
            is Say.Text -> speak(next.text)
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, generation.toString())
    }

    private fun playClip(path: String): Boolean = runCatching {
        val gen = generation
        appContext.assets.openFd(path).use { fd ->
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                setOnCompletionListener {
                    it.release()
                    if (player === it) player = null
                    if (gen == generation) playNext()
                }
                prepare()
                start()
            }
        }
        true
    }.getOrDefault(false)
}
