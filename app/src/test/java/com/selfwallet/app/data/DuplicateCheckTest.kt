package com.selfwallet.app.data

import com.selfwallet.app.data.DuplicateCheck.Verdict
import com.selfwallet.app.data.db.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateCheckTest {
    private val t0 = 1_760_000_000_000L
    private fun tx(id: Long, at: Long, merchant: String = "Carrefour Dubai", account: String? = "5678") =
        TransactionEntity(id = id, amount = 45.5, currency = "AED", merchant = merchant, account = account, timestamp = at)

    @Test
    fun sameSmsSeenAgainAtSameTimeIsIgnored() {
        val v = DuplicateCheck.classify(t0 + 2_000, "Carrefour Dubai", "5678", sameText = listOf(tx(1, t0)), nearby = emptyList())
        assertEquals(Verdict.AlreadyStored, v)
    }

    @Test
    fun sameSmsLaterIsFlagged() {
        val v = DuplicateCheck.classify(t0 + 60_000, "Carrefour Dubai", "5678", sameText = listOf(tx(1, t0)), nearby = emptyList())
        assertTrue(v is Verdict.Duplicate)
        assertEquals(DuplicateCheck.SAME_SMS, (v as Verdict.Duplicate).kind)
        assertEquals(1L, v.of.id)
    }

    @Test
    fun sameAmountSameMerchantWithinWindowIsSimilar() {
        val v = DuplicateCheck.classify(t0 + 3 * 60_000, "CARREFOUR DUBAI", null, emptyList(), nearby = listOf(tx(7, t0)))
        assertEquals(DuplicateCheck.SIMILAR, (v as Verdict.Duplicate).kind)
    }

    @Test
    fun sameCardDifferentMerchantNameIsSimilar() {
        val v = DuplicateCheck.classify(t0 + 60_000, "Carrefour", "5678", emptyList(), nearby = listOf(tx(7, t0)))
        assertTrue(v is Verdict.Duplicate)
    }

    @Test
    fun differentMerchantAndCardIsNew() {
        val v = DuplicateCheck.classify(t0 + 60_000, "Lulu", "1111", emptyList(), nearby = listOf(tx(7, t0)))
        assertEquals(Verdict.New, v)
    }

    @Test
    fun outsideWindowIsNew() {
        val v = DuplicateCheck.classify(t0 + 20 * 60_000, "Carrefour Dubai", "5678", emptyList(), nearby = listOf(tx(7, t0)))
        assertEquals(Verdict.New, v)
    }
}
