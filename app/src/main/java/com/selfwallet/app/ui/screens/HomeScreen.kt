@file:OptIn(ExperimentalMaterial3Api::class)

package com.selfwallet.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
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
import com.selfwallet.app.data.db.AccountEntity
import com.selfwallet.app.data.db.BillEntity
import com.selfwallet.app.ui.BudgetUi
import com.selfwallet.app.ui.Upcoming
import com.selfwallet.app.ui.TxUi
import com.selfwallet.app.ui.theme.LocalStyle
import com.selfwallet.app.util.Currencies
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
    onDuplicates: () -> Unit,
    onClaims: () -> Unit,
    onBudgets: () -> Unit,
    onManage: (String) -> Unit,
    onMarkBillPaid: (BillEntity) -> Unit,
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
                    budget = if (kind == "ALL") state.overallBudget else null,
                    base = base,
                    hidden = hidden,
                )
            }
            state.activeTrip?.let { trip ->
                item {
                    val tripSpent = state.items.filter { it.tx.tripId == trip.id && it.isExpense }.sumOf { it.base }
                    Banner(
                        icon = Icons.Filled.Luggage,
                        title = "Travel mode: ${trip.name}",
                        body = "${money(tripSpent, base, hidden)} spent so far · new expenses are tagged to this trip",
                        action = "Trips",
                        onClick = { onManage("trips") },
                    )
                }
            }
            if (state.pendingDuplicates > 0) {
                item {
                    val n = state.pendingDuplicates
                    Banner(
                        icon = Icons.Filled.Warning,
                        title = "$n possible duplicate payment${if (n == 1) "" else "s"}",
                        body = "Check with your bank: were you charged twice, or was the SMS just repeated?",
                        action = "Check",
                        onClick = onDuplicates,
                        danger = true,
                    )
                }
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
                item { SplitCards(allMonth, base, hidden, toClaim = state.toClaim, onClaims = onClaims) }
                val owed = state.owedToMe
                if (owed.isNotEmpty()) {
                    item {
                        val people = owed.map { it.split.person.trim().lowercase() }.distinct().size
                        val byCurrency = owed.groupBy { it.currency }.map { (c, l) -> money(l.sumOf { it.split.amount }, c, hidden) }
                        Banner(
                            icon = Icons.Filled.Groups,
                            title = "Friends owe you ${byCurrency.joinToString(" + ")}",
                            body = "$people ${if (people == 1) "person hasn't" else "people haven't"} paid back yet",
                            action = "View",
                            onClick = { onManage("splits") },
                        )
                    }
                }
                if (state.accounts.isNotEmpty()) {
                    item { SectionTitle("Accounts & cards") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.accounts, key = { it.id }) { a -> AccountCard(a, hidden) }
                        }
                    }
                }
                val upcoming = state.upcoming(14)
                if (upcoming.isNotEmpty()) {
                    item {
                        SectionTitle("Coming up") {
                            TextButton(onClick = { onManage("upcoming") }) { Text("All") }
                        }
                    }
                    item {
                        AppCard(Modifier.padding(horizontal = 16.dp)) {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                upcoming.take(4).forEach { u -> UpcomingRow(u, hidden, onMarkBillPaid) }
                            }
                        }
                    }
                }
                val categoryBudgets = state.budgets.filter { it != state.overallBudget }
                if (categoryBudgets.isNotEmpty()) {
                    item {
                        SectionTitle("Budgets") {
                            TextButton(onClick = onBudgets) { Text("Manage") }
                        }
                    }
                    item {
                        AppCard(Modifier.padding(horizontal = 16.dp)) {
                            Column(Modifier.padding(vertical = 8.dp)) {
                                categoryBudgets.sortedByDescending { it.fraction }.take(4).forEach { b ->
                                    BudgetRow(b, base, hidden)
                                }
                            }
                        }
                    }
                }
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
private fun HeroCard(title: String, spent: String, income: String, net: String, budget: BudgetUi?, base: String, hidden: Boolean) {
    val paper = LocalStyle.current.isPaper
    // Calm Teal: a solid accent card. Paper Ledger: an outlined ledger card in ink.
    val bg = if (paper) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.primary
    val ink = if (paper) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
    val soft = ink.copy(alpha = 0.75f)
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(bg)
            .then(if (paper) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraLarge) else Modifier)
            .padding(22.dp),
    ) {
        Column {
            Text(if (paper) title.uppercase() else title, color = soft, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                spent,
                color = ink,
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = LocalStyle.current.numberFont),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (budget != null) {
                Spacer(Modifier.height(14.dp))
                Row {
                    Text("Monthly budget", color = soft, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    Text(
                        if (hidden) "${(budget.fraction * 100).toInt()}%" else "${(budget.fraction * 100).toInt()}% of ${Currencies.format(budget.budget.amount, base)}",
                        color = soft,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.height(6.dp))
                ProgressLine(
                    budget.fraction,
                    when {
                        budget.fraction >= 1f -> Color(0xFFFB7185)
                        budget.fraction >= 0.8f -> Color(0xFFFBBF24)
                        paper -> MaterialTheme.colorScheme.primary
                        else -> Color(0xFFFDE68A)
                    },
                    track = ink.copy(alpha = 0.18f),
                )
            }
            Spacer(Modifier.height(16.dp))
            if (paper) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(if (paper) 12.dp else 0.dp))
            Row(Modifier.fillMaxWidth()) {
                HeroStat(Icons.Filled.ArrowDownward, "Income", income, ink, Modifier.weight(1f))
                HeroStat(Icons.Filled.ArrowUpward, "Net", net, ink, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroStat(icon: ImageVector, label: String, value: String, ink: Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(ink.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = ink, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, color = ink.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
            Text(
                value,
                color = ink,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = LocalStyle.current.numberFont),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SplitCards(items: List<TxUi>, base: String, hidden: Boolean, toClaim: Double, onClaims: () -> Unit) {
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
        SplitCard(
            Icons.Filled.Business, "Corporate", money(corporate, base, hidden), (corporate / total).toFloat(), CorporateBlue, Modifier.weight(1f),
            footnote = if (toClaim > 0) "${money(toClaim, base, hidden)} to claim" else null,
            onClick = if (toClaim > 0) onClaims else null,
        )
    }
}

@Composable
private fun AccountCard(a: AccountEntity, hidden: Boolean) {
    AppCard(Modifier.width(180.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (a.isCreditLimit) Icons.Filled.CreditCard else Icons.Filled.AccountBalance, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    a.label + (a.last4?.let { " ••$it" } ?: ""),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                money(a.balance, a.currency, hidden),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = LocalStyle.current.numberFont),
                maxLines = 1,
            )
            Text(
                (if (a.isCreditLimit) "Available limit" else "Balance") + " · " + Dates.dayLabel(Dates.toDate(a.updatedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun UpcomingRow(u: Upcoming, hidden: Boolean, onMarkPaid: (BillEntity) -> Unit) {
    val days = u.date.toEpochDay() - java.time.LocalDate.now().toEpochDay()
    val whenText = when {
        days < 0 -> "Overdue by ${-days} day${if (days == -1L) "" else "s"}"
        days == 0L -> "Due today"
        days == 1L -> "Tomorrow"
        else -> "In $days days · ${Dates.dayLabel(u.date)}"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (u.bill != null) Icons.AutoMirrored.Filled.ReceiptLong else Icons.Filled.Autorenew, null,
            tint = if (days <= 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(u.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${u.kind} · $whenText",
                style = MaterialTheme.typography.bodySmall,
                color = if (days <= 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                money(u.amount, u.currency, hidden),
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = LocalStyle.current.numberFont),
            )
            if (u.bill != null) {
                TextButton(onClick = { onMarkPaid(u.bill) }) { Text("Mark paid") }
            }
        }
    }
}

@Composable
private fun BudgetRow(b: BudgetUi, base: String, hidden: Boolean) {
    val over = b.fraction >= 1f
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    hidden -> "${(b.fraction * 100).toInt()}%"
                    over -> "${Currencies.format(-b.left, base)} over"
                    else -> "${Currencies.format(b.left, base)} left"
                },
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = LocalStyle.current.numberFont),
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        ProgressLine(
            b.fraction,
            when {
                over -> MaterialTheme.colorScheme.error
                b.fraction >= 0.8f -> Color(0xFFD97706)
                else -> MaterialTheme.colorScheme.primary
            },
        )
    }
}

@Composable
private fun SplitCard(
    icon: ImageVector,
    label: String,
    value: String,
    fraction: Float,
    color: Color,
    modifier: Modifier,
    footnote: String? = null,
    onClick: (() -> Unit)? = null,
) {
    AppCard(modifier, onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = LocalStyle.current.numberFont),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            ProgressLine(fraction, color)
            if (footnote != null) {
                Spacer(Modifier.height(8.dp))
                Text(footnote, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun Banner(icon: ImageVector, title: String, body: String, action: String, onClick: () -> Unit, danger: Boolean = false) {
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
                    .background(if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon, null,
                    tint = if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
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
