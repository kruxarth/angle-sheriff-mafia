package com.angelsheriffmafia.app.lan

import android.content.Context
import com.angelsheriffmafia.app.R
import com.angelsheriffmafia.lan.LanServer
import com.angelsheriffmafia.lan.StaticFile
import com.angelsheriffmafia.lan.StaticFiles
import com.angelsheriffmafia.lan.WebFiles
import java.util.concurrent.ConcurrentHashMap

/** The browser page from the app's assets, and its fonts and pictures from the app's own resources. */
class AndroidStaticFiles(context: Context) : StaticFiles {
    private val app = context.applicationContext
    private val cache = ConcurrentHashMap<String, StaticFile>()

    // Listed by hand, not looked up by name, so resource shrinking can see they're used.
    private val resourceIds = mapOf(
        "font/rye_regular" to R.font.rye_regular,
        "font/lilita_one" to R.font.lilita_one,
        "font/hind_medium" to R.font.hind_medium,
        "font/hind_semibold" to R.font.hind_semibold,
        "font/hind_bold" to R.font.hind_bold,
        "drawable/art_angel" to R.drawable.art_angel,
        "drawable/art_desert" to R.drawable.art_desert,
        "drawable/art_grave" to R.drawable.art_grave,
        "drawable/art_outlaw" to R.drawable.art_outlaw,
        "drawable/art_rooster" to R.drawable.art_rooster,
        "drawable/art_saloon" to R.drawable.art_saloon,
        "drawable/art_sheriff" to R.drawable.art_sheriff,
        "drawable/art_town_night" to R.drawable.art_town_night,
        "drawable/art_villager" to R.drawable.art_villager,
    )

    override fun open(path: String): StaticFile? {
        cache[path]?.let { return it }
        val file = page(path) ?: resource(path) ?: return null
        cache[path] = file
        return file
    }

    private fun page(path: String): StaticFile? {
        val name = WebFiles.pages[path] ?: return null
        val bytes = runCatching { app.assets.open("web/$name").use { it.readBytes() } }.getOrNull() ?: return null
        return StaticFile(bytes, LanServer.contentType(name))
    }

    private fun resource(path: String): StaticFile? {
        val id = WebFiles.resources[path]?.let(resourceIds::get) ?: return null
        val bytes = runCatching { app.resources.openRawResource(id).use { it.readBytes() } }.getOrNull() ?: return null
        return StaticFile(bytes, LanServer.contentType(path))
    }
}
