@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.selfwallet.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.selfwallet.app.backup.BackupManager
import com.selfwallet.app.ui.WalletState
import com.selfwallet.app.ui.WalletViewModel
import com.selfwallet.app.ui.theme.Ink
import com.selfwallet.app.ui.theme.Teal
import com.selfwallet.app.ui.theme.TealBright
import com.selfwallet.app.util.Currencies

private val popular = listOf("USD", "EUR", "GBP", "INR", "AED", "SAR", "QAR", "KWD", "PKR", "BDT", "SGD", "AUD")

@Composable
fun OnboardingScreen(vm: WalletViewModel, state: WalletState) {
    var step by rememberSaveable { mutableStateOf(0) }
    val context = LocalContext.current
    var smsGranted by remember { mutableStateOf(hasSmsPermission(context)) }
    var showCurrencies by remember { mutableStateOf(false) }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        smsGranted = smsPermissions.all { res[it] == true } || hasSmsPermission(context)
        if (smsGranted) {
            vm.setSmsAutoImport(true)
            vm.importInbox(90)
        }
        step = 3
    }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.linkBackupFile(uri, writeNow = true)
        vm.completeOnboarding()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(4) { i ->
                val w by animateDpAsState(if (i == step) 28.dp else 8.dp, label = "dot")
                Box(
                    Modifier
                        .height(8.dp)
                        .width(w)
                        .clip(CircleShape)
                        .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
            },
            modifier = Modifier.weight(1f),
            label = "step",
        ) { s ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                when (s) {
                    0 -> Welcome()
                    1 -> CurrencyStep(state.base, onPick = { vm.setBaseCurrency(it) }, onMore = { showCurrencies = true })
                    2 -> SmsStep(smsGranted)
                    else -> StorageStep()
                }
            }
        }

        Column(Modifier.padding(24.dp)) {
            when (step) {
                0 -> PrimaryButton("Get started") { step = 1 }
                1 -> PrimaryButton("Continue with ${state.base}") { step = 2 }
                2 -> {
                    if (smsGranted) {
                        PrimaryButton("Continue") { step = 3 }
                    } else {
                        PrimaryButton("Allow SMS access") {
                            val notif = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
                            smsLauncher.launch(smsPermissions + notif)
                        }
                        TextButton(onClick = { step = 3 }, modifier = Modifier.fillMaxWidth()) { Text("Skip — I'll add expenses manually") }
                    }
                }
                else -> {
                    PrimaryButton("Keep data on this phone") { vm.completeOnboarding() }
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        onClick = { createBackup.launch(BackupManager.defaultFileName()) },
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Cloud, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Also back up to Google Drive / OneDrive", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }

    if (showCurrencies) {
        CurrencySheet(
            selected = state.base,
            recent = emptyList(),
            onPick = { vm.setBaseCurrency(it); showCurrencies = false },
            onDismiss = { showCurrencies = false },
        )
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = MaterialTheme.shapes.large,
    ) { Text(text, style = MaterialTheme.typography.titleSmall) }
}

@Composable
private fun ColumnScope.Welcome() {
    Spacer(Modifier.height(24.dp))
    Box(
        Modifier
            .size(96.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(TealBright, Teal, Ink)))
            .align(Alignment.CenterHorizontally),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(48.dp))
    }
    Spacer(Modifier.height(28.dp))
    Text(
        "Your spending,\ntracked from your SMS",
        style = MaterialTheme.typography.headlineLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "Self Wallet reads the bank and card messages you already get and logs every expense for you.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(32.dp))
    Feature(Icons.Filled.Sms, "Automatic", "Debit, credit and card SMS become transactions instantly.")
    Feature(Icons.Filled.Business, "Personal or corporate", "Send each expense to yourself, family, your company or a client.")
    Feature(Icons.Filled.CurrencyExchange, "Any currency", "Spend in AED, pay in INR, report in USD — all converted.")
    Feature(Icons.Filled.Shield, "100% private & free", "No account, no ads, no internet. Your data stays on your phone.")
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(44.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StepHeader(icon: ImageVector, title: String, body: String) {
    Spacer(Modifier.height(16.dp))
    Box(
        Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp)) }
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun CurrencyStep(selected: String, onPick: (String) -> Unit, onMore: () -> Unit) {
    StepHeader(
        Icons.Filled.CurrencyExchange,
        "Your main currency",
        "Totals and reports are shown in this currency. Transactions keep their original currency and are converted using rates you control.",
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (popular + listOf(selected)).distinct().forEach { code ->
            val sel = code == selected
            Surface(
                onClick = { onPick(code) },
                shape = MaterialTheme.shapes.medium,
                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
                border = if (sel) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        code,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        Currencies.info(code).symbol,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (sel) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    TextButton(onClick = onMore) { Text("More currencies…") }
}

@Composable
private fun SmsStep(granted: Boolean) {
    StepHeader(
        Icons.Filled.Sms,
        "Capture expenses from SMS",
        "When your bank texts you about a payment, Self Wallet adds it automatically — amount, currency, merchant and card.",
    )
    Bullet("Only bank-style messages with an amount are used. OTPs and promotions are ignored.")
    Bullet("Messages are processed on this phone and never uploaded anywhere.")
    Bullet("We'll also import the last 3 months of bank SMS so you start with history.")
    if (granted) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("SMS access is on", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
    } else {
        Spacer(Modifier.height(16.dp))
        Text(
            "Tip: if Android says the setting is restricted, open App info › ⋮ › “Allow restricted settings” and try again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StorageStep() {
    StepHeader(
        Icons.Filled.PhoneAndroid,
        "Where should your data live?",
        "Everything is saved in a private database on this phone. You can also keep an automatic backup file in Google Drive or OneDrive — handy when you change phones.",
    )
    Bullet("No sign-up or cloud account with us — ever.")
    Bullet("Drive/OneDrive backups are uploaded by their own apps; Self Wallet has no internet permission.")
    Bullet("You can change this any time in Settings › Your data.")
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
