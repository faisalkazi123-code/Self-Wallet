@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.selfwallet.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.selfwallet.app.data.Budgets
import com.selfwallet.app.data.db.AllocationEntity
import com.selfwallet.app.data.db.BudgetEntity
import com.selfwallet.app.data.db.BillEntity
import com.selfwallet.app.data.db.TripEntity
import com.selfwallet.app.ui.components.money
import com.selfwallet.app.util.Dates
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDatePickerState
import com.selfwallet.app.data.db.CategoryEntity
import com.selfwallet.app.data.db.RateEntity
import com.selfwallet.app.data.db.RuleEntity
import com.selfwallet.app.data.db.SpendKind
import com.selfwallet.app.data.db.TxType
import com.selfwallet.app.ui.WalletState
import com.selfwallet.app.ui.WalletViewModel
import com.selfwallet.app.ui.components.CategoryIcons
import com.selfwallet.app.ui.components.EmptyState
import com.selfwallet.app.ui.components.IconBadge
import com.selfwallet.app.ui.components.Palette
import com.selfwallet.app.ui.components.ProgressLine
import com.selfwallet.app.ui.components.asColor
import com.selfwallet.app.util.Currencies

@Composable
fun ManageScreen(vm: WalletViewModel, state: WalletState, what: String, onBack: () -> Unit) {
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var editingAllocation by remember { mutableStateOf<AllocationEntity?>(null) }
    var editingRule by remember { mutableStateOf<RuleEntity?>(null) }
    var editingRate by remember { mutableStateOf<RateEntity?>(null) }
    var editingBudget by remember { mutableStateOf<BudgetEntity?>(null) }
    var editingTrip by remember { mutableStateOf<TripEntity?>(null) }
    var editingBill by remember { mutableStateOf<BillEntity?>(null) }
    val context = LocalContext.current
    val hidden = state.settings.hideAmounts

    val title = when (what) {
        "allocations" -> "Expense for"
        "rules" -> "Auto-categorise rules"
        "rates" -> "Exchange rates"
        "budgets" -> "Monthly budgets"
        "trips" -> "Trips (travel mode)"
        "templates" -> "Templates"
        "splits" -> "Friends owe you"
        "upcoming" -> "Bills & subscriptions"
        else -> "Categories"
    }

    fun add() {
        when (what) {
            "allocations" -> editingAllocation = AllocationEntity(name = "", kind = SpendKind.CORPORATE, color = Palette[13], sortOrder = state.allocations.size)
            "rules" -> editingRule = RuleEntity(keyword = "")
            "rates" -> editingRate = RateEntity(currency = state.missingRates.firstOrNull() ?: "", rate = 0.0)
            "trips" -> {
                val today = java.time.LocalDate.now().toEpochDay()
                editingTrip = TripEntity(name = "", startDay = today, endDay = today + 6)
            }
            "upcoming" -> editingBill = BillEntity(
                title = "", amount = 0.0, currency = state.base,
                dueDay = java.time.LocalDate.now().plusDays(7).toEpochDay(), hash = "manual-${System.currentTimeMillis()}",
            )
            "templates", "splits" -> Unit
            "budgets" -> editingBudget = BudgetEntity(
                categoryId = null,
                kind = if (state.budgets.any { it.budget.categoryId == null && it.budget.kind == Budgets.ALL }) SpendKind.PERSONAL else Budgets.ALL,
                amount = 0.0,
            )
            else -> editingCategory = CategoryEntity(name = "", icon = "other", color = Palette[0], sortOrder = state.categories.size)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (what != "templates" && what != "splits") {
                FloatingActionButton(onClick = { add() }, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Filled.Add, "Add") }
            }
        },
    ) { inner ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            when (what) {
                "allocations" -> {
                    item {
                        Hint("Decide where each expense belongs — yourself, your family, your employer or a client. Corporate destinations are separated in reports and CSV exports for easy claims.")
                    }
                    items(state.allocations, key = { it.id }) { a ->
                        val corporate = a.kind == SpendKind.CORPORATE
                        val count = state.items.count { it.tx.allocationId == a.id }
                        Row0(
                            leading = { IconBadge(if (corporate) Icons.Filled.Business else Icons.Filled.Person, a.color.asColor()) },
                            title = a.name,
                            subtitle = "${if (corporate) "Corporate" else "Personal"} · $count transaction${if (count == 1) "" else "s"}" +
                                if (a.id == state.settings.defaultAllocationId) " · default for SMS" else "",
                            onClick = { editingAllocation = a },
                        )
                    }
                }
                "rules" -> {
                    item {
                        Hint("When an SMS or merchant contains the keyword, the transaction gets this category and destination automatically. Rules are also created when you tick “Remember” while editing an SMS transaction.")
                    }
                    if (state.rules.isEmpty()) {
                        item { EmptyState(Icons.Filled.AutoAwesome, "No rules yet", "Add one, e.g. keyword “uber” → Transport · Company.") }
                    }
                    items(state.rules, key = { it.id }) { r ->
                        val c = state.categories.firstOrNull { it.id == r.categoryId }
                        val a = state.allocations.firstOrNull { it.id == r.allocationId }
                        Row0(
                            leading = { IconBadge(CategoryIcons.of(c?.icon), c?.color?.asColor() ?: MaterialTheme.colorScheme.primary) },
                            title = "“${r.keyword}”",
                            subtitle = listOfNotNull(c?.name, a?.name, r.renameTo?.takeIf { it.isNotBlank() }?.let { "rename to $it" }).joinToString(" · ").ifEmpty { "No action" },
                            onClick = { editingRule = r },
                        )
                    }
                }
                "trips" -> {
                    item {
                        Hint("Create a trip with its dates. Every expense in that period is tagged to it automatically, and you can filter reports by trip. Optionally send the trip's SMS expenses to a company or client.")
                    }
                    if (state.trips.isEmpty()) {
                        item { EmptyState(Icons.Filled.Luggage, "No trips yet", "Tap + before you travel, e.g. “London · Client X”.") }
                    }
                    items(state.trips, key = { it.id }) { t ->
                        val spent = state.items.filter { it.tx.tripId == t.id && it.isExpense }.sumOf { it.base }
                        val dest = state.allocations.firstOrNull { it.id == t.allocationId }?.name
                        Row0(
                            leading = { IconBadge(Icons.Filled.Luggage, MaterialTheme.colorScheme.primary) },
                            title = t.name,
                            subtitle = "${dayText(t.startDay)} – ${dayText(t.endDay)} · ${money(spent, state.base, hidden)}" + (dest?.let { " · $it" } ?: ""),
                            onClick = { editingTrip = t },
                        )
                    }
                }
                "templates" -> {
                    item {
                        Hint("Templates appear at the top of the Add screen. Create one from any expense with the bookmark button. Tap a template here to delete it.")
                    }
                    if (state.templates.isEmpty()) {
                        item { EmptyState(Icons.Filled.Bolt, "No templates yet", "Open Add, fill in an expense and tap the bookmark icon.") }
                    }
                    items(state.templates, key = { it.id }) { t ->
                        val cat = state.categories.firstOrNull { it.id == t.categoryId }
                        Row0(
                            leading = { IconBadge(CategoryIcons.of(cat?.icon), cat?.color?.asColor() ?: MaterialTheme.colorScheme.primary) },
                            title = t.name,
                            subtitle = listOfNotNull(Currencies.format(t.amount, t.currency), cat?.name, t.merchant.takeIf { it.isNotBlank() }).joinToString(" · "),
                            onClick = { vm.deleteTemplate(t) },
                        )
                    }
                }
                "splits" -> {
                    val open = state.splits.filter { !it.split.settled }
                    item {
                        Hint("Money friends owe you from split expenses. Tap Remind to send them a message, or Settled when they pay.")
                    }
                    if (open.isEmpty()) {
                        item { EmptyState(Icons.Filled.Groups, "All settled", "Split an expense from its edit screen with “Add person”.") }
                    }
                    open.groupBy { it.split.person.trim().lowercase() }.forEach { (_, list) ->
                        item(key = "p" + list.first().split.id) {
                            val person = list.first().split.person
                            val totals = list.groupBy { it.currency }.map { (c, l) -> Currencies.format(l.sumOf { it.split.amount }, c) }
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(person, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Owes you ${totals.joinToString(" + ")}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    TextButton(onClick = {
                                        val lines = list.joinToString("\n") { s ->
                                            "• ${s.tx?.tx?.merchant?.ifBlank { null } ?: s.tx?.category?.name ?: "Expense"}" +
                                                (s.tx?.let { " (${Dates.dayLabel(Dates.toDate(it.tx.timestamp))})" } ?: "") +
                                                ": ${Currencies.format(s.split.amount, s.currency)}"
                                        }
                                        val msg = "Hi $person, a quick reminder for our shared expenses:\n$lines\nTotal: ${totals.joinToString(" + ")}. Thanks!"
                                        runCatching {
                                            context.startActivity(
                                                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg), "Send reminder"),
                                            )
                                        }
                                    }) { Text("Remind") }
                                    TextButton(onClick = { list.forEach { vm.setSplitSettled(it.split, true) } }) { Text("Settled") }
                                }
                                list.forEach { s ->
                                    Text(
                                        "${s.tx?.tx?.merchant?.ifBlank { null } ?: s.tx?.category?.name ?: "Expense"} · ${Currencies.format(s.split.amount, s.currency)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
                "upcoming" -> {
                    item {
                        Hint("Card bills are read from your bank's reminder SMS; subscriptions and other repeating payments are spotted from your history. You get a reminder 2 days before and on the day.")
                    }
                    val upcoming = state.upcoming(60)
                    if (upcoming.isEmpty()) {
                        item { EmptyState(Icons.Filled.Autorenew, "Nothing due soon", "Bills and repeating payments will show up here.") }
                    }
                    items(upcoming.size) { i -> UpcomingRow(upcoming[i], hidden, onMarkPaid = { vm.saveBill(it.copy(paid = true)) }) }
                    if (state.recurring.isNotEmpty()) {
                        item { Text("Repeating payments", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)) }
                        items(state.recurring.size) { i ->
                            val r = state.recurring[i]
                            Row0(
                                leading = { IconBadge(Icons.Filled.Autorenew, MaterialTheme.colorScheme.primary) },
                                title = r.merchant,
                                subtitle = "${r.label} · ~${money(r.amount, r.currency, hidden)} · next ${Dates.dayLabel(r.next)} · seen ${r.count}×",
                                onClick = {},
                            )
                        }
                    }
                    val paid = state.bills.filter { it.paid }
                    if (paid.isNotEmpty()) {
                        item { Text("Paid bills", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)) }
                        items(paid, key = { "b" + it.id }) { b ->
                            Row0(
                                leading = { IconBadge(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.primary) },
                                title = b.title,
                                subtitle = "${money(b.amount, b.currency, hidden)} · was due ${dayText(b.dueDay)}",
                                onClick = { editingBill = b },
                            )
                        }
                    }
                }
                "budgets" -> {
                    item {
                        Hint("Set a monthly limit for all spending, for personal or corporate, or for a single category. You get a notification at 80% and when you go over. Amounts are in ${state.base}.")
                    }
                    if (state.budgets.isEmpty()) {
                        item { EmptyState(Icons.Filled.Savings, "No budgets yet", "Tap + to set your first monthly limit.") }
                    }
                    items(state.budgets, key = { it.budget.id }) { b ->
                        val over = b.fraction >= 1f
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { editingBudget = b.budget }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(b.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text(
                                    "${Currencies.format(b.spent, state.base)} / ${Currencies.format(b.budget.amount, state.base)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            ProgressLine(
                                b.fraction,
                                when {
                                    over -> MaterialTheme.colorScheme.error
                                    b.fraction >= 0.8f -> Color(0xFFD97706)
                                    else -> MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
                "rates" -> {
                    item {
                        Hint("Totals are shown in your base currency (${state.base}). Enter how much 1 unit of each foreign currency is worth in ${state.base}. Rates are stored offline — update them whenever you like.")
                    }
                    items(state.missingRates, key = { "m$it" }) { code ->
                        Row0(
                            leading = { IconBadge(Icons.Filled.Warning, MaterialTheme.colorScheme.error) },
                            title = "$code · ${Currencies.info(code).name}",
                            subtitle = "Rate missing — tap to set",
                            onClick = { editingRate = RateEntity(code, 0.0) },
                        )
                    }
                    items(state.rates, key = { it.currency }) { r ->
                        Row0(
                            leading = { IconBadge(Icons.Filled.CurrencyExchange, MaterialTheme.colorScheme.primary) },
                            title = "1 ${r.currency} = ${trimRate(r.rate)} ${state.base}",
                            subtitle = Currencies.info(r.currency).name,
                            onClick = { editingRate = r },
                        )
                    }
                }
                else -> {
                    listOf(TxType.EXPENSE to "Expense categories", TxType.INCOME to "Income categories").forEach { (t, label) ->
                        item(key = "h$t") {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(state.categories.filter { it.type == t }, key = { it.id }) { c ->
                            val count = state.items.count { it.tx.categoryId == c.id }
                            Row0(
                                leading = { IconBadge(CategoryIcons.of(c.icon), c.color.asColor()) },
                                title = c.name,
                                subtitle = "$count transaction${if (count == 1) "" else "s"}",
                                onClick = { editingCategory = c },
                            )
                        }
                    }
                }
            }
        }
    }

    editingCategory?.let { c ->
        CategoryDialog(
            initial = c,
            onSave = { vm.saveCategory(it); editingCategory = null },
            onDelete = if (c.id != 0L) ({ vm.deleteCategory(c); editingCategory = null }) else null,
            onDismiss = { editingCategory = null },
        )
    }
    editingAllocation?.let { a ->
        AllocationDialog(
            initial = a,
            isDefault = a.id != 0L && a.id == state.settings.defaultAllocationId,
            onSave = { vm.saveAllocation(it); editingAllocation = null },
            onMakeDefault = { vm.setDefaultAllocation(a.id) },
            onDelete = if (a.id != 0L && state.allocations.size > 1) ({ vm.deleteAllocation(a); editingAllocation = null }) else null,
            onDismiss = { editingAllocation = null },
        )
    }
    editingRule?.let { r ->
        RuleDialog(
            initial = r,
            state = state,
            onSave = { vm.saveRule(it); editingRule = null },
            onDelete = if (r.id != 0L) ({ vm.deleteRule(r); editingRule = null }) else null,
            onDismiss = { editingRule = null },
        )
    }
    editingTrip?.let { t ->
        TripDialog(
            initial = t,
            state = state,
            onSave = { vm.saveTrip(it); editingTrip = null },
            onDelete = if (t.id != 0L) ({ vm.deleteTrip(t); editingTrip = null }) else null,
            onDismiss = { editingTrip = null },
        )
    }
    editingBill?.let { b ->
        BillDialog(
            initial = b,
            onSave = { vm.saveBill(it); editingBill = null },
            onDelete = if (b.id != 0L) ({ vm.deleteBill(b); editingBill = null }) else null,
            onDismiss = { editingBill = null },
        )
    }
    editingBudget?.let { b ->
        BudgetDialog(
            initial = b,
            state = state,
            onSave = { vm.saveBudget(it); editingBudget = null },
            onDelete = if (b.id != 0L) ({ vm.deleteBudget(b); editingBudget = null }) else null,
            onDismiss = { editingBudget = null },
        )
    }
    editingRate?.let { r ->
        RateDialog(
            initial = r,
            base = state.base,
            suggestions = state.missingRates,
            onSave = { vm.saveRate(it); editingRate = null },
            onDelete = if (state.rates.any { it.currency == r.currency }) ({ vm.deleteRate(r); editingRate = null }) else null,
            onDismiss = { editingRate = null },
        )
    }
}

private fun trimRate(r: Double): String {
    val s = String.format(java.util.Locale.US, "%.6f", r)
    return s.trimEnd('0').trimEnd('.')
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun Row0(leading: @Composable () -> Unit, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = leading,
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun DialogActions(canSave: Boolean, onSave: () -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit): Pair<@Composable () -> Unit, @Composable () -> Unit> {
    val confirm: @Composable () -> Unit = { TextButton(onClick = onSave, enabled = canSave) { Text("Save") } }
    val dismiss: @Composable () -> Unit = {
        Row {
            if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    }
    return confirm to dismiss
}

@Composable
private fun ColorPicker(selected: Long, onPick: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Palette.forEach { c ->
            val isSel = c == selected
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(c.asColor())
                    .then(if (isSel) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape) else Modifier)
                    .clickable { onPick(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSel) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun CategoryDialog(initial: CategoryEntity, onSave: (CategoryEntity) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var icon by remember { mutableStateOf(initial.icon) }
    var color by remember { mutableLongStateOf(initial.color) }
    var type by remember { mutableStateOf(initial.type) }
    val (confirm, dismiss) = DialogActions(
        canSave = name.isNotBlank(),
        onSave = { onSave(initial.copy(name = name.trim(), icon = icon, color = color, type = type)) },
        onDelete = onDelete,
        onDismiss = onDismiss,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New category" else "Edit category") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(CategoryIcons.of(icon), color.asColor(), size = 52.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(TxType.EXPENSE to "Expense", TxType.INCOME to "Income").forEachIndexed { i, (t, l) ->
                        SegmentedButton(selected = type == t, onClick = { type = t }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryIcons.all.forEach { (key, vec) ->
                        val sel = key == icon
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(if (sel) color.asColor() else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(vec, key, tint = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                ColorPicker(color) { color = it }
            }
        },
        confirmButton = confirm,
        dismissButton = dismiss,
    )
}

@Composable
private fun AllocationDialog(
    initial: AllocationEntity,
    isDefault: Boolean,
    onSave: (AllocationEntity) -> Unit,
    onMakeDefault: () -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var kind by remember { mutableStateOf(initial.kind) }
    var color by remember { mutableLongStateOf(initial.color) }
    var makeDefault by remember { mutableStateOf(isDefault) }
    val (confirm, dismiss) = DialogActions(
        canSave = name.isNotBlank(),
        onSave = {
            onSave(initial.copy(name = name.trim(), kind = kind, color = color))
            if (makeDefault && !isDefault && initial.id != 0L) onMakeDefault()
        },
        onDelete = onDelete,
        onDismiss = onDismiss,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New destination" else "Edit destination") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Name") },
                    placeholder = { Text("e.g. ACME Corp, Client trip, Family") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(SpendKind.PERSONAL to "Personal", SpendKind.CORPORATE to "Corporate").forEachIndexed { i, (k, l) ->
                        SegmentedButton(selected = kind == k, onClick = { kind = k }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                ColorPicker(color) { color = it }
                if (initial.id != 0L) {
                    Spacer(Modifier.height(12.dp))
                    FilterChip(
                        selected = makeDefault,
                        onClick = { if (!isDefault) makeDefault = !makeDefault },
                        label = { Text(if (isDefault) "Default for SMS" else "Use as default for SMS") },
                        leadingIcon = { if (makeDefault) Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) },
                    )
                }
            }
        },
        confirmButton = confirm,
        dismissButton = dismiss,
    )
}

@Composable
private fun RuleDialog(initial: RuleEntity, state: WalletState, onSave: (RuleEntity) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var keyword by remember { mutableStateOf(initial.keyword) }
    var categoryId by remember { mutableStateOf(initial.categoryId) }
    var allocationId by remember { mutableStateOf(initial.allocationId) }
    var rename by remember { mutableStateOf(initial.renameTo.orEmpty()) }
    val (confirm, dismiss) = DialogActions(
        canSave = keyword.trim().length >= 2,
        onSave = { onSave(initial.copy(keyword = keyword.trim(), categoryId = categoryId, allocationId = allocationId, renameTo = rename.trim().ifEmpty { null })) },
        onDelete = onDelete,
        onDismiss = onDismiss,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New rule" else "Edit rule") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = keyword, onValueChange = { keyword = it }, singleLine = true,
                    label = { Text("If SMS or merchant contains") },
                    placeholder = { Text("e.g. uber, carrefour, HDFCBK") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Set category", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.categories.forEach { c ->
                        FilterChip(
                            selected = categoryId == c.id,
                            onClick = { categoryId = if (categoryId == c.id) null else c.id },
                            label = { Text(c.name) },
                            leadingIcon = { Icon(CategoryIcons.of(c.icon), null, Modifier.size(16.dp), tint = c.color.asColor()) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Set expense for", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.allocations.forEach { a ->
                        FilterChip(
                            selected = allocationId == a.id,
                            onClick = { allocationId = if (allocationId == a.id) null else a.id },
                            label = { Text(a.name) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = rename, onValueChange = { rename = it }, singleLine = true,
                    label = { Text("Rename merchant to (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = confirm,
        dismissButton = dismiss,
    )
}

private fun dayText(epochDay: Long): String = Dates.dayLabel(java.time.LocalDate.ofEpochDay(epochDay))

@Composable
private fun DayField(label: String, epochDay: Long, onPick: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Surface(
        onClick = { open = true },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CalendarMonth, null, Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("$label: ${dayText(epochDay)}")
        }
    }
    if (open) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = epochDay * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { onPick(it / 86_400_000L) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun TripDialog(
    initial: TripEntity,
    state: WalletState,
    onSave: (TripEntity) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var start by remember { mutableLongStateOf(initial.startDay) }
    var end by remember { mutableLongStateOf(initial.endDay) }
    var allocationId by remember { mutableStateOf(initial.allocationId) }
    val (confirm, dismiss) = DialogActions(
        canSave = name.isNotBlank() && end >= start,
        onSave = { onSave(initial.copy(name = name.trim(), startDay = start, endDay = end, allocationId = allocationId)) },
        onDelete = onDelete,
        onDismiss = onDismiss,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Luggage, null) },
        title = { Text(if (initial.id == 0L) "New trip" else "Edit trip") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Trip name") },
                    placeholder = { Text("e.g. London · Client X") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                DayField("From", start) { start = it; if (end < it) end = it }
                DayField("To", end) { end = maxOf(it, start) }
                Text("SMS expenses during the trip go to", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = allocationId == null, onClick = { allocationId = null }, label = { Text("Usual default") })
                    state.allocations.forEach { a ->
                        FilterChip(selected = allocationId == a.id, onClick = { allocationId = a.id }, label = { Text(a.name) })
                    }
                }
            }
        },
        confirmButton = confirm,
        dismissButton = dismiss,
    )
}

@Composable
private fun BillDialog(initial: BillEntity, onSave: (BillEntity) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var amount by remember { mutableStateOf(if (initial.amount > 0) trimRate(initial.amount) else "") }
    var currency by remember { mutableStateOf(initial.currency) }
    var due by remember { mutableLongStateOf(initial.dueDay) }
    var paid by remember { mutableStateOf(initial.paid) }
    val value = amount.replace(',', '.').toDoubleOrNull()
    val (confirm, dismiss) = DialogActions(
        canSave = title.isNotBlank() && value != null && value > 0 && currency.length == 3,
        onSave = { onSave(initial.copy(title = title.trim(), amount = value ?: 0.0, currency = currency.uppercase(), dueDay = due, paid = paid, reminded = if (due != initial.dueDay) 0 else initial.reminded)) },
        onDelete = onDelete,
        onDismiss = onDismiss,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New bill" else "Edit bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amount, onValueChange = { amount = it }, singleLine = true, label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = currency, onValueChange = { if (it.length <= 3) currency = it.uppercase() }, singleLine = true,
                        label = { Text("Cur.") }, modifier = Modifier.width(90.dp),
                    )
                }
                DayField("Due", due) { due = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = paid, onCheckedChange = { paid = it })
                    Text("Paid")
                }
            }
        },
        confirmButton = confirm,
        dismissButton = dismiss,
    )
}

@Composable
private fun BudgetDialog(
    initial: BudgetEntity,
    state: WalletState,
    onSave: (BudgetEntity) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var categoryId by remember { mutableStateOf(initial.categoryId) }
    var kind by remember { mutableStateOf(initial.kind) }
    var amount by remember { mutableStateOf(if (initial.amount > 0) trimRate(initial.amount) else "") }
    val value = amount.replace(',', '.').toDoubleOrNull()
    val (confirm, dismiss) = DialogActions(
        canSave = value != null && value > 0,
        onSave = { onSave(initial.copy(categoryId = categoryId, kind = kind, amount = value ?: 0.0)) },
        onDelete = onDelete,
        onDismiss = onDismiss,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Savings, null) },
        title = { Text(if (initial.id == 0L) "New budget" else "Edit budget") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Monthly limit") },
                    suffix = { Text(state.base) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Applies to", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Budgets.ALL to "All spending", SpendKind.PERSONAL to "Personal", SpendKind.CORPORATE to "Corporate").forEach { (k, l) ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(l) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = categoryId == null, onClick = { categoryId = null }, label = { Text("All categories") })
                    state.categories.filter { it.type == TxType.EXPENSE }.forEach { c ->
                        FilterChip(
                            selected = categoryId == c.id,
                            onClick = { categoryId = c.id },
                            label = { Text(c.name) },
                            leadingIcon = { Icon(CategoryIcons.of(c.icon), null, Modifier.size(16.dp), tint = c.color.asColor()) },
                        )
                    }
                }
            }
        },
        confirmButton = confirm,
        dismissButton = dismiss,
    )
}

@Composable
private fun RateDialog(
    initial: RateEntity,
    base: String,
    suggestions: List<String>,
    onSave: (RateEntity) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var code by remember { mutableStateOf(initial.currency) }
    var rate by remember { mutableStateOf(if (initial.rate > 0) trimRate(initial.rate) else "") }
    val value = rate.replace(',', '.').toDoubleOrNull()
    val validCode = code.length == 3 && code.all { it.isLetter() } && !code.equals(base, true)
    val (confirm, dismiss) = DialogActions(
        canSave = validCode && value != null && value > 0,
        onSave = { onSave(RateEntity(code.uppercase(), value ?: 1.0)) },
        onDelete = onDelete,
        onDismiss = onDismiss,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.CurrencyExchange, null) },
        title = { Text("Exchange rate") },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 3) code = it.uppercase() },
                    label = { Text("Currency code") },
                    placeholder = { Text("e.g. EUR") },
                    singleLine = true,
                    enabled = initial.rate <= 0.0,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                val quick = (suggestions + listOf("USD", "EUR", "GBP", "INR", "AED", "SAR")).distinct().filter { it != base }.take(6)
                if (initial.rate <= 0.0) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        quick.forEach { q -> FilterChip(selected = code == q, onClick = { code = q }, label = { Text(q) }) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text("1 ${code.ifEmpty { "XXX" }} equals") },
                    suffix = { Text(base) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = confirm,
        dismissButton = dismiss,
    )
}
