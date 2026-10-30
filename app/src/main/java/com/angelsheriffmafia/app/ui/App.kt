package com.angelsheriffmafia.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.angelsheriffmafia.app.GameViewModel
import com.angelsheriffmafia.app.MenuScreen
import com.angelsheriffmafia.app.TablePage
import com.angelsheriffmafia.app.TableViewModel
import com.angelsheriffmafia.app.TablesUi
import com.angelsheriffmafia.app.UiState
import com.angelsheriffmafia.app.data.Setup
import com.angelsheriffmafia.app.ui.screens.AfterTurnScreen
import com.angelsheriffmafia.app.ui.screens.ConnectingScreen
import com.angelsheriffmafia.app.ui.screens.DawnScreen
import com.angelsheriffmafia.app.ui.screens.DiscussionScreen
import com.angelsheriffmafia.app.ui.screens.GameOverScreen
import com.angelsheriffmafia.app.ui.screens.HostStartScreen
import com.angelsheriffmafia.app.ui.screens.HowToPlayScreen
import com.angelsheriffmafia.app.ui.screens.JoinScreen
import com.angelsheriffmafia.app.ui.screens.NightPickScreen
import com.angelsheriffmafia.app.ui.screens.OpenVoteScreen
import com.angelsheriffmafia.app.ui.screens.PassScreen
import com.angelsheriffmafia.app.ui.screens.PlayModeScreen
import com.angelsheriffmafia.app.ui.screens.RoleCardScreen
import com.angelsheriffmafia.app.ui.screens.RulesScreen
import com.angelsheriffmafia.app.ui.screens.SecretVoteScreen
import com.angelsheriffmafia.app.ui.screens.SetupScreen
import com.angelsheriffmafia.app.ui.screens.TableScreen
import com.angelsheriffmafia.app.ui.screens.TitleScreen
import com.angelsheriffmafia.app.ui.screens.VoteResultScreen
import com.angelsheriffmafia.engine.GameState
import com.angelsheriffmafia.engine.Phase
import com.angelsheriffmafia.engine.VoteMode
import com.angelsheriffmafia.lan.Stage

@Composable
fun App(vm: GameViewModel, tables: TableViewModel, onDarkScreen: (Boolean) -> Unit = {}) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val t by tables.ui.collectAsStateWithLifecycle()
    if (t.page != TablePage.NONE) {
        TablePages(t, ui.setup, vm, tables, onDarkScreen)
        return
    }
    val game = ui.game
    // Night and private screens are dark; day and menu screens are light. Status bar icons follow.
    val dark = ui.inGame && game != null && (
        ui.after != null ||
            game.phase is Phase.RoleReveal ||
            game.phase is Phase.Night ||
            (game.phase is Phase.Voting && game.rules.voteMode == VoteMode.SECRET)
        )
    SideEffect { onDarkScreen(dark) }
    if (ui.inGame && game != null) {
        GameScreens(ui, game, vm)
        return
    }
    when (ui.menu) {
        MenuScreen.TITLE -> TitleScreen(
            canContinue = game != null && game.phase !is Phase.GameOver,
            onContinue = vm::continueGame,
            onNewGame = { tables.showPage(TablePage.MODE) },
            onHowToPlay = { vm.showMenu(MenuScreen.HOW_TO_PLAY) },
            onJoin = { tables.showJoin() },
            atTable = t.atTable,
            onTable = { tables.backToTable(ui.setup.narratorOn) },
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

/** Hosting a table on this phone, or joining one on another, with everyone on their own phone. */
@Composable
private fun TablePages(t: TablesUi, setup: Setup, vm: GameViewModel, tables: TableViewModel, onDarkScreen: (Boolean) -> Unit) {
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val table = t.table
    val game = table?.view?.game
    val me = game?.me
    // Secret screens are dark, like the pass-around ones.
    val dark = t.page == TablePage.TABLE && game != null && (
        (game.stage == Stage.DEAL && me != null) ||
            game.stage == Stage.NIGHT ||
            (game.stage == Stage.VOTE && game.vote?.canVote == true)
        )
    SideEffect { onDarkScreen(dark) }
    when (t.page) {
        TablePage.NONE -> Unit
        TablePage.MODE -> {
            BackHandler { tables.showPage(TablePage.NONE) }
            PlayModeScreen(
                onPassAround = {
                    tables.showPage(TablePage.NONE)
                    vm.showMenu(MenuScreen.SETUP)
                },
                onHost = { tables.showPage(if (t.hosting) TablePage.TABLE else TablePage.HOST_START) },
                onBack = { tables.showPage(TablePage.NONE) },
            )
        }
        TablePage.HOST_START -> {
            val back = { tables.showPage(if (t.hosting) TablePage.TABLE else TablePage.MODE) }
            BackHandler(onBack = back)
            HostStartScreen(
                name = setup.myName,
                pronunciation = setup.myPronunciation,
                plays = setup.hostPlays,
                tableOpen = t.hosting,
                error = t.hostError,
                onName = { vm.updateSetup(setup.copy(myName = it)) },
                onPronunciation = { vm.updateSetup(setup.copy(myPronunciation = it)) },
                onPlays = { vm.updateSetup(setup.copy(hostPlays = it)) },
                onPreview = vm::previewName,
                onOpen = {
                    // The "hosting" notification needs permission from Android 13 on. The table works without it.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    tables.openTable(setup.myName, setup.myPronunciation, setup.hostPlays, setup.rules, setup.narratorOn)
                },
                onBack = back,
            )
        }
        TablePage.HOST_RULES -> {
            BackHandler { tables.showPage(TablePage.TABLE) }
            RulesScreen(
                setup = setup,
                onChange = {
                    vm.updateSetup(it)
                    tables.setRules(it.rules)
                    tables.setNarrator(it.narratorOn)
                },
                onDone = { tables.showPage(TablePage.TABLE) },
                ownPhones = true,
                players = table?.view?.seats?.size ?: 0,
            )
        }
        TablePage.JOIN -> {
            BackHandler { tables.showPage(TablePage.NONE) }
            JoinScreen(
                ui = t.join,
                onAddress = tables::setJoinAddress,
                onName = tables::setJoinName,
                onPronunciation = tables::setJoinPronunciation,
                onJoin = {
                    vm.updateSetup(setup.copy(myName = t.join.name.trim(), myPronunciation = t.join.pronunciation.trim()))
                    tables.join()
                },
                onBack = { tables.showPage(TablePage.NONE) },
            )
        }
        TablePage.TABLE -> {
            BackHandler { tables.actions.pause() }
            if (table == null) {
                ConnectingScreen(onBack = { tables.showPage(TablePage.NONE) })
            } else {
                TableScreen(table, tables.actions)
            }
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

        // Only games on everyone's own phone deal this way, and those run on the table screens.
        is Phase.Deal -> Unit
    }
}
