package com.selfwallet.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.selfwallet.app.SelfWalletApp
import com.selfwallet.app.backup.BackupFile
import com.selfwallet.app.backup.PdfReport
import com.selfwallet.app.data.Budgets
import com.selfwallet.app.data.Insights
import com.selfwallet.app.data.db.AccountEntity
import com.selfwallet.app.data.db.BillEntity
import com.selfwallet.app.data.db.SplitEntity
import com.selfwallet.app.data.db.TemplateEntity
import com.selfwallet.app.data.db.TripEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import com.selfwallet.app.data.DuplicateCheck
import com.selfwallet.app.data.db.BudgetEntity
import com.selfwallet.app.data.db.Claim
import com.selfwallet.app.util.Dates
import java.time.YearMonth
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
    /** Corporate expense the company hasn't paid back yet. */
    val awaitingClaim get() = isExpense && isCorporate && Claim.of(tx) != Claim.REIMBURSED
}

/** A budget with this month's progress. */
data class BudgetUi(
    val budget: BudgetEntity,
    val name: String,
    val spent: Double,
) {
    val fraction: Float get() = if (budget.amount > 0) (spent / budget.amount).toFloat() else 0f
    val left: Double get() = budget.amount - spent
}

data class WalletState(
    val loaded: Boolean = false,
    val settings: Settings = Settings(),
    val items: List<TxUi> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val allocations: List<AllocationEntity> = emptyList(),
    val rules: List<RuleEntity> = emptyList(),
    val rates: List<RateEntity> = emptyList(),
    val budgets: List<BudgetUi> = emptyList(),
    /** Currencies used by transactions that have no exchange rate to the base currency yet. */
    val missingRates: List<String> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val bills: List<BillEntity> = emptyList(),
    val splits: List<SplitUi> = emptyList(),
    val trips: List<TripEntity> = emptyList(),
    val templates: List<TemplateEntity> = emptyList(),
    val recurring: List<Insights.Recurring> = emptyList(),
) {
    /** Unpaid bills and recurring payments due in the next [days] days (and overdue bills). */
    fun upcoming(days: Long = 30): List<Upcoming> {
        val today = LocalDate.now()
        val limit = today.plusDays(days)
        val fromBills = bills.filter { !it.paid && LocalDate.ofEpochDay(it.dueDay) <= limit }
            .map { Upcoming(it.title, it.amount, it.currency, LocalDate.ofEpochDay(it.dueDay), "Bill", it) }
        val fromRecurring = recurring.filter { !it.next.isBefore(today) && it.next <= limit }
            .map { Upcoming(it.merchant, it.amount, it.currency, it.next, it.label, null) }
        return (fromBills + fromRecurring).sortedBy { it.date }
    }
    val owedToMe get() = splits.filter { !it.split.settled }
    val activeTrip get() = LocalDate.now().toEpochDay().let { d -> trips.firstOrNull { d in it.startDay..it.endDay } }
    val base get() = settings.baseCurrency
    val pendingReview get() = items.count { it.tx.needsReview }
    val pendingDuplicates get() = items.count { it.isPendingDuplicate }
    val toClaim get() = items.filter { it.awaitingClaim }.sumOf { it.base }
    /** The overall monthly budget (all categories, all spending), if one is set. */
    val overallBudget get() = budgets.firstOrNull { it.budget.categoryId == null && it.budget.kind == Budgets.ALL }
    fun rateOf(code: String): Double =
        if (code == base) 1.0 else rates.firstOrNull { it.currency == code }?.rate ?: 1.0
}

/** One person's share of an expense, with the expense it belongs to. */
data class SplitUi(val split: SplitEntity, val tx: TxUi?) {
    val currency get() = tx?.tx?.currency ?: ""
}

/** A bill or predicted recurring payment that is coming up. */
data class Upcoming(
    val title: String,
    val amount: Double,
    val currency: String,
    val date: LocalDate,
    val kind: String,
    val bill: BillEntity?,
)

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
}

class WalletViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as SelfWalletApp).container
    private val repo = container.repository
    private val backup = container.backup

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events

    private val lists = combine(repo.categories, repo.allocations, repo.rules, repo.rates, repo.budgets) { c, a, r, x, b ->
        Lists(c, a, r, x, b)
    }

    private val extras = combine(repo.accounts, repo.bills, repo.splits, repo.trips, repo.templates) { a, b, sp, t, tm ->
        Extras(a, b, sp, t, tm)
    }

    private data class Extras(
        val accounts: List<AccountEntity>,
        val bills: List<BillEntity>,
        val splits: List<SplitEntity>,
        val trips: List<TripEntity>,
        val templates: List<TemplateEntity>,
    )

    /** A colleague's backup opened read-only (Settings › View a shared ledger). */
    private val _shared = MutableStateFlow<BackupFile?>(null)
    val shared: StateFlow<BackupFile?> = _shared

    val state: StateFlow<WalletState> =
        combine(repo.settings.settings, repo.transactions, lists, extras) { s, txs, l, x ->
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
                budgets = budgetProgress(l.budgets, items, cats),
                missingRates = missing.sorted(),
                accounts = x.accounts,
                bills = x.bills,
                splits = items.associateBy { it.tx.id }.let { byId -> x.splits.map { SplitUi(it, byId[it.txId]) } },
                trips = x.trips,
                templates = x.templates,
                recurring = Insights.recurring(
                    items.filter { it.isExpense && it.base > 0 }
                        .map { Insights.Payment(it.tx.merchant, it.tx.amount, it.tx.currency, Dates.toDate(it.tx.timestamp)) },
                ),
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, WalletState())

    private data class Lists(
        val categories: List<CategoryEntity>,
        val allocations: List<AllocationEntity>,
        val rules: List<RuleEntity>,
        val rates: List<RateEntity>,
        val budgets: List<BudgetEntity>,
    )

    init {
        viewModelScope.launch { repo.ensureSeeded() }
    }

    private fun budgetProgress(budgets: List<BudgetEntity>, items: List<TxUi>, cats: Map<Long, CategoryEntity>): List<BudgetUi> {
        if (budgets.isEmpty()) return emptyList()
        val range = Dates.monthRange(YearMonth.now())
        val lines = items.filter { it.isExpense && it.tx.timestamp in range }
            .map { Budgets.Line(it.base, it.tx.categoryId, it.isCorporate) }
        return budgets.map { b ->
            BudgetUi(b, Budgets.name(b, b.categoryId?.let { cats[it]?.name }), Budgets.spent(b, lines))
        }
    }

    private fun toast(text: String) { _events.tryEmit(UiEvent.Message(text)) }

    // ---- transactions -------------------------------------------------------------------

    suspend fun loadTransaction(id: Long): TransactionEntity? = repo.getTransaction(id)

    fun saveTransaction(tx: TransactionEntity, learnFrom: String?, splits: List<SplitEntity>? = null) = viewModelScope.launch {
        val id = repo.saveTransaction(tx.copy(needsReview = false))
        if (splits != null) repo.saveSplits(id, splits)
        if (!learnFrom.isNullOrBlank()) {
            repo.learnRule(learnFrom, tx.categoryId, tx.allocationId)
            val n = repo.applyRulesToPending()
            if (n > 0) toast("Rule saved · $n more transaction${if (n == 1) "" else "s"} categorised")
        }
    }

    fun deleteTransaction(tx: TransactionEntity) = viewModelScope.launch { repo.deleteTransaction(tx) }

    suspend fun loadSplits(txId: Long): List<SplitEntity> = repo.splitsFor(txId)
    fun setSplitSettled(split: SplitEntity, settled: Boolean) = viewModelScope.launch { repo.setSplitSettled(split, settled) }

    fun saveTrip(t: TripEntity) = viewModelScope.launch { repo.saveTrip(t); toast("Trip saved · matching expenses tagged") }
    fun deleteTrip(t: TripEntity) = viewModelScope.launch { repo.deleteTrip(t) }
    fun saveTemplate(t: TemplateEntity) = viewModelScope.launch { repo.saveTemplate(t); toast("Template “${t.name}” saved") }
    fun deleteTemplate(t: TemplateEntity) = viewModelScope.launch { repo.deleteTemplate(t) }
    fun saveBill(b: BillEntity) = viewModelScope.launch { repo.saveBill(b) }
    fun deleteBill(b: BillEntity) = viewModelScope.launch { repo.deleteBill(b) }
    fun deleteAccount(a: AccountEntity) = viewModelScope.launch { repo.deleteAccount(a) }
    fun setSmartAlerts(on: Boolean) = viewModelScope.launch { repo.settings.setSmartAlerts(on) }
    fun setReminders(on: Boolean) = viewModelScope.launch { repo.settings.setReminders(on) }

    fun openShared(uri: Uri) = viewModelScope.launch {
        runCatching { backup.inspect(uri) }
            .onSuccess { _shared.value = it }
            .onFailure { toast("That file isn't a Self Wallet backup") }
    }
    fun closeShared() { _shared.value = null }
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
    fun setAppStyle(style: String) = viewModelScope.launch { settings.setAppStyle(style) }
    fun setAppLock(on: Boolean) = viewModelScope.launch { settings.setAppLock(on) }
    fun setOwnerName(name: String) = viewModelScope.launch { settings.setOwnerName(name.trim()) }

    fun saveBudget(b: BudgetEntity) = viewModelScope.launch { repo.saveBudget(b) }
    fun deleteBudget(b: BudgetEntity) = viewModelScope.launch { repo.deleteBudget(b) }

    fun setClaimStatus(items: List<TransactionEntity>, status: String) = viewModelScope.launch {
        repo.setClaimStatus(items.map { it.id }, status)
        toast("${items.size} expense${if (items.size == 1) "" else "s"} marked ${Claim.label(status).lowercase()}")
    }
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

    fun exportPdf(uri: Uri, info: PdfReport.Info, rows: List<TxUi>, markSubmitted: Boolean) = viewModelScope.launch {
        runCatching { PdfReport.write(getApplication(), uri, info, rows) }
            .onSuccess {
                if (markSubmitted) {
                    val pending = rows.filter { it.isExpense && it.isCorporate && Claim.of(it.tx) == Claim.NOT_CLAIMED }
                    if (pending.isNotEmpty()) repo.setClaimStatus(pending.map { it.tx.id }, Claim.SUBMITTED)
                }
                toast("PDF report saved")
            }
            .onFailure { toast("PDF export failed: ${it.message}") }
    }
}
