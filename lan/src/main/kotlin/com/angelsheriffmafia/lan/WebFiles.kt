package com.angelsheriffmafia.lan

/**
 * The browser page's files and where they live in the app: the page itself under
 * `assets/web/`, and the fonts and pictures shared with the app's own screens under `res/`.
 */
object WebFiles {
    /** URL path to a file under the app's `assets/web/` folder. */
    val pages = mapOf(
        "/" to "index.html",
        "/index.html" to "index.html",
        "/app.css" to "app.css",
        "/app.js" to "app.js",
    )

    /** URL path to an app resource, as "type/name" (the file under `res/` without its folder qualifier). */
    val resources = mapOf(
        "/fonts/rye.ttf" to "font/rye_regular",
        "/fonts/lilita.ttf" to "font/lilita_one",
        "/fonts/hind-500.ttf" to "font/hind_medium",
        "/fonts/hind-600.ttf" to "font/hind_semibold",
        "/fonts/hind-700.ttf" to "font/hind_bold",
        "/art/angel.png" to "drawable/art_angel",
        "/art/desert.png" to "drawable/art_desert",
        "/art/grave.png" to "drawable/art_grave",
        "/art/outlaw.png" to "drawable/art_outlaw",
        "/art/rooster.png" to "drawable/art_rooster",
        "/art/saloon.png" to "drawable/art_saloon",
        "/art/sheriff.png" to "drawable/art_sheriff",
        "/art/town-night.png" to "drawable/art_town_night",
        "/art/villager.png" to "drawable/art_villager",
    )
}
