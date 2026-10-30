package com.angelsheriffmafia.lan

import com.angelsheriffmafia.engine.Role
import com.angelsheriffmafia.engine.Rules
import com.angelsheriffmafia.engine.VoteMode
import java.io.File
import kotlin.random.Random

/** Serves the browser page from the source tree, as the app does from its assets and resources. */
class SourceTreeFiles(private val root: File) : StaticFiles {
    override fun open(path: String): StaticFile? {
        val file = WebFiles.pages[path]?.let { File(root, "app/src/main/assets/web/$it") }
            ?: WebFiles.resources[path]?.let { res ->
                val (type, name) = res.split('/')
                if (type == "font") File(root, "app/src/main/res/font/$name.ttf") else File(root, "app/src/main/res/drawable-nodpi/$name.png")
            }
            ?: return null
        if (!file.isFile) return null
        return StaticFile(file.readBytes(), LanServer.contentType(file.name))
    }
}

/**
 * A host on this computer with bot players, for trying the browser page without phones.
 *
 *   ./gradlew -PengineOnly=true :lan:demo --args="--bots 6 --humans 1"
 *
 * then open http://localhost:8765 and join. The host deals once everyone is seated, and moves the
 * day along by itself. Bots check their cards, choose at night and vote.
 */
fun main(args: Array<String>) {
    fun arg(name: String, default: Int) = args.toList().zipWithNext().firstOrNull { it.first == "--$name" }?.second?.toIntOrNull() ?: default
    val bots = arg("bots", 6)
    val humans = arg("humans", 1)
    val talkSeconds = arg("talk", 20)
    val port = arg("port", DEFAULT_PORT)
    val secret = args.contains("--secret")
    val rules = Rules(nightSeconds = arg("night", 60), discussionSeconds = 60, voteMode = if (secret) VoteMode.SECRET else VoteMode.OPEN)
    val session = HostSession(TableState(name = HostSession.tableName("Demo"), rules = rules))
    val server = LanServer(session, SourceTreeFiles(File(".").absoluteFile))
    val bound = server.start(port)
    val botNames = listOf("Aarav", "Kabir", "Meera", "Dev", "Sana", "Ishaan", "Tara", "Neel", "Riya", "Zoya", "Vikram", "Anya", "Rohan", "Pia", "Omar")
    val tokens = botNames.take(bots).map { session.join(JoinRequest(it)).token!! }
    println("Demo table on http://localhost:$bound/  (${LocalNetwork.addresses().joinToString { it.ip }})")
    println("$bots bots seated, waiting for $humans more")

    val rnd = Random(1)
    var dealtAt = 0L
    var lastStage: Pair<Int, Stage>? = null
    var stageSince = System.currentTimeMillis()
    while (true) {
        Thread.sleep(400)
        val table = session.state
        val now = System.currentTimeMillis()
        val game = table.game
        if (game == null) {
            if (table.seats.size >= bots + humans && now - dealtAt > 3000) {
                session.host(HostCommand.Deal).onSuccess { dealtAt = now; println("Dealt") }
            }
            continue
        }
        val view = session.hostView()
        val stage = view.game!!.stage
        val key = view.game!!.day to stage
        if (key != lastStage) {
            lastStage = key
            stageSince = now
            println("Stage: $stage ${view.game!!.day}")
        }
        val age = now - stageSince
        // The host's buttons, pressed after a pause.
        when (stage) {
            Stage.DAWN -> if (age > 5000) session.host(HostCommand.StartTalking)
            Stage.DISCUSSION -> if (age > talkSeconds * 1000L) session.host(HostCommand.StartVote)
            Stage.RESULT -> if (age > 6000) session.host(HostCommand.Continue)
            else -> Unit
        }
        // The bots, each acting after their own little delay.
        tokens.forEach { token ->
            val v = session.view(token) ?: return@forEach
            val g = v.game ?: return@forEach
            val me = g.me ?: return@forEach
            if (rnd.nextInt(4) != 0 || age < 1500) return@forEach
            when (g.stage) {
                Stage.DEAL -> if (!g.deal!!.seen) session.act(token, PlayerAction.Seen)
                Stage.NIGHT -> {
                    val night = g.night!!
                    if (!night.canPick || night.targets.isEmpty()) return@forEach
                    if (me.role == Role.MAFIA) {
                        // Go along with the gang, or start with the first name.
                        val target = night.gangPicks.firstOrNull()?.target ?: night.targets.first()
                        if (night.myPick != target) session.act(token, PlayerAction.Pick(target))
                    } else {
                        session.act(token, PlayerAction.Pick(night.targets.random(rnd)))
                    }
                }
                Stage.VOTE -> {
                    val vote = g.vote!!
                    if (vote.canVote) session.act(token, PlayerAction.Vote(vote.candidates.filter { it != me.id }.randomOrNull(rnd)))
                }
                else -> Unit
            }
        }
    }
}
