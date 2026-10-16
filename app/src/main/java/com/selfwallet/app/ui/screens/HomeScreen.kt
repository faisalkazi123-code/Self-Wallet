@file:OptIn(ExperimentalMaterial3Api::class)

package com.selfwallet.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.selfwallet.app.data.db.SpendKind
import com.selfwallet.app.ui.TxUi
import com.selfwallet.app.ui.WalletState
import com.selfwallet.app.ui.components.AppCard
import com.selfwallet.app.ui.components.DonutChart
import com.selfwallet.app.ui.components.EmptyState
import com.selfwallet.app.ui.components.ProgressLine
import com.selfwallet.app.ui.components.SectionTitle
import com.selfwallet.app.ui.components.Slice
import com.selfwallet.app.ui.components.TransactionRow
import com.selfwallet.app.ui.components.asColor
import com.selfwallet.app.ui.components.money
import com.selfwallet.app.ui.theme.CorporateBlue
import com.selfwallet.app.ui.theme.Ink
import com.selfwallet.app.ui.theme.Teal
import com.selfwallet.app.util.Dates
import java.time.LocalTime
import java.time.YearMonth

/** "ALL", PERSONAL or CORPORATE. */
fun List<TxUi>.byKind(kind: String): List<TxUi> = when (kind) {
    SpendKind.PERSONAL -> filter { !it.isCorporate }
    SpendKind.CORPORATE -> filter { it.isCorporate }
    else -> this
}

fun List<TxUi>.inRange(range: LongRange) = filter { it.tx.timestamp in range }
fun List<TxUi>.expenseTotal() = filter { it.isExpense }.sumOf { it.base }
fun List<TxUi>.incomeTotal() = filter { !it.isExpense }.sumOf { it.base }

@Composable
fun HomeScreen(
    state: WalletState,
    onAdd: (String?) -> Unit,
    onOpen: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onReview: () -> Unit,
    onFixRates: () -> Unit,
    onToggleHide: () -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf("ALL") }
    val hidden = state.settings.hideAmounts
    val month = remember { YearMonth.now() }
    val monthItems = remember(state.items, kind) { state.items.inRange(Dates.monthRange(month)).byKind(kind) }
    val allMonth = remember(state.items) { state.items.inRange(Dates.monthRange(month)) }
    val spent = monthItems.expenseTotal()
    val income = monthItems.incomeTotal()
    val base = state.base

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAdd(null) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Self Wallet", style = MaterialTheme.typography.headlineSmall)
                    }
                    IconButton(onClick = onToggleHide) {
                        Icon(
                            if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (hidden) "Show amounts" else "Hide amounts",
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("ALL" to "All", SpendKind.PERSONAL to "Personal", SpendKind.CORPORATE to "Corporate").forEach { (k, label) ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(label) })
                    }
                }
            }
            item {
                HeroCard(
                    title = "Spent in ${Dates.monthLabel(month).substringBefore(' ')}",
                    spent = money(spent, base, hidden),
                    income = money(income, base, hidden),
                    net = money(income - spent, base, hidden),
                )
            }
            if (state.pendingReview > 0) {
                item {
                    Banner(
                        icon = Icons.Filled.Sms,
                        title = "${state.pendingReview} auto-added from SMS",
                        body = "Pick a category so your reports stay accurate.",
                        action = "Review",
                        onClick = onReview,
                    )
                }
            }
            if (state.missingRates.isNotEmpty()) {
                item {
                    Banner(
                        icon = Icons.Filled.CurrencyExchange,
                        title = "Set exchange rates",
                        body = "${state.missingRates.joinToString()} → $base is counted 1:1 until you add a rate.",
                        action = "Fix",
                        onClick = onFixRates,
                    )
                }
            }
            if (kind == "ALL") {
                item { SplitCards(allMonth, base, hidden) }
            }
            if (state.items.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.TaskAlt,
                        title = "You're all set",
                        body = "New bank and card SMS will appear here automatically. You can also add cash spending with the Add button.",
                    )
                }
            } else {
                val byCat = monthItems.filter { it.isExpense }
                    .groupBy { it.category }
                    .map { (c, list) -> Triple(c?.name ?: "Uncategorised", list.sumOf { it.base }, c?.color?.asColor() ?: Color(0xFF94A3B8)) }
                    .sortedByDescending { it.second }
                if (byCat.isNotEmpty()) {
                    item { SectionTitle("Where your money went") }
                    item { CategoryDonut(byCat, spent, base, hidden) }
                }
                item {
                    SectionTitle("Recent") {
                        TextButton(onClick = onSeeAll) { Text("See all") }
                    }
                }
                item {
                    AppCard(Modifier.padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            val recent = state.items.byKind(kind).take(6)
                            recent.forEachIndexed { i, it ->
                                TransactionRow(it, hidden, onClick = { onOpen(it.tx.id) })
                                if (i < recent.lastIndex) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                            if (recent.isEmpty()) {
                                Text(
                                    "Nothing here yet",
                                    Modifier.padding(20.dp),
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

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun HeroCard(title: String, spent: String, income: String, net: String) {
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(Color(0xFF14B8A6), Teal, Ink)))
            .padding(24.dp),
    ) {
        Column {
            Text(title, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                spent,
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth()) {
                HeroStat(Icons.Filled.ArrowDownward, "Income", income, Modifier.weight(1f))
                HeroStat(Icons.Filled.ArrowUpward, "Net", net, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroStat(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
            Text(value, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SplitCards(items: List<TxUi>, base: String, hidden: Boolean) {
    val personal = items.filter { it.isExpense && !it.isCorporate }.sumOf { it.base }
    val corporate = items.filter { it.isExpense && it.isCorporate }.sumOf { it.base }
    val total = (personal + corporate).takeIf { it > 0 } ?: 1.0
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SplitCard(Icons.Filled.Person, "Personal", money(personal, base, hidden), (personal / total).toFloat(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        SplitCard(Icons.Filled.Business, "Corporate", money(corporate, base, hidden), (corporate / total).toFloat(), CorporateBlue, Modifier.weight(1f))
    }
}

@Composable
private fun SplitCard(icon: ImageVector, label: String, value: String, fraction: Float, color: Color, modifier: Modifier) {
    AppCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            ProgressLine(fraction, color)
        }
    }
}

@Composable
fun Banner(icon: ImageVector, title: String, body: String, action: String, onClick: () -> Unit) {
    AppCard(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun CategoryDonut(rows: List<Triple<String, Double, Color>>, total: Double, base: String, hidden: Boolean) {
    val top = rows.take(5)
    val rest = rows.drop(5).sumOf { it.second }
    val slices = top.map { Slice(it.first, it.second, it.third) } +
        if (rest > 0) listOf(Slice("Other", rest, Color(0xFFCBD5E1))) else emptyList()
    AppCard(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            DonutChart(slices, Modifier.size(132.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (hidden) "••••" else com.selfwallet.app.util.Currencies.compact(total),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                slices.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(s.color),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            s.label,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${(s.value / (if (total > 0) total else 1.0) * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
