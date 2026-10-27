package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.angelsheriffmafia.app.R

/** Desktop version of Android's painterResource(id). */
@Composable
fun painterResource(id: Int): Painter = remember(id) {
    BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(R.bytes(id)).toComposeImageBitmap())
}
