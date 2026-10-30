package com.angelsheriffmafia.app.lan

import android.content.Context
import android.util.AtomicFile
import com.angelsheriffmafia.lan.LanJson
import com.angelsheriffmafia.lan.TableState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.io.File

/** One small JSON file, replaced whole on every save so a crash never leaves half of it. */
private class JsonFile<T>(context: Context, name: String, private val serializer: KSerializer<T>) {
    private val file = AtomicFile(File(context.filesDir, name))

    fun load(): T? = runCatching { LanJson.decodeFromString(serializer, file.readFully().decodeToString()) }.getOrNull()

    fun save(value: T?) {
        if (value == null) {
            file.delete()
            return
        }
        val out = file.startWrite()
        try {
            out.write(LanJson.encodeToString(serializer, value).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }
}

/** The table this phone hosts, so a game survives the app being closed or the phone restarting. */
class TableStore(context: Context) {
    private val json = JsonFile(context, "table.json", TableState.serializer())

    fun load(): TableState? = json.load()

    fun save(table: TableState?) = json.save(table)
}

/** A seat this phone holds at another phone's table. */
@Serializable
data class JoinedSeat(
    /** Like "http://192.168.43.1:8765". */
    val base: String,
    val token: String,
    val table: String,
)

class JoinedStore(context: Context) {
    private val json = JsonFile(context, "joined.json", JoinedSeat.serializer())

    fun load(): JoinedSeat? = json.load()

    fun save(seat: JoinedSeat?) = json.save(seat)
}
