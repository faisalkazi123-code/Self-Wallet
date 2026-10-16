package com.selfwallet.app.data

import androidx.room.withTransaction
import com.selfwallet.app.data.db.AllocationEntity
import com.selfwallet.app.data.db.AppDatabase
import com.selfwallet.app.data.db.BudgetEntity
import com.selfwallet.app.data.db.CategoryEntity
import com.selfwallet.app.data.db.RateEntity
import com.selfwallet.app.data.db.RuleEntity
import com.selfwallet.app.data.db.SpendKind
import com.selfwallet.app.data.db.TransactionEntity
import com.selfwallet.app.data.db.TxSource
import com.selfwallet.app.data.db.TxType
import com.selfwallet.app.data.prefs.SettingsStore
import com.selfwallet.app.sms.SmsParser
import com.selfwallet.app.util.Currencies
import com.selfwallet.app.util.Dates
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

class Repository(
    private val db: AppDatabase,
    val settings: SettingsStore,
    private val onDataChanged: () -> Unit,
    /** Shows a notification when a budget reaches 80% or 100% this month. */
    private val onBudgetAlert: (title: String, body: String) -> Unit = { _, _ -> },
) {
    val transactions: Flow<List<TransactionEntity>> = db.transactions().observeAll()
    val categories: Flow<List<CategoryEntity>> = db.categories().observeAll()
    val allocations: Flow<List<AllocationEntity>> = db.allocations().observeAll()
    val rules: Flow<List<RuleEntity>> = db.rules().observeAll()
    val rates: Flow<List<RateEntity>> = db.rates().observeAll()
    val budgets: Flow<List<BudgetEntity>> = db.budgets().observeAll()

    private val seedLock = Mutex()

    suspend fun ensureSeeded() {
        seedLock.withLock {
            if (db.categories().getAll().isEmpty()) {
                db.categories().insertAll(DefaultData.categories())
            }
            if (db.allocations().getAll().isEmpty()) {
                db.allocations().insertAll(DefaultData.allocations())
                val personal = db.allocations().getAll().firstOrNull { it.kind == SpendKind.PERSONAL }
                if (personal != null) settings.setDefaultAllocation(personal.id)
            }
        }
    }

    // ---- transactions -------------------------------------------------------------------

    suspend fun getTransaction(id: Long): TransactionEntity? = db.transactions().get(id)

    suspend fun saveTransaction(tx: TransactionEntity): Long {
        val id = db.transactions().upsert(tx)
        onDataChanged()
        if (tx.type == TxType.EXPENSE) checkBudgets()
        return if (tx.id != 0L) tx.id else id
    }

    suspend fun deleteTransaction(tx: TransactionEntity) {
        db.transactions().delete(tx)
        onDataChanged()
    }

    // ---- SMS ingestion ------------------------------------------------------------------

    /**
     * Parses an SMS and stores it if it is a new transaction.
     * Returns the stored transaction, or null when the SMS was not a transaction or was already stored.
     * A possible duplicate is still stored, with [TransactionEntity.dupStatus] = PENDING.
     */
    suspend fun ingestSms(sender: String, body: String, timestamp: Long, notifyChange: Boolean = true): TransactionEntity? {
        val parsed = SmsParser.parse(sender, body) ?: return null
        val hash = hash("$sender|${body.trim()}")
        val sameText = db.transactions().byHash(hash)

        ensureSeeded()
        val s = settings.current()
        val rules = db.rules().getAll()
        val categories = db.categories().getAll()
        val haystack = "$sender ${parsed.merchant.orEmpty()} $body".lowercase()
        val rule = rules.firstOrNull { it.keyword.isNotBlank() && haystack.contains(it.keyword.lowercase().trim()) }

        val type = if (parsed.kind == SmsParser.Kind.INCOME) TxType.INCOME else TxType.EXPENSE
        val fallbackCategory = if (type == TxType.INCOME) {
            categories.firstOrNull { it.type == TxType.INCOME }?.id
        } else {
            null
        }

        val merchant = rule?.renameTo?.takeIf { it.isNotBlank() } ?: parsed.merchant.orEmpty()
        val nearby = db.transactions().similar(
            type, parsed.currency, parsed.amount,
            timestamp - DuplicateCheck.SIMILAR_WINDOW_MS, timestamp + DuplicateCheck.SIMILAR_WINDOW_MS,
        )
        val verdict = DuplicateCheck.classify(timestamp, merchant, parsed.account, sameText, nearby)
        if (verdict is DuplicateCheck.Verdict.AlreadyStored) return null
        val dup = verdict as? DuplicateCheck.Verdict.Duplicate

        val tx = TransactionEntity(
            amount = parsed.amount,
            currency = parsed.currency,
            type = type,
            categoryId = rule?.categoryId ?: fallbackCategory,
            allocationId = rule?.allocationId ?: s.defaultAllocationId.takeIf { it != 0L },
            merchant = merchant,
            timestamp = timestamp,
            source = TxSource.SMS,
            account = parsed.account,
            smsSender = sender,
            smsBody = body,
            // Repeats of the same text need their own key because smsHash is unique.
            smsHash = if (dup?.kind == DuplicateCheck.SAME_SMS) "$hash#$timestamp" else hash,
            needsReview = rule?.categoryId == null && type == TxType.EXPENSE,
            duplicateOf = dup?.of?.id,
            dupKind = dup?.kind,
            dupStatus = if (dup != null) DuplicateCheck.PENDING else null,
            // A repeat inherits the original's category and destination.
        ).let { if (dup != null) it.copy(categoryId = it.categoryId ?: dup.of.categoryId, allocationId = dup.of.allocationId ?: it.allocationId) else it }
        val id = db.transactions().insert(tx)
        if (id <= 0) return null
        if (notifyChange) {
            onDataChanged()
            if (type == TxType.EXPENSE) checkBudgets()
        }
        return tx.copy(id = id)
    }

    fun notifyChanged() = onDataChanged()

    /** User decided about a flagged duplicate: keep it (counted) or delete it. */
    suspend fun resolveDuplicate(txId: Long, keep: Boolean) {
        val tx = db.transactions().get(txId) ?: return
        if (keep) db.transactions().upsert(tx.copy(dupStatus = DuplicateCheck.KEEP)) else db.transactions().delete(tx)
        onDataChanged()
    }

    /**
     * Returns the destination a transaction should use for [kind]: the SMS default when it
     * matches, otherwise the first one of that kind. A "Company" destination is created when the
     * user asks for corporate but has none.
     */
    suspend fun destinationFor(kind: String): AllocationEntity {
        val all = db.allocations().getAll()
        val defaultId = settings.current().defaultAllocationId
        all.firstOrNull { it.kind == kind && it.id == defaultId }?.let { return it }
        all.firstOrNull { it.kind == kind }?.let { return it }
        val created = AllocationEntity(
            name = if (kind == SpendKind.CORPORATE) "Company" else "Personal",
            kind = kind,
            color = if (kind == SpendKind.CORPORATE) 0xFF2563EB else 0xFF0F766E,
            sortOrder = all.size,
        )
        val id = db.allocations().upsert(created)
        onDataChanged()
        return created.copy(id = id)
    }

    /** Moves a transaction between personal and corporate. Returns the new destination. */
    suspend fun setSpendKind(txId: Long, kind: String): AllocationEntity? {
        val tx = db.transactions().get(txId) ?: return null
        val target = destinationFor(kind)
        db.transactions().upsert(tx.copy(allocationId = target.id))
        onDataChanged()
        return target
    }

    // ---- categories / allocations / rules / rates ----------------------------------------

    suspend fun saveCategory(c: CategoryEntity) { db.categories().upsert(c); onDataChanged() }
    suspend fun deleteCategory(c: CategoryEntity) {
        db.withTransaction {
            db.transactions().clearCategory(c.id)
            db.budgets().deleteForCategory(c.id)
            db.categories().delete(c)
        }
        onDataChanged()
    }

    suspend fun saveAllocation(a: AllocationEntity) { db.allocations().upsert(a); onDataChanged() }
    suspend fun deleteAllocation(a: AllocationEntity) {
        db.withTransaction {
            db.transactions().clearAllocation(a.id)
            db.allocations().delete(a)
        }
        onDataChanged()
    }

    suspend fun saveRule(r: RuleEntity) { db.rules().upsert(r); onDataChanged() }
    suspend fun deleteRule(r: RuleEntity) { db.rules().delete(r); onDataChanged() }

    suspend fun saveRate(r: RateEntity) { db.rates().upsert(r); onDataChanged() }

    suspend fun saveBudget(b: BudgetEntity) {
        // A changed limit should be able to alert again this month.
        db.budgets().upsert(b.copy(alertedMonth = null, alertedLevel = 0))
        onDataChanged()
        checkBudgets()
    }
    suspend fun deleteBudget(b: BudgetEntity) { db.budgets().delete(b); onDataChanged() }

    /** Sets the reimbursement status of several corporate expenses at once. */
    suspend fun setClaimStatus(ids: Collection<Long>, status: String) {
        db.withTransaction {
            ids.forEach { id -> db.transactions().get(id)?.let { db.transactions().upsert(it.copy(claimStatus = status)) } }
        }
        onDataChanged()
    }

    /** Notifies once per month per level (80% / 100%) for each budget. */
    suspend fun checkBudgets() {
        val budgets = db.budgets().getAll()
        if (budgets.isEmpty()) return
        val month = YearMonth.now()
        val range = Dates.monthRange(month)
        val base = settings.current().baseCurrency
        val rates = db.rates().getAll().associate { it.currency to it.rate }
        val allocations = db.allocations().getAll().associateBy { it.id }
        val categories = db.categories().getAll().associateBy { it.id }
        val lines = db.transactions().getAll()
            .filter { it.type == TxType.EXPENSE && it.timestamp in range && !DuplicateCheck.isUncounted(it) }
            .map {
                val rate = if (it.currency == base) 1.0 else rates[it.currency] ?: 1.0
                Budgets.Line(it.amount * rate, it.categoryId, it.allocationId?.let { a -> allocations[a] }?.kind == SpendKind.CORPORATE)
            }
        budgets.forEach { b ->
            val spent = Budgets.spent(b, lines)
            val level = Budgets.level(spent, b.amount)
            val already = if (b.alertedMonth == month.toString()) b.alertedLevel else 0
            if (level > already) {
                db.budgets().upsert(b.copy(alertedMonth = month.toString(), alertedLevel = level))
                val name = Budgets.name(b, b.categoryId?.let { categories[it]?.name })
                val limit = Currencies.format(b.amount, base)
                if (level >= 100) {
                    onBudgetAlert("Over budget: $name", "You've spent ${Currencies.format(spent, base)} of $limit this month.")
                } else {
                    onBudgetAlert("80% of budget used: $name", "${Currencies.format(spent, base)} of $limit spent · ${Currencies.format(b.amount - spent, base)} left this month.")
                }
            }
        }
    }
    suspend fun deleteRate(r: RateEntity) { db.rates().delete(r); onDataChanged() }

    /** Creates or updates a rule for [keyword] after the user categorised an SMS transaction. */
    suspend fun learnRule(keyword: String, categoryId: Long?, allocationId: Long?) {
        val k = keyword.trim()
        if (k.length < 3) return
        val existing = db.rules().getAll().firstOrNull { it.keyword.equals(k, ignoreCase = true) }
        db.rules().upsert(
            (existing ?: RuleEntity(keyword = k)).copy(categoryId = categoryId, allocationId = allocationId),
        )
    }

    /** Applies all rules to SMS transactions still waiting for review. Returns how many changed. */
    suspend fun applyRulesToPending(): Int {
        val rules = db.rules().getAll()
        if (rules.isEmpty()) return 0
        var changed = 0
        db.transactions().getAll().filter { it.needsReview }.forEach { tx ->
            val hay = "${tx.smsSender.orEmpty()} ${tx.merchant} ${tx.smsBody.orEmpty()}".lowercase()
            val rule = rules.firstOrNull { it.keyword.isNotBlank() && hay.contains(it.keyword.lowercase().trim()) }
            if (rule != null) {
                db.transactions().upsert(
                    tx.copy(
                        categoryId = rule.categoryId ?: tx.categoryId,
                        allocationId = rule.allocationId ?: tx.allocationId,
                        merchant = rule.renameTo?.takeIf { it.isNotBlank() } ?: tx.merchant,
                        needsReview = (rule.categoryId ?: tx.categoryId) == null,
                    ),
                )
                changed++
            }
        }
        if (changed > 0) onDataChanged()
        return changed
    }

    // ---- backup -------------------------------------------------------------------------

    suspend fun snapshot(): Snapshot = Snapshot(
        categories = db.categories().getAll(),
        allocations = db.allocations().getAll(),
        rules = db.rules().getAll(),
        rates = db.rates().getAll(),
        transactions = db.transactions().getAll(),
        budgets = db.budgets().getAll(),
    )

    suspend fun replaceAll(s: Snapshot) {
        db.withTransaction {
            db.transactions().deleteAll()
            db.rules().deleteAll()
            db.rates().deleteAll()
            db.categories().deleteAll()
            db.allocations().deleteAll()
            db.budgets().deleteAll()
            db.categories().insertAll(s.categories)
            db.allocations().insertAll(s.allocations)
            db.rules().insertAll(s.rules)
            db.rates().insertAll(s.rates)
            db.transactions().insertAll(s.transactions)
            db.budgets().insertAll(s.budgets)
        }
    }

    data class Snapshot(
        val categories: List<CategoryEntity>,
        val allocations: List<AllocationEntity>,
        val rules: List<RuleEntity>,
        val rates: List<RateEntity>,
        val transactions: List<TransactionEntity>,
        val budgets: List<BudgetEntity> = emptyList(),
    )

    private fun hash(s: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }
}
