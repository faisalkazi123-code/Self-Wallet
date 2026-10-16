@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.selfwallet.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfwallet.app.data.db.SpendKind
import com.selfwallet.app.data.db.TransactionEntity
import com.selfwallet.app.data.db.TxSource
import com.selfwallet.app.data.db.TxType
import com.selfwallet.app.ui.WalletState
import com.selfwallet.app.ui.WalletViewModel
import com.selfwallet.app.ui.components.CategoryIcons
import com.selfwallet.app.ui.components.asColor
import com.selfwallet.app.ui.theme.AmountStyle
import com.selfwallet.app.ui.theme.CorporateBlue
import com.selfwallet.app.util.Currencies
import com.selfwallet.app.util.Dates
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private val amountRegex = Regex("^\\d{0,12}([.,]\\d{0,3})?$")

@Composable
fun EditTransactionScreen(
    vm: WalletViewModel,
    state: WalletState,
    id: Long,
    initialType: String?,
    onDone: () -> Unit,
    onManage: (String) -> Unit,
) {
    var original by remember { mutableStateOf<TransactionEntity?>(null) }
    var loaded by rememberSaveable { mutableStateOf(id == 0L) }

    var type by rememberSaveable { mutableStateOf(initialType ?: TxType.EXPENSE) }
    var amount by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf(state.base) }
    var merchant by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableLongStateOf(0L) }
    var allocationId by rememberSaveable { mutableLongStateOf(state.settings.defaultAllocationId) }
    var timestamp by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var learn by rememberSaveable { mutableStateOf(true) }

    var showCurrency by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }

    LaunchedEffect(id) {
        if (id != 0L) {
            val tx = vm.loadTransaction(id)
            original = tx
            if (tx != null && !loaded) {
                type = tx.type
                amount = formatPlain(tx.amount, tx.currency)
                currency = tx.currency
                merchant = tx.merchant
                note = tx.note
                categoryId = tx.categoryId ?: 0L
                allocationId = tx.allocationId ?: 0L
                timestamp = tx.timestamp
                learn = tx.needsReview
                loaded = true
            }
        } else {
            runCatching { amountFocus.requestFocus() }
        }
    }

    val parsedAmount = amount.replace(',', '.').toDoubleOrNull() ?: 0.0
    val canSave = parsedAmount > 0.0
    val fromSms = original?.source == TxSource.SMS
    val categories = state.categories.filter { it.type == type }

    fun save() {
        val base = original ?: TransactionEntity(amount = 0.0, currency = currency)
        val tx = base.copy(
            amount = parsedAmount,
            currency = currency,
            type = type,
            categoryId = categoryId.takeIf { it != 0L },
            allocationId = allocationId.takeIf { it != 0L },
            merchant = merchant.trim(),
            note = note.trim(),
            timestamp = timestamp,
        )
        val keyword = if (fromSms && learn && merchant.isNotBlank()) merchant.trim() else null
        vm.saveTransaction(tx, keyword)
        onDone()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Filled.Close, "Close") } },
                title = { Text(if (id == 0L) "New transaction" else "Edit transaction") },
                actions = {
                    if (original != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = { save() },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(16.dp)
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Filled.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (id == 0L) "Save" else "Save changes", fontSize = 16.sp)
                }
            }
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState()),
        ) {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                listOf(TxType.EXPENSE to "Expense", TxType.INCOME to "Income").forEachIndexed { i, (t, label) ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = {
                            if (type != t) {
                                type = t
                                categoryId = 0L
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }

            // ---- amount ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    onClick = { showCurrency = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$currency · ${Currencies.info(currency).name}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Icon(Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = amount,
                    onValueChange = { if (amountRegex.matches(it)) amount = it },
                    placeholder = {
                        Text("0", style = AmountStyle, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    },
                    prefix = { Text(Currencies.info(currency).symbol + " ", style = AmountStyle.copy(fontSize = 28.sp), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    textStyle = AmountStyle.copy(textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .focusRequester(amountFocus),
                )
                if (currency != state.base && parsedAmount > 0) {
                    val rate = state.rateOf(currency)
                    val known = state.rates.any { it.currency == currency }
                    Text(
                        if (known) "≈ ${Currencies.format(parsedAmount * rate, state.base)}" else "No $currency → ${state.base} rate yet (Settings › Exchange rates)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- merchant + date ----
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text(if (type == TxType.EXPENSE) "Paid to" else "Received from") },
                    leadingIcon = { Icon(Icons.Filled.Storefront, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                )
                Surface(
                    onClick = { showDate = true },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text(Dates.full(timestamp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            // ---- category ----
            PickerHeader("Category", onManage = { onManage("categories") })
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                categories.forEach { c ->
                    val selected = categoryId == c.id
                    val color = c.color.asColor()
                    FilterChip(
                        selected = selected,
                        onClick = { categoryId = if (selected) 0L else c.id },
                        label = { Text(c.name) },
                        leadingIcon = { Icon(CategoryIcons.of(c.icon), null, tint = if (selected) Color.White else color, modifier = Modifier.size(18.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }

            // ---- allocation ----
            PickerHeader(if (type == TxType.EXPENSE) "Expense for" else "Belongs to", onManage = { onManage("allocations") })
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.allocations.forEach { a ->
                    val selected = allocationId == a.id
                    val corporate = a.kind == SpendKind.CORPORATE
                    FilterChip(
                        selected = selected,
                        onClick = { allocationId = if (selected) 0L else a.id },
                        label = { Text(a.name) },
                        leadingIcon = {
                            Icon(
                                if (corporate) Icons.Filled.Business else Icons.Filled.Person, null,
                                modifier = Modifier.size(18.dp),
                                tint = if (selected) Color.White else a.color.asColor(),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = a.color.asColor(),
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
            val selectedAlloc = state.allocations.firstOrNull { it.id == allocationId }
            if (selectedAlloc != null) {
                Text(
                    if (selectedAlloc.kind == SpendKind.CORPORATE) "Corporate expense · shows in company reports and CSV export"
                    else "Personal expense",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selectedAlloc.kind == SpendKind.CORPORATE) CorporateBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            // ---- note ----
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.medium,
                minLines = 2,
            )

            // ---- SMS origin ----
            val o = original
            if (o != null && o.source == TxSource.SMS) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Sms, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "From ${o.smsSender ?: "SMS"}" + (o.account?.let { " · a/c ••$it" } ?: ""),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(o.smsBody.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (merchant.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { learn = !learn }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Remember for “${merchant.trim()}”", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        "Future SMS from this merchant get the same category and destination",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(checked = learn, onCheckedChange = { learn = it })
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showCurrency) {
        CurrencySheet(
            selected = currency,
            recent = (listOf(state.base) + state.items.map { it.tx.currency } + state.rates.map { it.currency }).distinct().take(6),
            onPick = { currency = it; showCurrency = false },
            onDismiss = { showCurrency = false },
        )
    }

    if (showDate) {
        val zone = Dates.zone
        val current = Instant.ofEpochMilli(timestamp).atZone(zone)
        // DatePicker works in UTC midnight millis.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = current.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { utc ->
                        val date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        val time = if (id == 0L && date == LocalDate.now(zone)) LocalTime.now(zone) else current.toLocalTime()
                        timestamp = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete transaction?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    original?.let { vm.deleteTransaction(it) }
                    confirmDelete = false
                    onDone()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PickerHeader(title: String, onManage: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onManage) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Manage")
        }
    }
}

@Composable
fun CurrencySheet(selected: String, recent: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var q by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Currency", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                placeholder = { Text("Search code or name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            )
            if (q.isBlank() && recent.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { code ->
                        FilterChip(selected = code == selected, onClick = { onPick(code) }, label = { Text(code) })
                    }
                }
            }
        }
        val list = Currencies.all.filter {
            q.isBlank() || it.code.contains(q.trim(), true) || it.name.contains(q.trim(), true)
        }
        val custom = q.trim().uppercase().takeIf { it.length == 3 && it.all(Char::isLetter) && list.none { c -> c.code == it } }
        LazyColumn(Modifier.height(420.dp)) {
            if (custom != null) {
                item {
                    ListItem(
                        headlineContent = { Text("Use $custom") },
                        supportingContent = { Text("Custom currency code") },
                        modifier = Modifier.clickable { onPick(custom) },
                    )
                }
            }
            items(list, key = { it.code }) { c ->
                ListItem(
                    leadingContent = {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(c.symbol.take(3), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    },
                    headlineContent = { Text(c.code, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(c.name) },
                    trailingContent = { if (c.code == selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { onPick(c.code) },
                )
            }
        }
    }
}

private fun formatPlain(amount: Double, currency: String): String {
    val d = Currencies.info(currency).decimals
    val s = String.format(java.util.Locale.US, "%.${d}f", amount)
    return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
}
