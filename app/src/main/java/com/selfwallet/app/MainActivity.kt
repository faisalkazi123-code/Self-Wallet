package com.selfwallet.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfwallet.app.sms.Notifications
import com.selfwallet.app.ui.AppRoot
import com.selfwallet.app.ui.WalletViewModel
import com.selfwallet.app.ui.theme.SelfWalletTheme

class MainActivity : ComponentActivity() {
    private val vm: WalletViewModel by viewModels()

    /** Transaction to open, set when the user taps an "auto-added" notification. */
    private val openTxId = mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        readIntent(intent)
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            SelfWalletTheme(themeMode = state.settings.themeMode, dynamicColor = state.settings.dynamicColor) {
                AppRoot(
                    vm = vm,
                    state = state,
                    openTxId = openTxId.longValue,
                    onOpenTxConsumed = { openTxId.longValue = 0L },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        val id = intent?.getLongExtra(Notifications.EXTRA_TX_ID, 0L) ?: 0L
        if (id != 0L) openTxId.longValue = id
    }
}
