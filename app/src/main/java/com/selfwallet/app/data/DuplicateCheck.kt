package com.selfwallet.app.data

import com.selfwallet.app.data.db.TransactionEntity
import kotlin.math.abs

/**
 * Decides whether an incoming SMS transaction duplicates one we already have.
 *
 * - SAME_SMS: the exact same message text arrived again at a different time. Usually the bank
 *   re-sent the SMS, but it can also mean the card was charged twice. Not counted in totals
 *   until the user confirms.
 * - SIMILAR: a different message for the same amount, currency and merchant/card within
 *   [SIMILAR_WINDOW_MS]. Could be a real double deduction. Counted, but flagged for review.
 */
object DuplicateCheck {
    const val SAME_SMS = "SAME_SMS"
    const val SIMILAR = "SIMILAR"
    const val PENDING = "PENDING"
    const val KEEP = "KEEP"

    /** Same text within this distance is the same SMS seen twice (e.g. live + inbox scan). */
    const val SAME_MESSAGE_TOLERANCE_MS = 5_000L
    const val SIMILAR_WINDOW_MS = 15 * 60_000L

    sealed interface Verdict {
        /** We already stored exactly this message. Ignore it. */
        data object AlreadyStored : Verdict
        data object New : Verdict
        data class Duplicate(val of: TransactionEntity, val kind: String) : Verdict
    }

    /**
     * @param sameText transactions stored from SMS with exactly the same sender + text.
     * @param nearby transactions of the same type/currency/amount within [SIMILAR_WINDOW_MS].
     */
    fun classify(
        timestamp: Long,
        merchant: String,
        account: String?,
        sameText: List<TransactionEntity>,
        nearby: List<TransactionEntity>,
    ): Verdict {
        if (sameText.any { abs(it.timestamp - timestamp) <= SAME_MESSAGE_TOLERANCE_MS }) return Verdict.AlreadyStored
        sameText.minByOrNull { abs(it.timestamp - timestamp) }?.let { return Verdict.Duplicate(it, SAME_SMS) }

        val match = nearby
            .filter { abs(it.timestamp - timestamp) <= SIMILAR_WINDOW_MS }
            .filter { other ->
                val sameMerchant = merchant.isNotBlank() && other.merchant.equals(merchant, ignoreCase = true)
                val sameAccount = !account.isNullOrBlank() && other.account == account
                sameMerchant || sameAccount
            }
            .minByOrNull { abs(it.timestamp - timestamp) }
        return if (match != null) Verdict.Duplicate(match, SIMILAR) else Verdict.New
    }

    /** True while an exact repeat SMS is waiting for the user: it is not counted in totals. */
    fun isUncounted(tx: TransactionEntity) = tx.dupKind == SAME_SMS && tx.dupStatus == PENDING
}
