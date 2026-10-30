package com.angelsheriffmafia.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.angelsheriffmafia.app.audio.Narration
import com.angelsheriffmafia.app.audio.Narrator
import com.angelsheriffmafia.app.audio.NarratorLine
import com.angelsheriffmafia.app.audio.Say
import com.angelsheriffmafia.app.data.GameStore
import com.angelsheriffmafia.app.data.Setup
import com.angelsheriffmafia.app.data.SetupStore
import com.angelsheriffmafia.engine.Action
import com.angelsheriffmafia.engine.Event
import com.angelsheriffmafia.engine.Game
import com.angelsheriffmafia.engine.GameSetupException
import com.angelsheriffmafia.engine.GameState
import com.angelsheriffmafia.engine.NewPlayer
import com.angelsheriffmafia.engine.Phase
import com.angelsheriffmafia.engine.PlayerId
import com.angelsheriffmafia.engine.Step
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MenuScreen { TITLE, SETUP, RULES, HOW_TO_PLAY }

data class UiState(
    val setup: Setup = Setup(),
    val setupLoaded: Boolean = false,
    val game: GameState? = null,
    val inGame: Boolean = false,
    val menu: MenuScreen = MenuScreen.TITLE,
    /** False while the "pass to X" cover is showing; true once X has unlocked their turn. */
    val turnOpen: Boolean = false,
    val turnOpenedAt: Long = 0L,
    val after: AfterTurn? = null,
    val error: String? = null,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val setupStore = SetupStore(app)
    private val gameStore = GameStore(app)
    val narrator = Narrator(app)

    private val _ui = MutableStateFlow(UiState(game = gameStore.load()))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /** Narration held back until the player on the phone has finished their private screen. */
    private var pendingNarration: List<Say> = emptyList()

    init {
        viewModelScope.launch {
            val setup = setupStore.setup.first()
            narrator.enabled = setup.narratorOn
            _ui.update { it.copy(setup = setup, setupLoaded = true) }
        }
    }

    override fun onCleared() {
        narrator.shutdown()
    }

    /* ----- Menus and setup ----- */

    fun showMenu(menu: MenuScreen) = _ui.update { it.copy(menu = menu, inGame = false, error = null) }

    fun updateSetup(setup: Setup) {
        narrator.enabled = setup.narratorOn
        _ui.update { it.copy(setup = setup, error = null) }
        viewModelScope.launch { setupStore.save(setup) }
    }

    fun startGame() {
        val setup = _ui.value.setup
        val names = setup.players.map { NewPlayer(it.name, it.pronunciation) }
        val game = try {
            Game.newGame(names, setup.rules)
        } catch (e: GameSetupException) {
            _ui.update { it.copy(error = e.message) }
            return
        }
        save(game)
        _ui.update { it.copy(game = game, inGame = true, turnOpen = false, after = null, error = null) }
        narrator.say(Say.Line(NarratorLine.DEAL_ROLES))
    }

    fun continueGame() = _ui.update { it.copy(inGame = true, turnOpen = false, after = null) }

    fun endGame() {
        narrator.stop()
        save(null)
        _ui.update { it.copy(game = null, inGame = false, menu = MenuScreen.TITLE, turnOpen = false, after = null) }
    }

    /** Pauses to the title screen. The game stays saved. */
    fun leaveGame() {
        narrator.stop()
        _ui.update { it.copy(inGame = false, menu = MenuScreen.TITLE, turnOpen = false, after = null) }
    }

    fun playAgain() {
        endGame()
        startGame()
    }

    fun previewName(text: String) = narrator.preview(text)

    /* ----- Turns on the phone ----- */

    fun openTurn() = _ui.update { it.copy(turnOpen = true, turnOpenedAt = SystemClock.elapsedRealtime()) }

    fun roleSeen() {
        val game = _ui.value.game ?: return
        dispatch(Action.RoleSeen(game.playerOnTurn ?: return))
    }

    fun nightPick(target: PlayerId) {
        val game = _ui.value.game ?: return
        val by = game.playerOnTurn ?: return
        val step = dispatch(Action.Pick(by, target), holdNarration = true) ?: return
        val check = step.events.filterIsInstance<Event.SheriffLearned>().firstOrNull()?.check
        _ui.update {
            it.copy(
                after = AfterTurn(
                    playerName = game.player(by).name,
                    kind = AfterTurn.Kind.NIGHT,
                    sheriffCheck = check,
                    targetName = game.player(target).name,
                    lastTurn = step.state.phase !is Phase.Night,
                ),
            )
        }
    }

    fun secretVote(target: PlayerId?) {
        val game = _ui.value.game ?: return
        val by = game.playerOnTurn ?: return
        val step = dispatch(Action.Vote(by, target), holdNarration = true) ?: return
        val stillVoting = (step.state.phase as? Phase.Voting)?.let { it.turn > 0 } == true
        _ui.update { it.copy(after = AfterTurn(game.player(by).name, AfterTurn.Kind.VOTE, lastTurn = !stillVoting)) }
    }

    fun finishAfter() {
        _ui.update { it.copy(after = null, turnOpen = false) }
        if (pendingNarration.isNotEmpty()) narrator.say(pendingNarration)
        pendingNarration = emptyList()
    }

    /* ----- The day, with the phone in the middle ----- */

    fun advance() {
        dispatch(Action.Advance)
    }

    fun startDiscussion() {
        dispatch(Action.StartDiscussion(System.currentTimeMillis()))
    }

    fun startVote() {
        dispatch(Action.StartVote)
    }

    /** Leaves the dawn screen and starts the discussion clock in one tap. */
    fun dawnDone() {
        advance()
        if (_ui.value.game?.phase is Phase.Discussion) startDiscussion()
    }

    fun discussionTimeUp() {
        narrator.say(Say.Line(NarratorLine.TIME_UP))
        if (_ui.value.game?.rules?.voteWhenTimeRunsOut == true) startVote()
    }

    fun openVote(top: List<PlayerId>) {
        dispatch(Action.OpenVote(top))
    }

    fun replayDawn() {
        val game = _ui.value.game ?: return
        narrator.say(Narration.lastDawn(game))
    }

    fun clearError() = _ui.update { it.copy(error = null) }

    /* ----- Plumbing ----- */

    private fun dispatch(action: Action, holdNarration: Boolean = false): Step? {
        val game = _ui.value.game ?: return null
        val step = Game.reduce(game, action).getOrElse { e ->
            _ui.update { it.copy(error = e.message) }
            return null
        }
        save(step.state)
        _ui.update { it.copy(game = step.state, turnOpen = false, error = null) }
        val narration = narrationFor(step.state, step.events)
        if (holdNarration) {
            pendingNarration = narration
        } else if (narration.isNotEmpty()) {
            narrator.say(narration)
        }
        return step
    }

    private fun save(state: GameState?) = gameStore.save(state)

    private fun narrationFor(state: GameState, events: List<Event>): List<Say> = Narration.forEvents(state, events) +
        if (events.isEmpty() && state.phase is Phase.Discussion && (state.phase as Phase.Discussion).startedAtMillis != null) {
            listOf(Say.Line(NarratorLine.DISCUSS))
        } else {
            emptyList()
        }
}
