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
import com.selfwallet.app.data.db.AllocationEntity
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
import com.selfwallet.app.ui.components.asColor
import com.selfwallet.app.util.Currencies

@Composable
fun ManageScreen(vm: WalletViewModel, state: WalletState, what: String, onBack: () -> Unit) {
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var editingAllocation by remember { mutableStateOf<AllocationEntity?>(null) }
    var editingRule by remember { mutableStateOf<RuleEntity?>(null) }
    var editingRate by remember { mutableStateOf<RateEntity?>(null) }

    val title = when (what) {
        "allocations" -> "Expense for"
        "rules" -> "Auto-categorise rules"
        "rates" -> "Exchange rates"
        else -> "Categories"
    }

    fun add() {
        when (what) {
            "allocations" -> editingAllocation = AllocationEntity(name = "", kind = SpendKind.CORPORATE, color = Palette[13], sortOrder = state.allocations.size)
            "rules" -> editingRule = RuleEntity(keyword = "")
            "rates" -> editingRate = RateEntity(currency = state.missingRates.firstOrNull() ?: "", rate = 0.0)
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
            FloatingActionButton(onClick = { add() }, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Filled.Add, "Add") }
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
                        leadingIcon = if (makeDefault) ({ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) else null,
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
