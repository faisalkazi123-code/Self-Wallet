@file:OptIn(ExperimentalMaterial3Api::class)

package com.selfwallet.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.selfwallet.app.BuildConfig
import com.selfwallet.app.MainActivity
import com.selfwallet.app.backup.BackupFile
import com.selfwallet.app.backup.BackupManager
import com.selfwallet.app.data.db.SpendKind
import com.selfwallet.app.data.prefs.ThemeMode
import com.selfwallet.app.ui.WalletState
import com.selfwallet.app.ui.WalletViewModel
import com.selfwallet.app.ui.theme.AppStyle
import com.selfwallet.app.util.Currencies
import com.selfwallet.app.util.Dates
import kotlinx.coroutines.launch

fun hasSmsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

val smsPermissions = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)

/** Re-reads a permission whenever the screen resumes (the user may have changed it in system settings). */
@Composable
fun rememberPermissionState(check: (Context) -> Boolean): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(check(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = check(context) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return granted to { granted = check(context) }
}

fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

@Composable
fun SettingsScreen(vm: WalletViewModel, state: WalletState, onManage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = state.settings

    val (smsGranted, refreshSms) = rememberPermissionState(::hasSmsPermission)
    val (notifGranted, refreshNotif) = rememberPermissionState(::hasNotificationPermission)
    var smsDeniedOnce by remember { mutableStateOf(false) }

    var showBaseCurrency by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showStyle by remember { mutableStateOf(false) }
    var showName by remember { mutableStateOf(false) }
    var showScan by remember { mutableStateOf(false) }
    var showDefaultAlloc by remember { mutableStateOf(false) }
    var showStorageHelp by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<Pair<Uri, BackupFile>?>(null) }
    var linkCandidate by remember { mutableStateOf<Pair<Uri, BackupFile?>?>(null) }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        refreshSms()
        if (res.values.all { it }) {
            vm.setSmsAutoImport(true)
            showScan = true
        } else {
            smsDeniedOnce = true
        }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshNotif()
        if (it) vm.setSmsNotify(true)
    }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.linkBackupFile(uri, writeNow = true)
    }
    val openExisting = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val file = vm.inspectBackup(uri).getOrNull()
                linkCandidate = uri to file
            }
        }
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                vm.inspectBackup(uri)
                    .onSuccess { pendingRestore = uri to it }
                    .onFailure { showStorageHelp = "That file isn't a Self Wallet backup." }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            // ---------------- SMS ----------------
            Group("SMS auto-capture") {
                if (!smsGranted) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Let Self Wallet read bank SMS", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Card and bank messages are turned into transactions on your phone. Messages never leave the device — the app has no internet access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { smsLauncher.launch(smsPermissions) }) { Text("Allow SMS access") }
                            if (smsDeniedOnce) {
                                OutlinedButton(onClick = { openAppSettings(context) }) { Text("Open settings") }
                            }
                        }
                        if (smsDeniedOnce) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "If the option is greyed out: App info › ⋮ menu › “Allow restricted settings”, then try again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Divider()
                }
                SwitchRow(
                    Icons.Filled.Sms, "Add transactions from SMS",
                    if (smsGranted) "New bank & card messages are added automatically" else "Needs SMS permission",
                    checked = s.smsAutoImport && smsGranted,
                    enabled = smsGranted,
                ) { vm.setSmsAutoImport(it) }
                Divider()
                SwitchRow(
                    Icons.Filled.Notifications, "Notify me", "Show a notification for each auto-added transaction",
                    checked = s.smsNotify && notifGranted,
                ) { on ->
                    if (on && !notifGranted && Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else vm.setSmsNotify(on)
                }
                Divider()
                val defAlloc = state.allocations.firstOrNull { it.id == s.defaultAllocationId }
                NavRow(
                    if (defAlloc?.kind == SpendKind.CORPORATE) Icons.Filled.Business else Icons.Filled.Payments,
                    "Default “Expense for”",
                    defAlloc?.name ?: "Not set",
                ) { showDefaultAlloc = true }
                Divider()
                NavRow(Icons.Filled.Inbox, "Scan existing inbox", "Import past bank SMS", enabled = smsGranted) { showScan = true }
            }

            // ---------------- Organise ----------------
            Group("Organise") {
                NavRow(Icons.Filled.Category, "Categories", "${state.categories.size} categories") { onManage("categories") }
                Divider()
                NavRow(Icons.Filled.Business, "Expense for (personal & corporate)", state.allocations.joinToString { it.name }) { onManage("allocations") }
                Divider()
                NavRow(Icons.Filled.AutoAwesome, "Auto-categorise rules", "${state.rules.size} rule${if (state.rules.size == 1) "" else "s"}") { onManage("rules") }
                Divider()
                NavRow(
                    Icons.Filled.Savings, "Monthly budgets",
                    if (state.budgets.isEmpty()) "Get alerts at 80% and 100%" else "${state.budgets.size} set",
                ) { onManage("budgets") }
                Divider()
                NavRow(Icons.Filled.Badge, "Your name on reports", s.ownerName.ifBlank { "Not set · shown on PDF expense reports" }) { showName = true }
                Divider()
                NavRow(Icons.Filled.CurrencyExchange, "Exchange rates", if (state.missingRates.isEmpty()) "${state.rates.size} set" else "Missing: ${state.missingRates.joinToString()}") { onManage("rates") }
                Divider()
                NavRow(Icons.AutoMirrored.Filled.Label, "Base currency", "${s.baseCurrency} · ${Currencies.info(s.baseCurrency).name}") { showBaseCurrency = true }
            }

            // ---------------- Storage ----------------
            Group("Your data") {
                StorageCard(
                    linkedLabel = s.backupLabel,
                    lastBackupAt = s.lastBackupAt,
                    onPick = { target -> showStorageHelp = target },
                    onUnlink = { vm.unlinkBackupFile() },
                )
                if (s.backupUri != null) {
                    Divider()
                    SwitchRow(Icons.Filled.Sync, "Auto backup", "Save to the linked file after every change and daily", checked = s.autoBackup) {
                        vm.setAutoBackup(it)
                    }
                    Divider()
                    NavRow(Icons.Filled.Backup, "Back up now", s.backupLabel ?: "") { vm.backupNow() }
                }
                Divider()
                NavRow(Icons.Filled.Restore, "Restore from a backup file", "Replaces the data on this phone") {
                    restorePicker.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
                }
            }

            // ---------------- Appearance ----------------
            Group("Appearance & privacy") {
                NavRow(
                    Icons.Filled.Style, "Style",
                    if (s.appStyle == AppStyle.PAPER) "Paper Ledger · warm paper, serif & typewriter numbers" else "Calm Teal · soft cards, rounded & colourful",
                ) { showStyle = true }
                Divider()
                NavRow(
                    Icons.Filled.DarkMode, "Theme",
                    when (s.themeMode) { ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark"; else -> "Follow system" },
                ) { showTheme = true }
                if (Build.VERSION.SDK_INT >= 31) {
                    Divider()
                    SwitchRow(Icons.Filled.Palette, "Wallpaper colours", "Use Material You dynamic colours", checked = s.dynamicColor) { vm.setDynamicColor(it) }
                }
                Divider()
                SwitchRow(Icons.Filled.Lock, "Hide amounts", "Mask balances on screen and in notifications", checked = s.hideAmounts) { vm.setHideAmounts(it) }
                Divider()
                SwitchRow(
                    Icons.Filled.Fingerprint, "App lock",
                    "Ask for fingerprint, face or screen lock when opening the app",
                    checked = s.appLock,
                ) { on ->
                    if (on && !MainActivity.canAuthenticate(context)) {
                        showStorageHelp = "Set up a fingerprint, face unlock or screen lock in your phone's settings first."
                    } else {
                        vm.setAppLock(on)
                    }
                }
            }

            Group("About") {
                NavRow(Icons.Filled.Info, "Self Wallet ${BuildConfig.VERSION_NAME}", "Free & open source · no ads · no tracking · works offline") {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/faisalkazi123-code/Self-Wallet")))
                    }
                }
            }
        }
    }

    // ---------------- dialogs ----------------

    if (showBaseCurrency) {
        CurrencySheet(
            selected = s.baseCurrency,
            recent = (state.items.map { it.tx.currency } + state.rates.map { it.currency }).distinct().take(6),
            onPick = { vm.setBaseCurrency(it); showBaseCurrency = false },
            onDismiss = { showBaseCurrency = false },
        )
    }

    if (showStyle) {
        ChoiceDialog(
            title = "Style",
            options = listOf(AppStyle.TEAL to "Calm Teal", AppStyle.PAPER to "Paper Ledger"),
            selected = s.appStyle,
            onPick = { vm.setAppStyle(it); showStyle = false },
            onDismiss = { showStyle = false },
        )
    }

    if (showName) {
        var name by remember { mutableStateOf(s.ownerName) }
        AlertDialog(
            onDismissRequest = { showName = false },
            title = { Text("Your name") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Name / employee ID") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = { vm.setOwnerName(name); showName = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showName = false }) { Text("Cancel") } },
        )
    }

    if (showTheme) {
        ChoiceDialog(
            title = "Theme",
            options = listOf(ThemeMode.SYSTEM to "Follow system", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"),
            selected = s.themeMode,
            onPick = { vm.setThemeMode(it); showTheme = false },
            onDismiss = { showTheme = false },
        )
    }

    if (showDefaultAlloc) {
        ChoiceDialog(
            title = "SMS expenses go to",
            options = state.allocations.map { it.id.toString() to "${it.name} · ${if (it.kind == SpendKind.CORPORATE) "Corporate" else "Personal"}" },
            selected = s.defaultAllocationId.toString(),
            onPick = { vm.setDefaultAllocation(it.toLong()); showDefaultAlloc = false },
            onDismiss = { showDefaultAlloc = false },
        )
    }

    if (showScan) {
        ScanDialog(onScan = { days -> vm.importInbox(days); showScan = false }, onDismiss = { showScan = false })
    }

    showStorageHelp?.let { target ->
        if (target == "drive" || target == "onedrive" || target == "device") {
            StorageHelpDialog(
                target = target,
                onCreate = { showStorageHelp = null; createBackup.launch(BackupManager.defaultFileName()) },
                onExisting = { showStorageHelp = null; openExisting.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
                onDismiss = { showStorageHelp = null },
            )
        } else {
            AlertDialog(
                onDismissRequest = { showStorageHelp = null },
                title = { Text("Heads up") },
                text = { Text(target) },
                confirmButton = { TextButton(onClick = { showStorageHelp = null }) { Text("OK") } },
            )
        }
    }

    linkCandidate?.let { (uri, file) ->
        AlertDialog(
            onDismissRequest = { linkCandidate = null },
            title = { Text("Use this backup file?") },
            text = {
                Text(
                    if (file != null) {
                        "It contains ${file.transactions.size} transactions saved ${Dates.full(file.exportedAt)}.\n\n" +
                            "Restore them to this phone, or overwrite the file with what's on this phone."
                    } else {
                        "This file is empty or not a Self Wallet backup. It will be overwritten with the data on this phone."
                    },
                )
            },
            confirmButton = {
                if (file != null) {
                    TextButton(onClick = {
                        vm.restore(file)
                        vm.linkBackupFile(uri, writeNow = false)
                        linkCandidate = null
                    }) { Text("Restore & sync") }
                } else {
                    TextButton(onClick = { vm.linkBackupFile(uri, writeNow = true); linkCandidate = null }) { Text("Use file") }
                }
            },
            dismissButton = {
                Row {
                    if (file != null) {
                        TextButton(onClick = { vm.linkBackupFile(uri, writeNow = true); linkCandidate = null }) { Text("Overwrite") }
                    }
                    TextButton(onClick = { linkCandidate = null }) { Text("Cancel") }
                }
            },
        )
    }

    pendingRestore?.let { (_, file) ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            icon = { Icon(Icons.Filled.Restore, null) },
            title = { Text("Restore backup?") },
            text = {
                Text(
                    "${file.transactions.size} transactions, ${file.categories.size} categories and ${file.allocations.size} destinations " +
                        "from ${Dates.full(file.exportedAt)}.\n\nEverything currently on this phone will be replaced.",
                )
            },
            confirmButton = { TextButton(onClick = { vm.restore(file); pendingRestore = null }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StorageCard(linkedLabel: String?, lastBackupAt: Long, onPick: (String) -> Unit, onUnlink: () -> Unit) {
    val current = when {
        linkedLabel == null -> "device-only"
        linkedLabel.startsWith("Google Drive") -> "drive"
        linkedLabel.startsWith("OneDrive") -> "onedrive"
        else -> "device"
    }
    Column(Modifier.padding(16.dp)) {
        Text("Where your data is stored", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Data always lives in a private database on this phone. Optionally keep a backup copy in a file on your phone, Google Drive or OneDrive — synced by their own apps, never by ours.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StorageOption(Icons.Filled.PhoneAndroid, "Phone", current == "device-only" || current == "device", Modifier.weight(1f)) {
                if (current != "device-only") onPick("device")
            }
            StorageOption(Icons.Filled.Cloud, "Google Drive", current == "drive", Modifier.weight(1f), tint = Color(0xFF1A73E8)) { onPick("drive") }
            StorageOption(Icons.Filled.Cloud, "OneDrive", current == "onedrive", Modifier.weight(1f), tint = Color(0xFF0078D4)) { onPick("onedrive") }
        }
        if (linkedLabel != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CloudDone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(linkedLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        if (lastBackupAt > 0) "Last backup ${Dates.full(lastBackupAt)}" else "Not backed up yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onUnlink) { Text("Unlink") }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { onPick("device") }) { Text("Keep a backup file on this phone") }
        }
    }
}

@Composable
private fun StorageOption(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, tint: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = tint)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun StorageHelpDialog(target: String, onCreate: () -> Unit, onExisting: () -> Unit, onDismiss: () -> Unit) {
    val name = when (target) { "drive" -> "Google Drive"; "onedrive" -> "OneDrive"; else -> "this phone" }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (target == "device") Icons.Filled.PhoneAndroid else Icons.Filled.Cloud, null) },
        title = { Text("Back up to $name") },
        text = {
            Text(
                if (target == "device") {
                    "Choose a folder on your phone (for example Documents) and save the backup file there."
                } else {
                    "The system file picker opens next. Tap ☰ (top-left) and pick $name, choose a folder, then Save.\n\n" +
                        "The $name app must be installed and signed in. It uploads the file for you — Self Wallet itself never connects to the internet.\n\n" +
                        "Moving to a new phone? Choose “Use existing file” instead."
                },
            )
        },
        confirmButton = { TextButton(onClick = onCreate) { Text("Create new file") } },
        dismissButton = {
            Row {
                TextButton(onClick = onExisting) { Text("Use existing file") }
            }
        },
    )
}

@Composable
private fun ScanDialog(onScan: (Int) -> Unit, onDismiss: () -> Unit) {
    var days by remember { mutableIntStateOf(90) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Inbox, null) },
        title = { Text("Import past SMS") },
        text = {
            Column {
                Text("Bank messages already in your inbox will be added. Duplicates are skipped automatically.")
                Spacer(Modifier.height(8.dp))
                listOf(30 to "Last 30 days", 90 to "Last 3 months", 365 to "Last 12 months", 3650 to "Everything").forEach { (d, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .selectable(selected = days == d, onClick = { days = d }, role = Role.RadioButton)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = days == d, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onScan(days) }) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
fun ChoiceDialog(title: String, options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (key, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .selectable(selected = key == selected, onClick = { onPick(key) }, role = Role.RadioButton)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = key == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun Group(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp),
    )
    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column { content() }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun RowIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
}

@Composable
fun NavRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}

@Composable
fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
