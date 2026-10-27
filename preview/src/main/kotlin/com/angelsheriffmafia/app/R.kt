package com.angelsheriffmafia.app

/** Stand-in for Android's generated R class. Ids map to files under app/src/main/res. */
@Suppress("ClassName")
object R {
    object drawable {
        const val art_angel = 1
        const val art_desert = 2
        const val art_grave = 3
        const val art_outlaw = 4
        const val art_rooster = 5
        const val art_saloon = 6
        const val art_sheriff = 7
        const val art_town_night = 8
        const val art_villager = 9
    }

    object font {
        const val rye_regular = 101
        const val lilita_one = 102
        const val hind_regular = 103
        const val hind_medium = 104
        const val hind_semibold = 105
        const val hind_bold = 106
    }

    val paths = mapOf(
        drawable.art_angel to "drawable-nodpi/art_angel.png",
        drawable.art_desert to "drawable-nodpi/art_desert.png",
        drawable.art_grave to "drawable-nodpi/art_grave.png",
        drawable.art_outlaw to "drawable-nodpi/art_outlaw.png",
        drawable.art_rooster to "drawable-nodpi/art_rooster.png",
        drawable.art_saloon to "drawable-nodpi/art_saloon.png",
        drawable.art_sheriff to "drawable-nodpi/art_sheriff.png",
        drawable.art_town_night to "drawable-nodpi/art_town_night.png",
        drawable.art_villager to "drawable-nodpi/art_villager.png",
        font.rye_regular to "font/rye_regular.ttf",
        font.lilita_one to "font/lilita_one.ttf",
        font.hind_regular to "font/hind_regular.ttf",
        font.hind_medium to "font/hind_medium.ttf",
        font.hind_semibold to "font/hind_semibold.ttf",
        font.hind_bold to "font/hind_bold.ttf",
    )

    fun bytes(id: Int): ByteArray = java.io.File("app/src/main/res", paths.getValue(id)).readBytes()
}
