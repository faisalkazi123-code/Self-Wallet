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
