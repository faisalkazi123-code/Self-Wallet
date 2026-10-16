@file:OptIn(ExperimentalMaterial3Api::class)

package com.selfwallet.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.selfwallet.app.backup.PdfReport
import com.selfwallet.app.data.db.Claim
import com.selfwallet.app.data.db.SpendKind
import com.selfwallet.app.ui.TxUi
import com.selfwallet.app.ui.WalletState
import com.selfwallet.app.ui.components.AppCard
import com.selfwallet.app.ui.components.BarChart
import com.selfwallet.app.ui.components.CategoryIcons
import com.selfwallet.app.ui.components.EmptyState
import com.selfwallet.app.ui.components.IconBadge
import com.selfwallet.app.ui.components.ProgressLine
import com.selfwallet.app.ui.components.SectionTitle
import com.selfwallet.app.ui.components.asColor
import com.selfwallet.app.ui.components.money
import com.selfwallet.app.ui.theme.CorporateBlue
import com.selfwallet.app.util.Currencies
import com.selfwallet.app.util.Dates
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun ReportsScreen(
    state: WalletState,
    onExport: (Uri, List<TxUi>) -> Unit,
    onExportPdf: (Uri, PdfReport.Info, List<TxUi>, Boolean) -> Unit,
) {
    var showPdf by remember { mutableStateOf(false) }
    var markSubmitted by remember { mutableStateOf(true) }
    var yearly by rememberSaveable { mutableStateOf(false) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var kind by rememberSaveable { mutableStateOf("ALL") }
    var allocationId by rememberSaveable { mutableLongStateOf(0L) }
    val hidden = state.settings.hideAmounts
    val base = state.base

    val month = YearMonth.now().minusMonths(offset.toLong())
    val year = LocalDate.now().year - offset
    val range = if (yearly) Dates.yearRange(year) else Dates.monthRange(month)
    val title = if (yearly) year.toString() else Dates.monthLabel(month)

    val rows = remember(state.items, yearly, offset, kind, allocationId) {
        state.items.inRange(range).byKind(kind).filter { allocationId == 0L || it.tx.allocationId == allocationId }
    }
    val expenses = rows.filter { it.isExpense }
    val spent = expenses.sumOf { it.base }
    val income = rows.incomeTotal()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) onExport(uri, rows)
    }
    val scopeLabel = when {
        allocationId != 0L -> state.allocations.firstOrNull { it.id == allocationId }?.name ?: "Report"
        kind == SpendKind.CORPORATE -> "Corporate"
        kind == SpendKind.PERSONAL -> "Personal"
        else -> "All spending"
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            onExportPdf(
                uri,
                PdfReport.Info(
                    title = "Expense report",
                    owner = state.settings.ownerName,
                    scope = scopeLabel,
                    period = title,
                    baseCurrency = state.base,
                ),
                rows,
                markSubmitted,
            )
        }
    }
    val unclaimed = rows.count { it.isExpense && it.isCorporate && Claim.of(it.tx) == Claim.NOT_CLAIMED }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                actions = {
                    IconButton(onClick = {
                        val who = when {
                            allocationId != 0L -> state.allocations.firstOrNull { it.id == allocationId }?.name ?: "report"
                            kind == SpendKind.CORPORATE -> "corporate"
                            kind == SpendKind.PERSONAL -> "personal"
                            else -> "all"
                        }
                        exportLauncher.launch("self-wallet-${who.lowercase().replace(' ', '-')}-${title.lowercase().replace(' ', '-')}.csv")
                    }, enabled = rows.isNotEmpty()) {
                        Icon(Icons.Filled.FileDownload, "Export CSV")
                    }
                    IconButton(onClick = { showPdf = true }, enabled = rows.isNotEmpty()) {
                        Icon(Icons.Filled.PictureAsPdf, "Export PDF report")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    listOf(false to "Monthly", true to "Yearly").forEachIndexed { i, (y, label) ->
                        SegmentedButton(
                            selected = yearly == y,
                            onClick = { yearly = y; offset = 0 },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label) }
                    }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { offset++ }) { Icon(Icons.Filled.ChevronLeft, "Previous") }
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { offset-- }, enabled = offset > 0) { Icon(Icons.Filled.ChevronRight, "Next") }
                }
            }
            item {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("ALL" to "Everything", SpendKind.PERSONAL to "Personal", SpendKind.CORPORATE to "Corporate").forEach { (k, label) ->
                        FilterChip(selected = kind == k && allocationId == 0L, onClick = { kind = k; allocationId = 0L }, label = { Text(label) })
                    }
                    state.allocations.forEach { a ->
                        FilterChip(
                            selected = allocationId == a.id,
                            onClick = { allocationId = if (allocationId == a.id) 0L else a.id; kind = "ALL" },
                            label = { Text(a.name) },
                            leadingIcon = {
                                Icon(if (a.kind == SpendKind.CORPORATE) Icons.Filled.Business else Icons.Filled.Person, null, tint = a.color.asColor())
                            },
                        )
                    }
                }
            }
            item {
                AppCard(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Total spent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(money(spent, base, hidden), style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(12.dp))
                        Row {
                            Stat("Income", money(income, base, hidden), Modifier.weight(1f))
                            Stat("Net", money(income - spent, base, hidden), Modifier.weight(1f))
                            val days = if (yearly) {
                                if (offset == 0) LocalDate.now().dayOfYear else LocalDate.of(year, 12, 31).dayOfYear
                            } else {
                                if (offset == 0) LocalDate.now().dayOfMonth else month.lengthOfMonth()
                            }
                            Stat("Per day", money(spent / days.coerceAtLeast(1), base, hidden), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(20.dp))
                        if (yearly) {
                            val values = (1..12).map { m -> expenses.filter { Dates.toDate(it.tx.timestamp).monthValue == m }.sumOf { it.base } }
                            val labels = (1..12).map { m -> Dates.shortMonthLabel(YearMonth.of(year, m)).take(1) }
                            val hl = if (offset == 0) LocalDate.now().monthValue - 1 else -1
                            BarChart(values, labels, highlight = hl)
                        } else {
                            val n = month.lengthOfMonth()
                            val values = (1..n).map { d -> expenses.filter { Dates.toDate(it.tx.timestamp).dayOfMonth == d }.sumOf { it.base } }
                            val labels = (1..n).map { d -> if (d == 1 || d % 7 == 0) d.toString() else "" }
                            val hl = if (offset == 0) LocalDate.now().dayOfMonth - 1 else -1
                            BarChart(values, labels, highlight = hl)
                        }
                    }
                }
            }

            if (rows.isEmpty()) {
                item {
                    EmptyState(Icons.Filled.QueryStats, "No data for $title", "Transactions you add or receive by SMS will be analysed here.")
                }
            } else {
                if (expenses.isNotEmpty()) {
                    item { SectionTitle("By category") }
                    item {
                        val groups = expenses.groupBy { it.category }
                            .map { (c, l) -> Triple(c, l.sumOf { it.base }, l.size) }
                            .sortedByDescending { it.second }
                        BreakdownCard {
                            groups.forEach { (c, total, count) ->
                                BreakdownRow(
                                    icon = CategoryIcons.of(c?.icon),
                                    color = c?.color?.asColor() ?: Color(0xFF94A3B8),
                                    label = c?.name ?: "Uncategorised",
                                    sub = "$count transaction${if (count == 1) "" else "s"}",
                                    value = money(total, base, hidden),
                                    fraction = (total / spent).toFloat(),
                                )
                            }
                        }
                    }
                    item { SectionTitle("Where it went") }
                    item {
                        val groups = expenses.groupBy { it.allocation }
                            .map { (a, l) -> a to l.sumOf { it.base } }
                            .sortedByDescending { it.second }
                        BreakdownCard {
                            groups.forEach { (a, total) ->
                                val corporate = a?.kind == SpendKind.CORPORATE
                                BreakdownRow(
                                    icon = if (corporate) Icons.Filled.Business else Icons.Filled.Person,
                                    color = a?.color?.asColor() ?: if (corporate) CorporateBlue else MaterialTheme.colorScheme.primary,
                                    label = a?.name ?: "Not assigned",
                                    sub = if (corporate) "Corporate" else "Personal",
                                    value = money(total, base, hidden),
                                    fraction = (total / spent).toFloat(),
                                )
                            }
                        }
                    }
                }
                val currencies = rows.groupBy { it.tx.currency }
                if (currencies.size > 1 || currencies.keys.firstOrNull() != base) {
                    item { SectionTitle("By currency") }
                    item {
                        BreakdownCard {
                            currencies.forEach { (code, l) ->
                                val out = l.filter { it.isExpense }.sumOf { it.tx.amount }
                                val outBase = l.filter { it.isExpense }.sumOf { it.base }
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(code, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(56.dp))
                                    Text(
                                        if (hidden) "••••" else Currencies.format(out, code),
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (code != base) {
                                        Text(
                                            "≈ " + money(outBase, base, hidden),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPdf) {
        AlertDialog(
            onDismissRequest = { showPdf = false },
            icon = { Icon(Icons.Filled.PictureAsPdf, null) },
            title = { Text("PDF expense report") },
            text = {
                Column {
                    Text("$scopeLabel · $title · ${rows.size} transactions. Receipt photos are added on their own pages.")
                    if (state.settings.ownerName.isBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tip: add your name in Settings › Your name on reports.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (unclaimed > 0) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = markSubmitted, onCheckedChange = { markSubmitted = it })
                            Text("Mark $unclaimed corporate expense${if (unclaimed == 1) "" else "s"} as submitted")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPdf = false
                    pdfLauncher.launch("expense-report-${scopeLabel.lowercase().replace(' ', '-')}-${title.lowercase().replace(' ', '-')}.pdf")
                }) { Text("Create PDF") }
            },
            dismissButton = { TextButton(onClick = { showPdf = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BreakdownCard(content: @Composable () -> Unit) {
    AppCard(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) { content() }
    }
}

@Composable
private fun BreakdownRow(icon: ImageVector, color: Color, label: String, sub: String, value: String, fraction: Float) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, color, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressLine(fraction, color, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Text(
                    "${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
