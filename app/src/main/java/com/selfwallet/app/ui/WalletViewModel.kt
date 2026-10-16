package com.selfwallet.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.selfwallet.app.SelfWalletApp
import com.selfwallet.app.backup.BackupFile
import com.selfwallet.app.data.DuplicateCheck
import com.selfwallet.app.backup.BackupScheduler
import com.selfwallet.app.backup.CsvExporter
import com.selfwallet.app.data.db.AllocationEntity
import com.selfwallet.app.data.db.CategoryEntity
import com.selfwallet.app.data.db.RateEntity
import com.selfwallet.app.data.db.RuleEntity
import com.selfwallet.app.data.db.SpendKind
import com.selfwallet.app.data.db.TransactionEntity
import com.selfwallet.app.data.db.TxType
import com.selfwallet.app.data.prefs.Settings
import com.selfwallet.app.sms.SmsImporter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TxUi(
    val tx: TransactionEntity,
    val category: CategoryEntity?,
    val allocation: AllocationEntity?,
    /** Amount converted to the base currency. */
    val base: Double,
) {
    val isExpense get() = tx.type == TxType.EXPENSE
    val isCorporate get() = allocation?.kind == SpendKind.CORPORATE
    /** Flagged as a possible duplicate and waiting for the user to decide. */
    val isPendingDuplicate get() = tx.dupStatus == DuplicateCheck.PENDING
}

data class WalletState(
    val loaded: Boolean = false,
    val settings: Settings = Settings(),
    val items: List<TxUi> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val allocations: List<AllocationEntity> = emptyList(),
    val rules: List<RuleEntity> = emptyList(),
    val rates: List<RateEntity> = emptyList(),
    /** Currencies used by transactions that have no exchange rate to the base currency yet. */
    val missingRates: List<String> = emptyList(),
) {
    val base get() = settings.baseCurrency
    val pendingReview get() = items.count { it.tx.needsReview }
    val pendingDuplicates get() = items.count { it.isPendingDuplicate }
    fun rateOf(code: String): Double =
        if (code == base) 1.0 else rates.firstOrNull { it.currency == code }?.rate ?: 1.0
}

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
}

class WalletViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as SelfWalletApp).container
    private val repo = container.repository
    private val backup = container.backup

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events

    private val lists = combine(repo.categories, repo.allocations, repo.rules, repo.rates) { c, a, r, x ->
        Lists(c, a, r, x)
    }

    val state: StateFlow<WalletState> =
        combine(repo.settings.settings, repo.transactions, lists) { s, txs, l ->
            val cats = l.categories.associateBy { it.id }
            val allocs = l.allocations.associateBy { it.id }
            val rates = l.rates.associateBy { it.currency }
            val missing = mutableSetOf<String>()
            val items = txs.map { t ->
                val rate = when {
                    t.currency == s.baseCurrency -> 1.0
                    rates[t.currency] != null -> rates.getValue(t.currency).rate
                    else -> { missing += t.currency; 1.0 }
                }
                // An unconfirmed repeat SMS is listed but not counted in any total.
                val counted = if (DuplicateCheck.isUncounted(t)) 0.0 else t.amount * rate
                TxUi(t, t.categoryId?.let { cats[it] }, t.allocationId?.let { allocs[it] }, counted)
            }
            WalletState(
                loaded = true,
                settings = s,
                items = items,
                categories = l.categories,
                allocations = l.allocations,
                rules = l.rules,
                rates = l.rates,
                missingRates = missing.sorted(),
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, WalletState())

    private data class Lists(
        val categories: List<CategoryEntity>,
        val allocations: List<AllocationEntity>,
        val rules: List<RuleEntity>,
        val rates: List<RateEntity>,
    )

    init {
        viewModelScope.launch { repo.ensureSeeded() }
    }

    private fun toast(text: String) { _events.tryEmit(UiEvent.Message(text)) }

    // ---- transactions -------------------------------------------------------------------

    suspend fun loadTransaction(id: Long): TransactionEntity? = repo.getTransaction(id)

    fun saveTransaction(tx: TransactionEntity, learnFrom: String?) = viewModelScope.launch {
        repo.saveTransaction(tx.copy(needsReview = false))
        if (!learnFrom.isNullOrBlank()) {
            repo.learnRule(learnFrom, tx.categoryId, tx.allocationId)
            val n = repo.applyRulesToPending()
            if (n > 0) toast("Rule saved · $n more transaction${if (n == 1) "" else "s"} categorised")
        }
    }

    fun deleteTransaction(tx: TransactionEntity) = viewModelScope.launch { repo.deleteTransaction(tx) }
    fun restoreTransaction(tx: TransactionEntity) = viewModelScope.launch { repo.saveTransaction(tx) }

    /** Flips a transaction between Personal and Corporate. */
    fun toggleSpendKind(item: TxUi) = viewModelScope.launch {
        val kind = if (item.isCorporate) SpendKind.PERSONAL else SpendKind.CORPORATE
        repo.setSpendKind(item.tx.id, kind)?.let {
            toast("Moved to ${it.name} · ${if (kind == SpendKind.CORPORATE) "Corporate" else "Personal"}")
        }
    }

    suspend fun destinationFor(kind: String): AllocationEntity = repo.destinationFor(kind)

    fun resolveDuplicate(tx: TransactionEntity, keep: Boolean) = viewModelScope.launch {
        repo.resolveDuplicate(tx.id, keep)
        toast(if (keep) "Kept · counted in your totals" else "Duplicate removed")
    }

    fun markReviewed(items: List<TransactionEntity>) = viewModelScope.launch {
        items.forEach { repo.saveTransaction(it.copy(needsReview = false)) }
    }

    // ---- organise -----------------------------------------------------------------------

    fun saveCategory(c: CategoryEntity) = viewModelScope.launch { repo.saveCategory(c) }
    fun deleteCategory(c: CategoryEntity) = viewModelScope.launch { repo.deleteCategory(c) }
    fun saveAllocation(a: AllocationEntity) = viewModelScope.launch { repo.saveAllocation(a) }
    fun deleteAllocation(a: AllocationEntity) = viewModelScope.launch { repo.deleteAllocation(a) }
    fun saveRule(r: RuleEntity) = viewModelScope.launch {
        repo.saveRule(r)
        val n = repo.applyRulesToPending()
        if (n > 0) toast("$n pending transaction${if (n == 1) "" else "s"} categorised")
    }
    fun deleteRule(r: RuleEntity) = viewModelScope.launch { repo.deleteRule(r) }
    fun saveRate(r: RateEntity) = viewModelScope.launch { repo.saveRate(r) }
    fun deleteRate(r: RateEntity) = viewModelScope.launch { repo.deleteRate(r) }

    // ---- settings -----------------------------------------------------------------------

    private val settings get() = repo.settings

    fun completeOnboarding() = viewModelScope.launch { settings.setOnboardingDone(true) }
    fun setBaseCurrency(code: String) = viewModelScope.launch { settings.setBaseCurrency(code) }
    fun setThemeMode(mode: String) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setDynamicColor(on: Boolean) = viewModelScope.launch { settings.setDynamicColor(on) }
    fun setSmsAutoImport(on: Boolean) = viewModelScope.launch { settings.setSmsAutoImport(on) }
    fun setSmsNotify(on: Boolean) = viewModelScope.launch { settings.setSmsNotify(on) }
    fun setDefaultAllocation(id: Long) = viewModelScope.launch { settings.setDefaultAllocation(id) }
    fun setHideAmounts(on: Boolean) = viewModelScope.launch { settings.setHideAmounts(on) }
    fun setAutoBackup(on: Boolean) = viewModelScope.launch {
        settings.setAutoBackup(on)
        if (on) BackupScheduler.ensureDaily(getApplication())
    }

    // ---- SMS ----------------------------------------------------------------------------

    fun importInbox(days: Int) = viewModelScope.launch {
        val since = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        runCatching { SmsImporter.importInbox(getApplication(), repo, since) }
            .onSuccess { toast("Scanned ${it.scanned} messages · added ${it.added} transaction${if (it.added == 1) "" else "s"}") }
            .onFailure { toast("Couldn't read SMS: ${it.message}") }
    }

    // ---- backup -------------------------------------------------------------------------

    /** User picked (or created) a file to keep the backup in. */
    fun linkBackupFile(uri: Uri, writeNow: Boolean) = viewModelScope.launch {
        settings.current().backupUri?.let { old -> if (old != uri.toString()) backup.release(Uri.parse(old)) }
        backup.persist(uri)
        settings.setBackupTarget(uri.toString(), backup.describe(uri))
        BackupScheduler.ensureDaily(getApplication())
        if (writeNow) backupNow()
    }

    fun unlinkBackupFile() = viewModelScope.launch {
        settings.current().backupUri?.let { backup.release(Uri.parse(it)) }
        settings.setBackupTarget(null, null)
        toast("Data now stays only on this device")
    }

    fun backupNow() = viewModelScope.launch {
        val uri = settings.current().backupUri ?: run { toast("Choose a backup location first"); return@launch }
        runCatching { backup.writeTo(Uri.parse(uri)) }
            .onSuccess { toast("Backup saved") }
            .onFailure { toast("Backup failed: ${it.message ?: "permission lost, choose the file again"}") }
    }

    suspend fun inspectBackup(uri: Uri): Result<BackupFile> = runCatching { backup.inspect(uri) }

    fun restore(file: BackupFile) = viewModelScope.launch {
        runCatching { backup.restore(file) }
            .onSuccess { toast("Restored ${file.transactions.size} transactions") }
            .onFailure { toast("Restore failed: ${it.message}") }
    }

    fun exportCsv(uri: Uri, rows: List<TxUi>) = viewModelScope.launch {
        val s = state.value
        runCatching {
            CsvExporter.write(
                getApplication(), uri, rows.map { it.tx },
                s.categories.associateBy { it.id }, s.allocations.associateBy { it.id },
                s.base, s::rateOf,
            )
        }.onSuccess { toast("Exported ${rows.size} rows") }
            .onFailure { toast("Export failed: ${it.message}") }
    }
}
