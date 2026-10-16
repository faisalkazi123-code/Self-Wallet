@file:OptIn(ExperimentalMaterial3Api::class)

package com.selfwallet.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfwallet.app.data.DuplicateCheck
import com.selfwallet.app.data.db.SpendKind
import com.selfwallet.app.data.db.TxType
import com.selfwallet.app.ui.WalletViewModel
import com.selfwallet.app.ui.components.AppCard
import com.selfwallet.app.ui.components.EmptyState
import com.selfwallet.app.ui.components.SectionTitle
import com.selfwallet.app.util.Currencies
import com.selfwallet.app.util.Dates
import java.time.YearMonth

/**
 * Read-only view of someone else's Self Wallet backup (e.g. a colleague's corporate ledger
 * shared through OneDrive or Google Drive). Nothing is imported into this phone's data.
 */
@Composable
fun SharedLedgerScreen(vm: WalletViewModel, onBack: () -> Unit) {
    val file by vm.shared.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Shared ledger") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        val f = file
        if (f == null) {
            EmptyState(Icons.Filled.Visibility, "Opening…", "Pick a Self Wallet backup file to view it here.", Modifier.padding(inner))
            return@Scaffold
        }
        val rates = remember(f) { f.rates.associate { it.currency to it.rate } }
        val cats = remember(f) { f.categories.associateBy { it.id } }
        val allocs = remember(f) { f.allocations.associateBy { it.id } }
        val txs = remember(f) {
            f.transactions.filter { !DuplicateCheck.isUncounted(it) }.sortedByDescending { it.timestamp }
        }
        fun base(amount: Double, cur: String) = amount * (if (cur == f.baseCurrency) 1.0 else rates[cur] ?: 1.0)
        val months = remember(f) {
            txs.filter { it.type == TxType.EXPENSE }
                .groupBy { YearMonth.from(Dates.toDate(it.timestamp)) }
                .map { (m, l) -> m to l.sumOf { base(it.amount, it.currency) } }
                .sortedByDescending { it.first }
                .take(6)
        }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                AppCard(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Read-only · nothing is merged into your data", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "${f.transactions.size} transactions · saved ${Dates.full(f.exportedAt)} · ${f.baseCurrency}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { SectionTitle("Spending by month") }
            items(months, key = { it.first.toString() }) { (m, total) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Text(Dates.monthLabel(m), modifier = Modifier.weight(1f))
                    Text(Currencies.format(total, f.baseCurrency), style = MaterialTheme.typography.titleSmall)
                }
            }
            item { SectionTitle("Transactions") }
            items(txs.take(300), key = { it.id }) { t ->
                val corporate = t.allocationId?.let { allocs[it] }?.kind == SpendKind.CORPORATE
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.merchant.ifBlank { t.categoryId?.let { cats[it] }?.name ?: "Transaction" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                Dates.dayLabel(Dates.toDate(t.timestamp)),
                                t.categoryId?.let { cats[it]?.name },
                                t.allocationId?.let { allocs[it]?.name },
                                if (corporate) "Corporate" else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        (if (t.type == TxType.INCOME) "+" else "−") + Currencies.format(t.amount, t.currency),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                HorizontalDivider(Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}
