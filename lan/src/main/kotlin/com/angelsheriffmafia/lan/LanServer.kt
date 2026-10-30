package com.angelsheriffmafia.lan

import kotlinx.serialization.SerializationException
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** A file the web page needs: the page itself, its script, fonts and pictures. */
class StaticFile(val bytes: ByteArray, val contentType: String)

/** Where the server finds the web page's files. On the phone, the app's assets and resources. */
fun interface StaticFiles {
    /** [path] is the URL path, such as "/" or "/fonts/rye.ttf". Null if there's no such file. */
    fun open(path: String): StaticFile?
}

/**
 * A small HTTP server for the local network. Single use: once stopped, make a new one. It serves the browser page and the JSON API in
 * [Protocol.kt], and pushes table changes to each phone as server-sent events.
 *
 * Phones on the same Wi-Fi or hotspot reach it at http://<host address>:<port>/.
 */
class LanServer(
    private val session: HostSession,
    private val files: StaticFiles,
    /** The host's own copy of the app, so friends can install it from the table. Null if not offered. */
    private val apk: (() -> File?)? = null,
) {
    private var socket: ServerSocket? = null
    private var ticker: ScheduledExecutorService? = null
    private val workers = ThreadPoolExecutor(0, MAX_CONNECTIONS, 30, TimeUnit.SECONDS, SynchronousQueue()) { r ->
        Thread(r, "lan-http").apply { isDaemon = true }
    }

    @Volatile
    var port: Int = 0
        private set

    /** Starts listening on [preferredPort], or the next free port after it. Returns the port. */
    fun start(preferredPort: Int = DEFAULT_PORT): Int {
        check(socket == null) { "Already started" }
        var lastError: IOException? = null
        for (candidate in preferredPort until preferredPort + 10) {
            val server = ServerSocket()
            try {
                server.reuseAddress = true
                server.bind(InetSocketAddress(candidate), 50)
            } catch (e: IOException) {
                server.close()
                lastError = e
                continue
            }
            socket = server
            port = server.localPort
            Thread({ acceptLoop(server) }, "lan-accept").apply { isDaemon = true }.start()
            ticker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "lan-clock").apply { isDaemon = true } }.also {
                it.scheduleWithFixedDelay({ runCatching { session.tick() } }, 250, 250, TimeUnit.MILLISECONDS)
            }
            return port
        }
        throw lastError ?: IOException("No free port")
    }

    fun stop() {
        ticker?.shutdownNow()
        ticker = null
        runCatching { socket?.close() }
        socket = null
        workers.shutdownNow()
    }

    private fun acceptLoop(server: ServerSocket) {
        while (!server.isClosed) {
            val client = try {
                server.accept()
            } catch (e: IOException) {
                break
            }
            try {
                workers.execute { handle(client) }
            } catch (e: RejectedExecutionException) {
                runCatching { client.close() }
            }
        }
    }

    private fun handle(client: Socket) {
        client.use {
            try {
                client.soTimeout = READ_TIMEOUT_MS
                client.tcpNoDelay = true
                client.keepAlive = true
                val request = readRequest(client.getInputStream()) ?: return
                val out = BufferedOutputStream(client.getOutputStream())
                route(request, out)
                out.flush()
            } catch (_: IOException) {
                // The phone went away mid-request. Nothing to do.
            }
        }
    }

    private class Request(val method: String, val path: String, val query: Map<String, String>, val body: ByteArray)

    private fun route(req: Request, out: OutputStream) {
        when {
            req.path == "/api/hello" && req.method == "GET" -> json(out, 200, LanJson.encodeToString(Hello.serializer(), hello()))
            req.path == "/api/join" && req.method == "POST" -> {
                val body = decode(req, JoinRequest.serializer()) ?: return badRequest(out)
                val response = session.join(body)
                json(out, if (response.error == null) 200 else 409, LanJson.encodeToString(JoinResponse.serializer(), response))
            }
            req.path == "/api/act" && req.method == "POST" -> {
                val body = decode(req, ActRequest.serializer()) ?: return badRequest(out)
                val response = session.act(body.token, body.action)
                json(out, if (response.ok) 200 else 409, LanJson.encodeToString(ActResponse.serializer(), response))
            }
            req.path == "/api/view" && req.method == "GET" -> {
                val view = session.view(req.query["token"].orEmpty())
                if (view == null) {
                    json(out, 401, LanJson.encodeToString(Gone.serializer(), Gone(signedOutReason())))
                } else {
                    json(out, 200, LanJson.encodeToString(TableView.serializer(), view))
                }
            }
            req.path == "/api/events" && req.method == "GET" -> events(req.query["token"].orEmpty(), out)
            req.path == "/app.apk" && req.method == "GET" -> sendApk(out)
            req.method == "GET" || req.method == "HEAD" -> {
                val file = files.open(req.path)
                if (file == null) {
                    respond(out, 404, "text/plain; charset=utf-8", "Not found".toByteArray())
                } else {
                    respond(out, 200, file.contentType, file.bytes, cache = "no-cache", head = req.method == "HEAD")
                }
            }
            else -> respond(out, 405, "text/plain; charset=utf-8", "Method not allowed".toByteArray())
        }
    }

    private fun hello(): Hello {
        val table = session.state
        return Hello(table = table.name, players = table.seats.size, inGame = table.game != null, apk = apk?.invoke() != null)
    }

    private fun signedOutReason() = if (session.state.closed) "The host closed the table." else "You're no longer at this table."

    /** Pushes the seat's view whenever the table changes, with a heartbeat so dead phones are noticed. */
    private fun events(token: String, out: OutputStream) {
        out.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream; charset=utf-8\r\nCache-Control: no-store\r\n" +
                "Connection: keep-alive\r\nX-Accel-Buffering: no\r\n\r\nretry: 2000\n\n").toByteArray(),
        )
        out.flush()
        val seatId = session.seatIdFor(token)
        if (seatId == null) {
            sendEvent(out, "gone", LanJson.encodeToString(Gone.serializer(), Gone(signedOutReason())))
            return
        }
        session.streamOpened(seatId)
        try {
            var sent = -1L
            while (true) {
                val version = session.awaitVersion(sent, HEARTBEAT_MS)
                if (version == sent) {
                    out.write(": ping\n\n".toByteArray())
                    out.flush()
                    continue
                }
                val view = session.view(token)
                if (view == null) {
                    sendEvent(out, "gone", LanJson.encodeToString(Gone.serializer(), Gone(signedOutReason())))
                    return
                }
                sendEvent(out, "view", LanJson.encodeToString(TableView.serializer(), view))
                sent = view.version
                if (view.status == SeatStatus.CLOSED) return
            }
        } catch (_: SocketException) {
            // Phone locked, browser closed or Wi-Fi dropped. The page reconnects by itself.
        } catch (_: InterruptedException) {
            // The server is stopping.
        } finally {
            session.streamClosed(seatId)
        }
    }

    private fun sendEvent(out: OutputStream, event: String, data: String) {
        out.write("event: $event\ndata: $data\n\n".toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun sendApk(out: OutputStream) {
        val file = apk?.invoke()
        if (file == null || !file.isFile) {
            respond(out, 404, "text/plain; charset=utf-8", "The host isn't sharing the app.".toByteArray())
            return
        }
        out.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.android.package-archive\r\nContent-Length: ${file.length()}\r\n" +
                "Content-Disposition: attachment; filename=\"angel-sheriff-mafia.apk\"\r\nConnection: close\r\n\r\n").toByteArray(),
        )
        file.inputStream().use { it.copyTo(out) }
    }

    private fun <T> decode(req: Request, serializer: kotlinx.serialization.KSerializer<T>): T? = try {
        LanJson.decodeFromString(serializer, req.body.toString(Charsets.UTF_8))
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun badRequest(out: OutputStream) =
        json(out, 400, LanJson.encodeToString(ActResponse.serializer(), ActResponse(ok = false, error = "That didn't make sense to the host.")))

    private fun json(out: OutputStream, status: Int, body: String) =
        respond(out, status, "application/json; charset=utf-8", body.toByteArray(Charsets.UTF_8))

    private fun respond(out: OutputStream, status: Int, type: String, body: ByteArray, cache: String = "no-store", head: Boolean = false) {
        val header = "HTTP/1.1 $status ${reason(status)}\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            "Cache-Control: $cache\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray())
        if (!head) out.write(body)
    }

    private fun reason(status: Int) = when (status) {
        200 -> "OK"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        409 -> "Conflict"
        413 -> "Payload Too Large"
        else -> "Error"
    }

    /** Reads one request: the request line, headers and a small body. Null if it's malformed or too big. */
    private fun readRequest(input: InputStream): Request? {
        val head = ByteArrayOutputStream()
        // The header ends at the first blank line: \r\n\r\n.
        var last4 = 0
        while (last4 != 0x0D0A0D0A) {
            val b = input.read()
            if (b < 0) return null
            head.write(b)
            if (head.size() > MAX_HEADER_BYTES) return null
            last4 = (last4 shl 8) or b
        }
        val lines = head.toString(Charsets.ISO_8859_1.name()).split("\r\n")
        val parts = lines.first().split(" ")
        if (parts.size < 3) return null
        val headers = lines.drop(1).filter { it.contains(':') }.associate {
            it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length < 0 || length > MAX_BODY_BYTES) return null
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) return null
            read += n
        }
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').filter { it.contains('=') }.associate {
            decodeUrl(it.substringBefore('=')) to decodeUrl(it.substringAfter('='))
        }
        return Request(parts[0].uppercase(), path, query, body)
    }

    private fun decodeUrl(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    companion object {
        private const val MAX_CONNECTIONS = 64
        private const val MAX_HEADER_BYTES = 8 * 1024
        private const val MAX_BODY_BYTES = 8 * 1024
        private const val READ_TIMEOUT_MS = 20_000
        private const val HEARTBEAT_MS = 15_000L

        fun contentType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html; charset=utf-8"
            "js" -> "text/javascript; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "png" -> "image/png"
            "svg" -> "image/svg+xml"
            "ttf" -> "font/ttf"
            "json" -> "application/json; charset=utf-8"
            else -> "application/octet-stream"
        }
    }
}
