package com.angelsheriffmafia.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.angelsheriffmafia.app.data.SetupStore
import com.angelsheriffmafia.app.lan.HostRuntime
import com.angelsheriffmafia.app.lan.JoinedSeat
import com.angelsheriffmafia.app.lan.JoinedStore
import com.angelsheriffmafia.app.lan.TableFinder
import com.angelsheriffmafia.app.lan.TableStore
import com.angelsheriffmafia.app.ui.screens.JoinUi
import com.angelsheriffmafia.app.ui.screens.LinkState
import com.angelsheriffmafia.app.ui.screens.TableActions
import com.angelsheriffmafia.app.ui.screens.TableUi
import com.angelsheriffmafia.engine.Phase
import com.angelsheriffmafia.engine.Rules
import com.angelsheriffmafia.lan.APP_ID
import com.angelsheriffmafia.lan.HostCommand
import com.angelsheriffmafia.lan.HostSession
import com.angelsheriffmafia.lan.HostSignal
import com.angelsheriffmafia.lan.JoinRequest
import com.angelsheriffmafia.lan.LanClient
import com.angelsheriffmafia.lan.LocalNetwork
import com.angelsheriffmafia.lan.PROTOCOL_VERSION
import com.angelsheriffmafia.lan.PlayerAction
import com.angelsheriffmafia.lan.SeatStatus
import com.angelsheriffmafia.lan.TableState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Which part of playing on everyone's own phone is showing. NONE leaves the screen to the rest of the app. */
enum class TablePage { NONE, MODE, HOST_START, HOST_RULES, JOIN, TABLE }

data class TablesUi(
    val page: TablePage = TablePage.NONE,
    /** The table this phone hosts or sits at, once there's something to show. */
    val table: TableUi? = null,
    val join: JoinUi = JoinUi(),
    val hostError: String? = null,
    val hosting: Boolean = false,
    /** There's a table to go back to: hosted here, saved from before the app closed, or a seat elsewhere. */
    val atTable: Boolean = false,
)

/**
 * Hosting a table on this phone, or sitting at one on another phone. The host's table runs in
 * [HostRuntime]; a seat elsewhere is followed over the network with a [LanClient].
 */
class TableViewModel(private val app: Application) : AndroidViewModel(app) {
    private val tableStore = TableStore(app)
    private val joinedStore = JoinedStore(app)
    private val finder = TableFinder(app)

    private val _ui = MutableStateFlow(TablesUi())
    val ui: StateFlow<TablesUi> = _ui.asStateFlow()

    private var hostJob: Job? = null
    private var guestJob: Job? = null
    private var findJob: Job? = null

    /** A table this phone was hosting when the app last closed, ready to open again. */
    private var savedTable: TableState? = null
    private var seat: JoinedSeat? = joinedStore.load()
    private var hostName = ""
    private var hostPronunciation = ""

    init {
        val running = HostRuntime.hosting.value
        if (running != null) {
            watchHost(running)
        } else {
            savedTable = tableStore.load()?.takeIf { !it.closed }
        }
        refresh()
    }

    private fun refresh() = _ui.update {
        val hosting = HostRuntime.hosting.value != null
        it.copy(hosting = hosting, atTable = hosting || savedTable != null || seat != null)
    }

    /* ---------- Pages ---------- */

    fun showPage(page: TablePage) {
        if (page == TablePage.JOIN) startFinding() else stopFinding()
        _ui.update { it.copy(page = page, hostError = null) }
    }

    /** The join page, with this phone owner's name filled in from last time. */
    fun showJoin(address: String? = null, error: String? = null) {
        _ui.update { it.copy(join = it.join.copy(error = error, address = address ?: it.join.address)) }
        showPage(TablePage.JOIN)
        viewModelScope.launch {
            val setup = SetupStore(app).setup.first()
            _ui.update {
                if (it.join.name.isNotBlank()) it else it.copy(join = it.join.copy(name = setup.myName, pronunciation = setup.myPronunciation))
            }
        }
    }

    /** Opened from the browser page's "open in the app" link, or a scanned code. */
    fun openJoinLink(address: String) = showJoin(address)

    /** "Back to the table" on the title screen. */
    fun backToTable(narratorOn: Boolean) {
        val saved = savedTable
        when {
            HostRuntime.hosting.value != null -> showPage(TablePage.TABLE)
            saved != null -> {
                if (startHosting(saved, narratorOn)) showPage(TablePage.TABLE) else showPage(TablePage.HOST_START)
            }
            else -> seat?.let { joined ->
                if (guestJob?.isActive != true) follow(joined)
                showPage(TablePage.TABLE)
            }
        }
    }

    /* ---------- Hosting ---------- */

