package com.angelsheriffmafia.lan

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.KSerializer
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** What a joined phone hears from the host. */
sealed interface HostSignal {
    data class Update(val view: TableView) : HostSignal

    /** The connection dropped. The client keeps trying. */
    data class Lost(val attempt: Int) : HostSignal

    /** This phone no longer has a seat (the host let someone else in or closed the table). */
    data class SignedOut(val reason: String) : HostSignal
}

/**
 * Talks to a host's [LanServer] from another phone that has the app. Calls other than
 * [signals] block, so run them off the main thread. [base] is like "http://192.168.43.1:8765".
 */
class LanClient(val base: String) {

    fun hello(): Hello = request("GET", "/api/hello", null, Hello.serializer())

    fun join(request: JoinRequest): JoinResponse =
        request("POST", "/api/join", LanJson.encodeToString(JoinRequest.serializer(), request), JoinResponse.serializer())

    fun act(token: String, action: PlayerAction): ActResponse =
        request("POST", "/api/act", LanJson.encodeToString(ActRequest.serializer(), ActRequest(token, action)), ActResponse.serializer())

    /**
     * The seat's view as it changes. Reconnects by itself when the Wi-Fi drops or the phone
     * sleeps, and ends after [HostSignal.SignedOut]. Reading happens on its own thread, and
     * cancelling the flow hangs up at once.
     */
    fun signals(token: String): Flow<HostSignal> = callbackFlow {
        val stopped = AtomicBoolean(false)
        val current = AtomicReference<HttpURLConnection?>(null)
        val reader = thread(name = "lan-events", isDaemon = true) {
            var attempt = 0
            while (!stopped.get()) {
                var signedOut: String? = null
                try {
                    val conn = open("GET", "/api/events?token=$token", readTimeoutMs = 40_000)
                    current.set(conn)
                    if (stopped.get()) break
                    conn.setRequestProperty("Accept", "text/event-stream")
                    if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
                    signedOut = readEvents(conn) {
                        attempt = 0
                        trySendBlocking(HostSignal.Update(it))
                    }
                } catch (_: IOException) {
                    // Fall through to reconnect.
                } catch (_: IllegalArgumentException) {
                    // A garbled message: reconnect and get a fresh view.
                } finally {
                    current.getAndSet(null)?.disconnect()
                }
                if (stopped.get()) break
                if (signedOut != null) {
                    trySendBlocking(HostSignal.SignedOut(signedOut))
                    channel.close()
                    break
                }
                attempt++
                trySendBlocking(HostSignal.Lost(attempt))
                try {
                    Thread.sleep(minOf(5_000L, 500L * attempt))
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
        awaitClose {
            stopped.set(true)
            current.get()?.disconnect()
            reader.interrupt()
        }
    }

    /** Reads server-sent events until the stream ends. Returns the reason if the seat was signed out. */
    private fun readEvents(conn: HttpURLConnection, onView: (TableView) -> Unit): String? {
        conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            var event = "message"
            val data = StringBuilder()
            while (true) {
                val line = reader.readLine() ?: return null
                when {
                    line.isEmpty() -> {
                        val text = data.toString()
                        data.clear()
                        if (text.isNotEmpty()) {
                            when (event) {
                                "view" -> onView(LanJson.decodeFromString(TableView.serializer(), text))
                                "gone" -> return LanJson.decodeFromString(Gone.serializer(), text).reason
                            }
                        }
                        event = "message"
                    }
                    line.startsWith(":") -> Unit
                    line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.removePrefix("data:").removePrefix(" "))
                    }
                }
            }
        }
    }

    private fun <T> request(method: String, path: String, body: String?, serializer: KSerializer<T>): T {
        val conn = open(method, path, readTimeoutMs = 8_000)
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream ?: throw IOException("HTTP $code")
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return try {
                LanJson.decodeFromString(serializer, text)
            } catch (e: IllegalArgumentException) {
                throw IOException("The host sent something unexpected (HTTP $code).", e)
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(method: String, path: String, readTimeoutMs: Int): HttpURLConnection {
        // Phones on the same Wi-Fi talk directly, never through a proxy.
        val conn = URI.create(base.trimEnd('/') + path).toURL().openConnection(Proxy.NO_PROXY) as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 5_000
        conn.readTimeout = readTimeoutMs
        conn.useCaches = false
        return conn
    }

    companion object {
        /** Turns what someone typed ("192.168.1.5", "192.168.1.5:8765", a full URL) into a base URL. */
        fun baseFrom(typed: String): String? {
            val text = typed.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
            if (text.isEmpty() || text.contains('/') || text.contains(' ')) return null
            val host = text.substringBefore(':')
            val port = text.substringAfter(':', DEFAULT_PORT.toString()).toIntOrNull() ?: return null
            if (host.isEmpty() || port !in 1..65535) return null
            return "http://$host:$port"
        }
    }
}
