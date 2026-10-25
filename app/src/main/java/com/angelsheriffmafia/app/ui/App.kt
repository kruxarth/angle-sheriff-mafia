package com.angelsheriffmafia.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.angelsheriffmafia.app.GameViewModel
import com.angelsheriffmafia.app.MenuScreen
import com.angelsheriffmafia.app.UiState
import com.angelsheriffmafia.app.ui.screens.AfterTurnScreen
import com.angelsheriffmafia.app.ui.screens.DawnScreen
import com.angelsheriffmafia.app.ui.screens.DiscussionScreen
import com.angelsheriffmafia.app.ui.screens.GameOverScreen
import com.angelsheriffmafia.app.ui.screens.HowToPlayScreen
import com.angelsheriffmafia.app.ui.screens.NightPickScreen
import com.angelsheriffmafia.app.ui.screens.OpenVoteScreen
import com.angelsheriffmafia.app.ui.screens.PassScreen
import com.angelsheriffmafia.app.ui.screens.RoleCardScreen
import com.angelsheriffmafia.app.ui.screens.RulesScreen
import com.angelsheriffmafia.app.ui.screens.SecretVoteScreen
import com.angelsheriffmafia.app.ui.screens.SetupScreen
import com.angelsheriffmafia.app.ui.screens.TitleScreen
import com.angelsheriffmafia.app.ui.screens.VoteResultScreen
import com.angelsheriffmafia.engine.GameState
import com.angelsheriffmafia.engine.Phase
import com.angelsheriffmafia.engine.VoteMode

@Composable
fun App(vm: GameViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val game = ui.game
    if (ui.inGame && game != null) {
        GameScreens(ui, game, vm)
        return
    }
    when (ui.menu) {
        MenuScreen.TITLE -> TitleScreen(
            canContinue = game != null && game.phase !is Phase.GameOver,
            onContinue = vm::continueGame,
            onNewGame = { vm.showMenu(MenuScreen.SETUP) },
            onHowToPlay = { vm.showMenu(MenuScreen.HOW_TO_PLAY) },
        )
        MenuScreen.SETUP -> {
            BackHandler { vm.showMenu(MenuScreen.TITLE) }
            SetupScreen(
                setup = ui.setup,
                error = ui.error,
                hasSavedGame = game != null && game.phase !is Phase.GameOver,
                onChange = vm::updateSetup,
                onPreview = vm::previewName,
                onRules = { vm.showMenu(MenuScreen.RULES) },
                onBack = { vm.showMenu(MenuScreen.TITLE) },
                onDeal = vm::startGame,
            )
        }
        MenuScreen.RULES -> {
            BackHandler { vm.showMenu(MenuScreen.SETUP) }
            RulesScreen(
                setup = ui.setup,
                onChange = vm::updateSetup,
                onDone = { vm.showMenu(MenuScreen.SETUP) },
            )
        }
        MenuScreen.HOW_TO_PLAY -> {
            BackHandler { vm.showMenu(MenuScreen.TITLE) }
            HowToPlayScreen(onBack = { vm.showMenu(MenuScreen.TITLE) })
        }
    }
}

@Composable
private fun GameScreens(ui: UiState, game: GameState, vm: GameViewModel) {
    // Back pauses to the title screen. The game is saved and can be continued.
    BackHandler { vm.leaveGame() }

    ui.after?.let { after ->
        val next = game.playerOnTurn?.takeIf { !after.lastTurn }?.let { game.player(it).name }
        AfterTurnScreen(after = after, nextPlayer = next, onDone = vm::finishAfter)
        return
    }

    when (val phase = game.phase) {
        is Phase.RoleReveal -> {
            val player = game.players[phase.index]
            if (!ui.turnOpen) {
                PassScreen(
                    ribbon = "Dealing roles · ${phase.index + 1}/${game.players.size}",
                    turn = phase.index,
                    total = game.players.size,
                    name = player.name,
                    onOpen = vm::openTurn,
                    onPause = vm::leaveGame,
                )
            } else {
                val next = game.players.getOrNull(phase.index + 1)?.name
                RoleCardScreen(game = game, player = player, nextPlayer = next, onDone = vm::roleSeen)
            }
        }

        is Phase.Night -> {
            val night = game.nights.last()
            val by = night.turnOrder[phase.turn]
            if (!ui.turnOpen) {
                PassScreen(
                    ribbon = "Night ${phase.night} · turn ${phase.turn + 1}/${night.turnOrder.size}",
                    turn = phase.turn,
                    total = night.turnOrder.size,
                    name = game.player(by).name,
                    onOpen = vm::openTurn,
                    onPause = vm::leaveGame,
                )
            } else {
                NightPickScreen(game = game, by = by, openedAt = ui.turnOpenedAt, onConfirm = vm::nightPick)
            }
        }

        is Phase.Dawn -> DawnScreen(game = game, onContinue = vm::dawnDone, onReplay = vm::replayDawn)

        is Phase.Discussion -> DiscussionScreen(
            game = game,
            phase = phase,
            onStart = vm::startDiscussion,
            onVote = vm::startVote,
            onTimeUp = vm::discussionTimeUp,
        )

        is Phase.Voting -> if (game.rules.voteMode == VoteMode.OPEN) {
            OpenVoteScreen(game = game, phase = phase, onResult = vm::openVote)
        } else {
            val voter = game.playerOnTurn!!
            if (!ui.turnOpen) {
                val voters = game.secretVoters()
                PassScreen(
                    ribbon = (if (phase.isRevote) "Revote" else "Secret vote") + " · ${phase.turn + 1}/${voters.size}",
                    turn = phase.turn,
                    total = voters.size,
                    name = game.player(voter).name,
                    onOpen = vm::openTurn,
                    onPause = vm::leaveGame,
                )
            } else {
                SecretVoteScreen(game = game, phase = phase, voter = voter, onVote = vm::secretVote)
            }
        }

        is Phase.VoteResult -> VoteResultScreen(game = game, onContinue = vm::advance)

        is Phase.GameOver -> GameOverScreen(game = game, winner = phase.winner, onPlayAgain = vm::playAgain, onDone = vm::endGame)
    }
}
