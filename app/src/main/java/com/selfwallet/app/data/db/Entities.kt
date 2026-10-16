package com.selfwallet.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

object TxType {
    const val EXPENSE = "EXPENSE"
    const val INCOME = "INCOME"
}

object TxSource {
    const val MANUAL = "MANUAL"
    const val SMS = "SMS"
}

/** Where an expense is charged to: the user personally, or a company / client / project. */
object SpendKind {
    const val PERSONAL = "PERSONAL"
    const val CORPORATE = "CORPORATE"
}

@Serializable
@Entity(
    tableName = "transactions",
    indices = [Index("timestamp"), Index(value = ["smsHash"], unique = true)],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val currency: String,
    val type: String = TxType.EXPENSE,
    val categoryId: Long? = null,
    val allocationId: Long? = null,
    val merchant: String = "",
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = TxSource.MANUAL,
    val account: String? = null,
    val smsSender: String? = null,
    val smsBody: String? = null,
    val smsHash: String? = null,
    val needsReview: Boolean = false,
    /** Id of the transaction this one may duplicate (see DuplicateCheck). */
    val duplicateOf: Long? = null,
    /** DuplicateCheck.SAME_SMS or DuplicateCheck.SIMILAR. */
    val dupKind: String? = null,
    /** DuplicateCheck.PENDING until the user decides, then KEEP. */
    val dupStatus: String? = null,
    /** Absolute path of a receipt photo in the app's private storage. */
    val receiptPath: String? = null,
    /** Claim.* for corporate expenses; null means not claimed yet. */
    val claimStatus: String? = null,
    /** Travel-mode trip this expense belongs to. */
    val tripId: Long? = null,
)

/** Latest balance or available credit limit seen in a bank SMS for one account/card. */
@Serializable
@Entity(tableName = "accounts", indices = [Index(value = ["accountKey"], unique = true)])
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountKey: String,
    val label: String,
    val last4: String? = null,
    val balance: Double,
    val currency: String,
    val isCreditLimit: Boolean = false,
    val updatedAt: Long,
)

/** A bill / card payment that is due, read from a reminder SMS or added by hand. */
@Serializable
@Entity(tableName = "bills", indices = [Index(value = ["hash"], unique = true)])
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Double,
    val currency: String,
    /** Epoch day of the due date. */
    val dueDay: Long,
    val account: String? = null,
    val paid: Boolean = false,
    val hash: String,
    /** 0 none, 1 = "due soon" sent, 2 = "due today" sent. */
    val reminded: Int = 0,
)

/** Someone who owes you part of an expense. */
@Serializable
@Entity(tableName = "splits", indices = [Index("txId")])
data class SplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val txId: Long,
    val person: String,
    val amount: Double,
    val settled: Boolean = false,
)

/** Travel mode: expenses between [startDay] and [endDay] (epoch days) are tagged to this trip. */
@Serializable
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDay: Long,
    val endDay: Long,
    /** Optional destination applied to SMS expenses during the trip. */
    val allocationId: Long? = null,
)

/** A saved expense the user adds often ("Taxi to office", "Coffee"). */
@Serializable
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val currency: String,
    val type: String = TxType.EXPENSE,
    val categoryId: Long? = null,
    val allocationId: Long? = null,
    val merchant: String = "",
)

/** Reimbursement progress of a corporate expense. */
object Claim {
    const val NOT_CLAIMED = "NOT_CLAIMED"
    const val SUBMITTED = "SUBMITTED"
    const val REIMBURSED = "REIMBURSED"

    fun of(tx: TransactionEntity): String = tx.claimStatus ?: NOT_CLAIMED

    fun label(status: String) = when (status) {
        SUBMITTED -> "Submitted"
        REIMBURSED -> "Paid back"
        else -> "Not claimed"
    }
}

/**
 * Monthly spending limit in the base currency. [categoryId] null = all categories;
 * [kind] narrows it to personal or corporate spending ("ALL" = both).
 */
@Serializable
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long? = null,
    val kind: String = "ALL",
    val amount: Double,
    /** "2026-10": the month the last alert was sent for, with [alertedLevel] 80 or 100. */
    val alertedMonth: String? = null,
    val alertedLevel: Int = 0,
)

@Serializable
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val color: Long,
    val type: String = TxType.EXPENSE,
    val sortOrder: Int = 0,
)

/** A user defined destination for an expense ("Personal", "ACME Corp", "Client X trip"). */
@Serializable
@Entity(tableName = "allocations")
data class AllocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String = SpendKind.PERSONAL,
    val color: Long,
    val sortOrder: Int = 0,
)

/** Auto-categorisation rule: when an SMS / merchant contains [keyword], apply the targets. */
@Serializable
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val categoryId: Long? = null,
    val allocationId: Long? = null,
    val renameTo: String? = null,
)

/** 1 unit of [currency] equals [rate] units of the base currency. */
@Serializable
@Entity(tableName = "rates")
data class RateEntity(
    @PrimaryKey val currency: String,
    val rate: Double,
)
