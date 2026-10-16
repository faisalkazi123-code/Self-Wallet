package com.selfwallet.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfwallet.app.sms.Notifications
import com.selfwallet.app.ui.AppRoot
import com.selfwallet.app.ui.LockScreen
import com.selfwallet.app.ui.WalletViewModel
import com.selfwallet.app.ui.theme.SelfWalletTheme

// FragmentActivity (a ComponentActivity) is required by BiometricPrompt.
class MainActivity : FragmentActivity() {
    private val vm: WalletViewModel by viewModels()

    /** Transaction to open, set when the user taps an "auto-added" notification. */
    private val openTxId = mutableLongStateOf(0L)

    /** Screen requested by the widget, app shortcut or a notification (Notifications.OPEN_*). */
    private val openTarget = mutableStateOf<String?>(null)

    private val unlocked = mutableStateOf(false)
    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        readIntent(intent)
        unlocked.value = savedInstanceState?.getBoolean(KEY_UNLOCKED) ?: false
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            val s = state.settings
            val locked = state.loaded && s.appLock && !unlocked.value
            LaunchedEffect(s.appLock) {
                // Keep amounts out of the recent-apps preview and screenshots while the lock is on.
                if (s.appLock) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            SelfWalletTheme(themeMode = s.themeMode, dynamicColor = s.dynamicColor, style = s.appStyle) {
                if (locked) {
                    LaunchedEffect(Unit) { authenticate() }
                    LockScreen(onUnlock = ::authenticate)
                } else {
                    AppRoot(
                        vm = vm,
                        state = state,
                        openTxId = openTxId.longValue,
                        onOpenTxConsumed = { openTxId.longValue = 0L },
                        openTarget = openTarget.value,
                        onOpenTargetConsumed = { openTarget.value = null },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, unlocked.value)
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > RELOCK_AFTER_MS) {
            unlocked.value = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        val id = intent?.getLongExtra(Notifications.EXTRA_TX_ID, 0L) ?: 0L
        if (id != 0L) openTxId.longValue = id
        intent?.getStringExtra(Notifications.EXTRA_OPEN)?.let { openTarget.value = it }
        if (intent?.action == ACTION_ADD) openTarget.value = Notifications.OPEN_ADD
    }

    private fun authenticate() {
        if (!canAuthenticate(this)) {
            // No screen lock on the phone any more: don't lock the user out of their data.
            unlocked.value = true
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked.value = true
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Self Wallet")
            .setSubtitle("Use your fingerprint, face or screen lock")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }

    companion object {
        private const val KEY_UNLOCKED = "unlocked"
        const val ACTION_ADD = "com.selfwallet.app.ADD"
        private const val RELOCK_AFTER_MS = 30_000L
        const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

        fun canAuthenticate(context: android.content.Context): Boolean =
            BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
