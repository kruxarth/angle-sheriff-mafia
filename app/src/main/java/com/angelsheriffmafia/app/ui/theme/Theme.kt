package com.angelsheriffmafia.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.angelsheriffmafia.app.R
import com.angelsheriffmafia.engine.Role

/** The Wild West cartoon palette from the visual design. */
object Western {
    val Ink = Color(0xFF1D1410)
    val Cream = Color(0xFFFFF1C1)
    val Mustard = Color(0xFFF6B31B)
    val MustardLight = Color(0xFFFAC94E)
    val Orange = Color(0xFFE8891A)
    val Dirt = Color(0xFFD9761A)
    val Teal = Color(0xFF3CC7BD)
    val Red = Color(0xFFE2492C)
    val Stamp = Color(0xFFC43B26)
    val Magenta = Color(0xFFB01A5C)
    val NightTop = Color(0xFF121034)
    val Night = Color(0xFF1C1A45)
    val NightBottom = Color(0xFF2B2A5C)
    val NightDim = Color(0xFF34316B)
    val Lavender = Color(0xFFCDC8F2)
    val LavenderDim = Color(0xFFA4A0D6)
    val Wood = Color(0xFF8A5228)
    val WoodDark = Color(0xFF3B1D0C)
    val WoodDeep = Color(0xFF2A160A)
    val Rivet = Color(0xFF3A2418)
    val Paper = Color(0xFFF4E4BC)
    val PaperShade = Color(0xFFEFD9A7)
    val PaperText = Color(0xFF6B4A2A)
}

val Rye = FontFamily(Font(R.font.rye_regular))
val Lilita = FontFamily(Font(R.font.lilita_one))
val Hind = FontFamily(
    Font(R.font.hind_regular, FontWeight.Normal),
    Font(R.font.hind_medium, FontWeight.Medium),
    Font(R.font.hind_semibold, FontWeight.SemiBold),
    Font(R.font.hind_bold, FontWeight.Bold),
)

object Type {
    val poster = TextStyle(fontFamily = Rye, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = 0.04.em)
    val button = TextStyle(fontFamily = Rye, fontSize = 21.sp, letterSpacing = 0.05.em)
    val plank = TextStyle(fontFamily = Rye, fontSize = 19.sp, letterSpacing = 0.04.em)
    val headline = TextStyle(fontFamily = Lilita, fontSize = 34.sp, lineHeight = 37.sp)
    val title = TextStyle(fontFamily = Lilita, fontSize = 24.sp, lineHeight = 27.sp)
    val body = TextStyle(fontFamily = Hind, fontWeight = FontWeight.Medium, fontSize = 16.5.sp, lineHeight = 23.sp)
    val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
    val label = TextStyle(fontFamily = Hind, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.16.em)
}

fun Role.color(): Color = when (this) {
    Role.MAFIA -> Western.Red
    Role.ANGEL -> Western.Mustard
    Role.SHERIFF -> Western.Teal
    Role.VILLAGER -> Western.Orange
}

fun Role.title(): String = when (this) {
    Role.MAFIA -> "Mafia"
    Role.ANGEL -> "Angel"
    Role.SHERIFF -> "Sheriff"
    Role.VILLAGER -> "Villager"
}

fun Role.art(): Int = when (this) {
    Role.MAFIA -> R.drawable.art_outlaw
    Role.ANGEL -> R.drawable.art_angel
    Role.SHERIFF -> R.drawable.art_sheriff
    Role.VILLAGER -> R.drawable.art_villager
}

@Composable
fun WesternTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Western.Mustard,
            onPrimary = Western.Ink,
            secondary = Western.Teal,
            background = Western.Night,
            surface = Western.Night,
            onBackground = Western.Cream,
            onSurface = Western.Cream,
            error = Western.Red,
        ),
        typography = Typography(bodyLarge = Type.body, bodyMedium = Type.body),
        content = content,
    )
}