    /** Opens a new table, or renames the host at the open one. */
    fun openTable(name: String, pronunciation: String, plays: Boolean, rules: Rules, narratorOn: Boolean) {
        val justOpened = HostRuntime.hosting.value == null
        if (justOpened) {
            val table = savedTable ?: TableState(name = HostSession.tableName(name.trim().ifEmpty { null }), rules = rules)
            if (!startHosting(table, narratorOn)) return
        }
        val session = HostRuntime.hosting.value?.session ?: return
        val game = session.state.game
        if (game != null && game.phase !is Phase.GameOver) {
            // A saved game picked up where it left off. Names can change once it's over.
            showPage(TablePage.TABLE)
            return
        }
        hostName = name.trim()
        hostPronunciation = pronunciation.trim()
        val error = session.setHost(hostName, hostPronunciation.ifEmpty { null }, plays).exceptionOrNull()?.message
        if (error == null || justOpened) {
            showPage(TablePage.TABLE)
            setError(error)
        } else {
            _ui.update { it.copy(hostError = error) }
        }
    }

    private fun startHosting(table: TableState, narratorOn: Boolean): Boolean {
        val hosting = try {
            HostRuntime.open(app, table, narratorOn)
        } catch (e: IOException) {
            _ui.update { it.copy(hostError = "Couldn't open the table on this phone's network. Try again in a moment.") }
            return false
        }
        savedTable = null
        // Hosting here means leaving any table on another phone.
        leaveSeat(tellHost = true)
        hostName = hosting.session.state.hostSeat?.name ?: hostName
        hostPronunciation = hosting.session.state.hostSeat?.pronunciation ?: hostPronunciation
        watchHost(hosting)
        refresh()
        return true
    }

    private fun watchHost(hosting: HostRuntime.Hosting) {
        hostJob?.cancel()
        hostJob = viewModelScope.launch {
            var joinUrl: String? = null
            launch {
                // The address changes when the hotspot or Wi-Fi is switched on or off.
                while (true) {
                    val url = withContext(Dispatchers.IO) { LocalNetwork.addresses().firstOrNull()?.let { LocalNetwork.url(it.ip, hosting.port) } }
                    if (url != joinUrl) {
                        joinUrl = url
                        publishHost(hosting, url)
                    }
                    delay(3000)
                }
            }
            hosting.session.version.collect { publishHost(hosting, joinUrl) }
        }
    }

    private fun publishHost(hosting: HostRuntime.Hosting, joinUrl: String?) {
        if (HostRuntime.hosting.value !== hosting) return
        val view = hosting.session.hostView()
        _ui.update {
            it.copy(table = TableUi(view = view, isHost = true, joinUrl = joinUrl, error = it.table?.takeIf { t -> t.isHost }?.error))
        }
    }

    fun setRules(rules: Rules) {
        val error = HostRuntime.hosting.value?.session?.setRules(rules)?.exceptionOrNull()?.message
        if (error != null) setError(error)
    }

    fun setNarrator(on: Boolean) = HostRuntime.setNarrator(on)

    private fun hostDo(block: (HostSession) -> Result<Unit>) {
        val session = HostRuntime.hosting.value?.session ?: return
        setError(block(session).exceptionOrNull()?.message)
    }

    private fun closeTable() {
        HostRuntime.close(app)
        hostJob?.cancel()
        hostJob = null
        savedTable = null
        _ui.update { it.copy(table = null) }
        refresh()
        showPage(TablePage.NONE)
    }

    /* ---------- Sitting at another phone's table ---------- */

    fun setJoinAddress(text: String) = _ui.update { it.copy(join = it.join.copy(address = text, error = null)) }

    fun setJoinName(text: String) = _ui.update { it.copy(join = it.join.copy(name = text, error = null)) }

    fun setJoinPronunciation(text: String) = _ui.update { it.copy(join = it.join.copy(pronunciation = text)) }

    fun join() {
        val form = _ui.value.join
        val typed = form.address.trim()
        val base = LanClient.baseFrom(typed)
        if (base == null) {
            _ui.update { it.copy(join = it.join.copy(error = "That address doesn't look right. It looks like 192.168.43.1:8765.")) }
            return
        }
        if (HostRuntime.hosting.value != null) {
            _ui.update { it.copy(join = it.join.copy(error = "This phone is hosting a table. Close it before joining another.")) }
            return
        }
        _ui.update { it.copy(join = it.join.copy(busy = true, error = null)) }
        val previous = seat?.takeIf { it.base == base }?.token
        val request = JoinRequest(name = form.name.trim(), pronunciation = form.pronunciation.trim().ifEmpty { null }, token = previous, app = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { attemptJoin(base, typed, request) }
            _ui.update { it.copy(join = it.join.copy(busy = false)) }
            result.onSuccess { joined ->
                // A different table: leave the old seat behind.
                if (seat != null && seat?.token != joined.token) leaveSeat(tellHost = true)
                seat = joined
                joinedStore.save(joined)
                refresh()
                follow(joined)
                showPage(TablePage.TABLE)
            }.onFailure { e ->
                _ui.update { it.copy(join = it.join.copy(error = e.message)) }
            }
        }
    }

