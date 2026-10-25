package androidx.compose.ui.text.font

import com.angelsheriffmafia.app.R

/** Desktop version of Android's Font(resId). */
fun Font(resId: Int, weight: FontWeight = FontWeight.Normal, style: FontStyle = FontStyle.Normal): Font =
    androidx.compose.ui.text.platform.Font("res-$resId", R.bytes(resId), weight, style)
