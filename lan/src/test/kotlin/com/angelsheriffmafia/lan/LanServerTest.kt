package com.angelsheriffmafia.lan

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LanServerTest {

    private val session = HostSession(TableState(name = "Test table"))
    private val server = LanServer(session, StaticFiles { path ->
        if (path == "/") StaticFile("<h1>hi</h1>".toByteArray(), LanServer.contentType("index.html")) else null
    })
    private val port = server.start(0)
    private val client = LanClient("http://127.0.0.1:$port")

    @AfterTest
    fun stop() = server.stop()

    private fun get(path: String): Pair<Int, String> {
        val conn = URI.create("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection
        val code = conn.responseCode
        val body = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
        return code to body
    }

    @Test
    fun servesThePageAndSaysHello() {
        assertEquals(200 to "<h1>hi</h1>", get("/"))
        assertEquals(404, get("/nope").first)
        assertEquals(404, get("/../secret").first)
        val hello = client.hello()
        assertEquals("Test table", hello.table)
        assertEquals(PROTOCOL_VERSION, hello.protocol)
        assertFalse(hello.apk)
    }

    @Test
    fun joinPlayAndStreamOverHttp() = runBlocking {
        val tokens = listOf("Ava", "Ben", "Cal", "Dee", "Eli").map { client.join(JoinRequest(it, app = true)).token!! }
        assertNotNull(client.join(JoinRequest("ava")).error)
        assertEquals(5, client.hello().players)

        val first = withTimeout(5_000) {
            client.signals(tokens[0]).filterIsInstance<HostSignal.Update>().first()
        }
        assertEquals(SeatStatus.LOBBY, first.view.status)
        assertEquals("Ava", first.view.you!!.name)

        session.host(HostCommand.Deal).getOrThrow()
        val dealt = withTimeout(5_000) {
            client.signals(tokens[1]).filterIsInstance<HostSignal.Update>().first { it.view.game != null }
        }
        assertEquals(Stage.DEAL, dealt.view.game!!.stage)
        assertNotNull(dealt.view.game!!.me)

        tokens.forEach { assertTrue(client.act(it, PlayerAction.Seen).ok) }
        assertEquals(Stage.NIGHT, session.view(tokens[0])!!.game!!.stage)
        val bad = client.act(tokens[0], PlayerAction.Vote(1))
        assertFalse(bad.ok)
        assertNotNull(bad.error)
        assertFalse(client.act("not-a-token", PlayerAction.Seen).ok)
    }

    @Test
    fun aSignedOutPhoneIsTold() = runBlocking {
        val token = client.join(JoinRequest("Ava")).token!!
        session.letBackIn(session.state.seats.single().id).getOrThrow()
        val signal = withTimeout(5_000) {
            client.signals(token).first { it is HostSignal.SignedOut }
        }
        assertTrue((signal as HostSignal.SignedOut).reason.isNotEmpty())
        assertEquals(401, get("/api/view?token=$token").first)
    }

    @Test
    fun streamSeesChangesAsTheyHappen() = runBlocking {
        val token = client.join(JoinRequest("Ava")).token!!
        val seen = mutableListOf<Int>()
        withTimeout(5_000) {
            client.signals(token).filterIsInstance<HostSignal.Update>().first { update ->
                seen += update.view.seats.size
                if (update.view.seats.size == 1) Thread { client.join(JoinRequest("Ben")) }.start()
                update.view.seats.size == 2
            }
        }
        assertEquals(listOf(1, 2), seen.distinct())
    }

    @Test
    fun rejectsJunk() {
        val conn = URI.create("http://127.0.0.1:$port/api/act").toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.outputStream.use { it.write("{not json".toByteArray()) }
        assertEquals(400, conn.responseCode)
        assertEquals("http://10.0.0.2:8765", LanClient.baseFrom("10.0.0.2"))
        assertEquals("http://10.0.0.2:9000", LanClient.baseFrom(" http://10.0.0.2:9000/ "))
        assertEquals(null, LanClient.baseFrom("10.0.0.2:port"))
    }
}