    private fun attemptJoin(base: String, typed: String, request: JoinRequest): Result<JoinedSeat> {
        val client = LanClient(base)
        return try {
            val hello = client.hello()
            when {
                hello.app != APP_ID -> Result.failure(IOException("That isn't an Angel Sheriff Mafia table."))
                hello.protocol != PROTOCOL_VERSION -> Result.failure(
                    IOException("That table runs a different version of the game. Put the same version on both phones."),
                )
                else -> {
                    val response = client.join(request)
                    val token = response.token
                    if (token != null) {
                        Result.success(JoinedSeat(base, token, hello.table))
                    } else {
                        Result.failure(IOException(response.error ?: "The host said no."))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(IOException("Couldn't reach a table at $typed. Are you on the same Wi-Fi or hotspot as the host?"))
        }
    }

    private fun follow(seat: JoinedSeat) {
        guestJob?.cancel()
        _ui.update { it.copy(table = null) }
        guestJob = viewModelScope.launch {
            LanClient(seat.base).signals(seat.token).collect { signal ->
                when (signal) {
                    is HostSignal.Update -> {
                        _ui.update {
                            val old = it.table?.takeIf { t -> !t.isHost }
                            it.copy(
                                table = TableUi(
                                    view = signal.view,
                                    isHost = false,
                                    busy = old?.busy ?: false,
                                    error = old?.error,
                                    clockOffset = signal.view.now - System.currentTimeMillis(),
                                ),
                            )
                        }
                        // The host closed the table. Keep showing it, but there's nothing left to follow.
                        if (signal.view.status == SeatStatus.CLOSED) forgetSeat()
                    }
                    is HostSignal.Lost -> _ui.update { it.copy(table = it.table?.copy(link = LinkState.RECONNECTING)) }
                    is HostSignal.SignedOut -> {
                        val onTable = _ui.value.page == TablePage.TABLE
                        _ui.update { it.copy(table = null) }
                        if (onTable) showJoin(seat.base.removePrefix("http://"), error = signal.reason)
                        forgetSeat()
                    }
                }
            }
        }
    }

    private fun guestAct(action: PlayerAction) {
        val seat = seat ?: return
        if (_ui.value.table?.busy == true) return
        _ui.update { it.copy(table = it.table?.copy(busy = true, error = null)) }
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching { LanClient(seat.base).act(seat.token, action) }
                    .fold({ if (it.ok) null else it.error ?: "That didn't work." }, { "Couldn't reach the host. Try again in a moment." })
            }
            _ui.update { it.copy(table = it.table?.copy(busy = false, error = error)) }
        }
    }

    /** Gives up this phone's seat elsewhere, telling the host when it can. */
    private fun leaveSeat(tellHost: Boolean) {
        val old = seat ?: return
        forgetSeat()
        if (tellHost) {
            viewModelScope.launch(Dispatchers.IO) { runCatching { LanClient(old.base).act(old.token, PlayerAction.Leave) } }
        }
    }

    private fun forgetSeat() {
        guestJob?.cancel()
        guestJob = null
        seat = null
        joinedStore.save(null)
        refresh()
    }

    /* ---------- Finding tables ---------- */

    private fun startFinding() {
        if (findJob?.isActive == true) return
        findJob = viewModelScope.launch {
            finder.tables().collect { found ->
                _ui.update {
                    // With exactly one table around and nothing typed, take it.
                    val address = if (it.join.address.isBlank() && found.size == 1) found[0].address else it.join.address
                    it.copy(join = it.join.copy(found = found, address = address))
                }
            }
        }
    }

    private fun stopFinding() {
        findJob?.cancel()
        findJob = null
        _ui.update { it.copy(join = it.join.copy(found = emptyList())) }
    }

    /* ---------- What the table screens ask for ---------- */

    private fun setError(error: String?) = _ui.update { it.copy(table = it.table?.copy(error = error)) }

    val actions = object : TableActions {
        override fun act(action: PlayerAction) {
            if (HostRuntime.hosting.value != null) hostDo { it.hostAct(action) } else guestAct(action)
        }

        override fun host(command: HostCommand) = hostDo { it.host(command) }

        override fun replayDawn() = HostRuntime.replayDawn()

        override fun letBackIn(seat: Int) = hostDo { it.letBackIn(seat) }

        override fun removeSeat(seat: Int) = hostDo { it.removeSeat(seat) }

        override fun setPlaying(plays: Boolean) = hostDo { it.setHost(hostName, hostPronunciation.ifEmpty { null }, plays) }

        override fun editHost() = showPage(TablePage.HOST_START)

        override fun openRules() = showPage(TablePage.HOST_RULES)

        override fun leaveTable() {
            val status = _ui.value.table?.view?.status
            leaveSeat(tellHost = status != SeatStatus.CLOSED)
            _ui.update { it.copy(table = null) }
            showPage(TablePage.NONE)
        }

        override fun closeTable() = this@TableViewModel.closeTable()

        override fun pause() = showPage(TablePage.NONE)

        override fun dismissError() = setError(null)
    }

    override fun onCleared() {
        // The hosted table carries on without this screen: HostRuntime belongs to the app.
        stopFinding()
    }
}
