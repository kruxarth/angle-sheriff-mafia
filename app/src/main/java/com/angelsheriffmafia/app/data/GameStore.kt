package com.angelsheriffmafia.app.data

import android.content.Context
import android.util.AtomicFile
import com.angelsheriffmafia.engine.GameState
import java.io.File

/** Saves the game in progress after every move, so locking the phone or closing the app loses nothing. */
class GameStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "game.json"))

    fun load(): GameState? = runCatching { GameState.fromJson(file.readFully().decodeToString()) }.getOrNull()

    fun save(state: GameState?) {
        if (state == null) {
            file.delete()
            return
        }
        val out = file.startWrite()
        try {
            out.write(state.toJson().encodeToByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }
}
