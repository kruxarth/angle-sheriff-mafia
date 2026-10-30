package com.angelsheriffmafia.app.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import com.angelsheriffmafia.app.audio.Narration
import com.angelsheriffmafia.app.audio.Narrator
import com.angelsheriffmafia.app.audio.NarratorLine
import com.angelsheriffmafia.app.audio.Say
import com.angelsheriffmafia.lan.Cue
import com.angelsheriffmafia.lan.DEFAULT_PORT
import com.angelsheriffmafia.lan.HostSession
import com.angelsheriffmafia.lan.LanServer
import com.angelsheriffmafia.lan.TableState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors

/** NSD wants the service type without the trailing dot. */
internal const val NSD_TYPE = "_angelsheriff._tcp"

/**
 * The table this phone hosts. It belongs to the app process rather than a screen, so turning
 * the phone or leaving for the title doesn't stop it, and [HostService] keeps the process in
 * the foreground while it's open.
 */
object HostRuntime {

    class Hosting(val session: HostSession, val port: Int)

    private val _hosting = MutableStateFlow<Hosting?>(null)
    val hosting: StateFlow<Hosting?> = _hosting.asStateFlow()

    private val main = Handler(Looper.getMainLooper())

    // Saves happen in order, one at a time, off the threads that answer players.
    private val saver = Executors.newSingleThreadExecutor { r -> Thread(r, "table-saver").apply { isDaemon = true } }
    private var server: LanServer? = null
    private var narrator: Narrator? = null
    private var advertiser: Advertiser? = null

    /** Opens [table] to the local network. Throws an IOException if no port is free. */
    @MainThread
    fun open(context: Context, table: TableState, narratorOn: Boolean): Hosting {
        _hosting.value?.let { return it }
        val app = context.applicationContext
        val store = TableStore(app)
        val session = HostSession(table)
        val server = LanServer(session, AndroidStaticFiles(app), apk = { File(app.applicationInfo.sourceDir) })
        val port = server.start(DEFAULT_PORT)
        val narrator = Narrator(app).also { it.enabled = narratorOn }
        val advertiser = Advertiser(app, port).also { it.advertise(table.name) }
        session.listener = object : HostSession.Listener {
            override fun onChanged(table: TableState) {
                saver.execute { store.save(table) }
                main.post { advertiser.advertise(table.name) }
            }

            override fun onCues(cues: List<Cue>, table: TableState) {
                val lines = narration(cues, table)
                if (lines.isNotEmpty()) main.post { narrator.say(lines) }
            }
        }
        this.server = server
        this.narrator = narrator
        this.advertiser = advertiser
        store.save(table)
        HostService.start(app, table.name)
        return Hosting(session, port).also { _hosting.value = it }
    }

    /** Closes the table for everyone and forgets it. */
    @MainThread
    fun close(context: Context) {
        val hosting = _hosting.value ?: return
        val app = context.applicationContext
        hosting.session.close()
        val server = server
        // A moment for every phone to hear that the table closed before the server goes quiet.
        main.postDelayed({ server?.stop() }, 1500)
        advertiser?.stop()
        narrator?.shutdown()
        saver.execute { TableStore(app).save(null) }
        this.server = null
        this.narrator = null
        this.advertiser = null
        _hosting.value = null
        HostService.stop(app)
    }

    fun setNarrator(on: Boolean) {
        narrator?.enabled = on
    }

    /** Reads the last night's news out again. */
    fun replayDawn() {
        val game = _hosting.value?.session?.state?.game ?: return
        narrator?.say(Narration.lastDawn(game))
    }

    private fun narration(cues: List<Cue>, table: TableState): List<Say> {
        val game = table.game ?: return emptyList()
        return cues.flatMap { cue ->
            when (cue) {
                Cue.Dealt -> listOf(Say.Line(NarratorLine.DEAL_OWN))
                Cue.Discussion -> listOf(Say.Line(NarratorLine.DISCUSS))
                Cue.TimeUp -> listOf(Say.Line(NarratorLine.TIME_UP))
                is Cue.Game -> Narration.forEvents(game, listOf(cue.event))
            }
        }
    }

    /** Announces the table on the network so phones with the app find it without typing an address. */
    private class Advertiser(context: Context, private val port: Int) {
        private val nsd: NsdManager? = context.getSystemService(NsdManager::class.java)
        private var name: String? = null

        // NSD wants a fresh listener for every registration.
        private var listener: NsdManager.RegistrationListener? = null

        fun advertise(tableName: String) {
            val nsd = nsd ?: return
            if (tableName == name) return
            stop()
            name = tableName
            val info = NsdServiceInfo().apply {
                serviceName = tableName
                serviceType = NSD_TYPE
                port = this@Advertiser.port
            }
            val next = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) = Unit
                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            }
            if (runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, next) }.isSuccess) listener = next
        }

        fun stop() {
            listener?.let { runCatching { nsd?.unregisterService(it) } }
            listener = null
            name = null
        }
    }
}
