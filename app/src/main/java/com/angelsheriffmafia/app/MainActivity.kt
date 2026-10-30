package com.angelsheriffmafia.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.angelsheriffmafia.app.ui.App
import com.angelsheriffmafia.app.ui.theme.WesternTheme

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()
    private val tables: TableViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openLink(intent)
        setSystemBars(dark = false)
        // Roles are secret: keep them out of screenshots and the recent-apps preview,
        // and keep the screen awake while the phone goes round.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            WesternTheme { App(viewModel, tables, onDarkScreen = ::setSystemBars) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openLink(intent)
    }

    /** angelsheriffmafia://join?host=192.168.43.1:8765, from the browser page's "open in the app" link. */
    private fun openLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "angelsheriffmafia" || uri.host != "join") return
        uri.getQueryParameter("host")?.takeIf { it.isNotBlank() }?.let(tables::openJoinLink)
    }

    private var darkBars: Boolean? = null

    private fun setSystemBars(dark: Boolean) {
        if (darkBars == dark) return
        darkBars = dark
        val style = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
