@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.selfwallet.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.selfwallet.app.data.db.Claim
import com.selfwallet.app.data.db.TransactionEntity
import com.selfwallet.app.data.db.TxSource
import com.selfwallet.app.ui.TxUi
import com.selfwallet.app.ui.WalletState
import com.selfwallet.app.ui.components.EmptyState
import com.selfwallet.app.ui.components.TransactionRow
import com.selfwallet.app.ui.components.money
import com.selfwallet.app.ui.theme.CorporateBlue
import com.selfwallet.app.util.Dates
import kotlinx.coroutines.launch
import java.time.YearMonth

private val filters = listOf(
    "all" to "All",
    "expense" to "Expenses",
    "income" to "Income",
    "personal" to "Personal",
    "corporate" to "Corporate",
    "dups" to "Duplicates",
    "claims" to "To claim",
    "review" to "To review",
    "sms" to "From SMS",
)

@Composable
fun TransactionsScreen(
    state: WalletState,
    initialFilter: String?,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onDelete: (TransactionEntity) -> Unit,
    onUndoDelete: (TransactionEntity) -> Unit,
    onMarkReviewed: (List<TransactionEntity>) -> Unit,
    onToggleKind: (TxUi) -> Unit,
    onSetClaim: (List<TransactionEntity>, String) -> Unit,
    snackbar: SnackbarHostState,
) {
    var filter by rememberSaveable(initialFilter) { mutableStateOf(initialFilter ?: "all") }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    // null = all time
    var monthIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val month = monthIndex?.let { YearMonth.now().minusMonths(it.toLong()) }
    val scope = rememberCoroutineScope()
    val hidden = state.settings.hideAmounts

    val shown = remember(state.items, filter, query, monthIndex) {
        val q = query.trim().lowercase()
        state.items.asSequence()
            .filter { month == null || it.tx.timestamp in Dates.monthRange(month) }
            .filter {
                when (filter) {
                    "expense" -> it.isExpense
                    "income" -> !it.isExpense
                    "personal" -> !it.isCorporate
                    "corporate" -> it.isCorporate
                    "review" -> it.tx.needsReview
                    "dups" -> it.isPendingDuplicate
                    "claims" -> it.awaitingClaim
                    "sms" -> it.tx.source == TxSource.SMS
                    else -> true
                }
            }
            .filter {
                q.isEmpty() || it.tx.merchant.lowercase().contains(q) || it.tx.note.lowercase().contains(q) ||
                    (it.category?.name?.lowercase()?.contains(q) ?: false) ||
                    (it.allocation?.name?.lowercase()?.contains(q) ?: false) ||
                    it.tx.amount.toString().contains(q)
            }
            .toList()
    }
    val groups = remember(shown) { shown.groupBy { Dates.toDate(it.tx.timestamp) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search merchant, note, category") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                        )
                    } else {
                        Text("Activity")
                    }
                },
                actions = {
                    if (searching) {
                        IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.Filled.Close, "Close search") }
                    } else {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, "Search") }
                    }
                    if (filter == "claims" && shown.isNotEmpty()) {
                        IconButton(onClick = { onSetClaim(shown.map { it.tx }, Claim.SUBMITTED) }) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Mark all as submitted")
                        }
                        IconButton(onClick = { onSetClaim(shown.map { it.tx }, Claim.REIMBURSED) }) {
                            Icon(Icons.Filled.Paid, "Mark all as paid back")
                        }
                    }
                    if (filter == "review" && shown.isNotEmpty()) {
                        IconButton(onClick = { onMarkReviewed(shown.map { it.tx }) }) { Icon(Icons.Filled.DoneAll, "Mark all reviewed") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Filled.Add, "Add transaction")
            }
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filters.filter { it.first != "dups" || state.pendingDuplicates > 0 || filter == "dups" }.forEach { (key, label) ->
                    val count = when (key) {
                        "review" -> state.pendingReview
                        "dups" -> state.pendingDuplicates
                        else -> 0
                    }
                    FilterChip(
                        selected = filter == key,
                        onClick = { filter = key },
                        label = { Text(if (count > 0) "$label ($count)" else label) },
                    )
                }
            }
            MonthSwitcher(
                label = month?.let { Dates.monthLabel(it) } ?: "All time",
                onPrev = { monthIndex = (monthIndex ?: -1) + 1 },
                onNext = { monthIndex = monthIndex?.let { if (it <= 0) null else it - 1 } },
                canNext = monthIndex != null,
                summary = if (shown.isEmpty()) "" else {
                    val out = shown.filter { it.isExpense }.sumOf { it.base }
                    val inc = shown.filter { !it.isExpense }.sumOf { it.base }
                    buildString {
                        append("Out ${money(out, state.base, hidden)}")
                        if (inc > 0) append("  ·  In ${money(inc, state.base, hidden)}")
                    }
                },
            )

            if (shown.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.SearchOff,
                    title = when (filter) {
                        "review" -> "Nothing to review"
                        "dups" -> "No duplicates"
                        else -> "No transactions"
                    },
                    body = when (filter) {
                        "review" -> "Every SMS transaction has a category. Nice!"
                        "dups" -> "No repeated SMS or double charges waiting for you."
                        else -> "Try another filter or month, or add one with +."
                    },
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    groups.forEach { (date, list) ->
                        stickyHeader(key = "h$date") {
                            val dayOut = list.filter { it.isExpense }.sumOf { it.base }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    Dates.dayLabel(date),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                if (dayOut > 0) {
                                    Text(
                                        money(dayOut, state.base, hidden),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        items(list, key = { it.tx.id }) { item ->
                            SwipeRow(
                                item = item,
                                hidden = hidden,
                                onOpen = { onOpen(item.tx.id) },
                                onToggleKind = { onToggleKind(item) },
                                onDelete = {
                                    onDelete(item.tx)
                                    scope.launch {
                                        val r = snackbar.showSnackbar(
                                            "Transaction deleted",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (r == SnackbarResult.ActionPerformed) onUndoDelete(item.tx)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSwitcher(label: String, onPrev: () -> Unit, onNext: () -> Unit, canNext: Boolean, summary: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, "Previous month") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (summary.isNotEmpty()) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onNext, enabled = canNext) { Icon(Icons.Filled.ChevronRight, "Next month") }
    }
}

@Composable
private fun SwipeRow(item: TxUi, hidden: Boolean, onOpen: () -> Unit, onToggleKind: () -> Unit, onDelete: () -> Unit) {
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Swipe right flips Personal <-> Corporate; the row springs back.
                    onToggleKind()
                    false
                }
                else -> false
            }
        },
    )
    SwipeToDismissBox(
        state = dismiss,
        backgroundContent = {
            if (dismiss.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                val toCorporate = !item.isCorporate
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(if (toCorporate) CorporateBlue.copy(alpha = 0.18f) else MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (toCorporate) Icons.Filled.Business else Icons.Filled.Person, null,
                            tint = if (toCorporate) CorporateBlue else MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (toCorporate) "Mark as Corporate" else "Mark as Personal",
                            color = if (toCorporate) CorporateBlue else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            TransactionRow(item, hidden, onClick = onOpen)
        }
    }
}
