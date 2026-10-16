package com.selfwallet.app.sms

import java.time.LocalDate

/**
 * Turns a bank / card / wallet SMS into a transaction. Pure Kotlin so it can be unit tested.
 *
 * The parser is deliberately conservative: a message must contain both a money amount
 * and a debit/credit verb, and must not look like an OTP, a promotion or a reminder.
 */
object SmsParser {

    enum class Kind { EXPENSE, INCOME }

    data class Result(
        val amount: Double,
        val currency: String,
        val kind: Kind,
        val merchant: String?,
        val account: String?,
        /** "Avl Bal" / "Avl Limit" reported in the same SMS, if any. */
        val balance: Double? = null,
        val balanceCurrency: String? = null,
        val balanceIsLimit: Boolean = false,
    )

    /** A card bill or loan payment that is due. */
    data class Due(
        val amount: Double,
        val currency: String,
        val dueDate: LocalDate,
        val account: String?,
    )

    // Alias -> ISO code. Short aliases are only accepted when glued to a number.
    private val currencyAliases: Map<String, String> = linkedMapOf(
        "inr" to "INR", "rs." to "INR", "rs" to "INR", "₹" to "INR",
        "usd" to "USD", "us$" to "USD", "$" to "USD",
        "eur" to "EUR", "€" to "EUR",
        "gbp" to "GBP", "£" to "GBP",
        "aed" to "AED", "dhs" to "AED", "dh" to "AED",
        "sar" to "SAR", "sr" to "SAR",
        "qar" to "QAR", "qr" to "QAR",
        "kwd" to "KWD", "kd" to "KWD",
        "bhd" to "BHD", "bd" to "BHD",
        "omr" to "OMR", "ro" to "OMR",
        "pkr" to "PKR",
        "bdt" to "BDT", "tk" to "BDT", "৳" to "BDT",
        "lkr" to "LKR",
        "npr" to "NPR",
        "egp" to "EGP", "le" to "EGP",
        "jod" to "JOD", "jd" to "JOD",
        "try" to "TRY", "₺" to "TRY",
        "jpy" to "JPY", "¥" to "JPY",
        "cny" to "CNY", "rmb" to "CNY",
        "hkd" to "HKD", "hk$" to "HKD",
        "sgd" to "SGD", "s$" to "SGD",
        "myr" to "MYR", "rm" to "MYR",
        "idr" to "IDR", "rp" to "IDR",
        "thb" to "THB", "฿" to "THB",
        "php" to "PHP", "₱" to "PHP",
        "vnd" to "VND", "₫" to "VND",
        "krw" to "KRW", "₩" to "KRW",
        "aud" to "AUD", "a$" to "AUD",
        "nzd" to "NZD",
        "cad" to "CAD", "c$" to "CAD",
        "mxn" to "MXN",
        "brl" to "BRL", "r$" to "BRL",
        "zar" to "ZAR",
        "ngn" to "NGN", "₦" to "NGN",
        "kes" to "KES", "ksh" to "KES",
        "chf" to "CHF",
        "sek" to "SEK", "nok" to "NOK", "dkk" to "DKK",
        "pln" to "PLN",
        "rub" to "RUB", "₽" to "RUB",
    )

    private val tokenAlternation: String = currencyAliases.keys
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }

    private const val NUMBER = "([0-9][0-9,]*(?:\\.[0-9]{1,3})?)"

    private val currencyThenNumber = Regex(
        "(?<![a-z0-9])($tokenAlternation)\\s?$NUMBER",
        RegexOption.IGNORE_CASE,
    )
    private val numberThenCurrency = Regex(
        "(?<![0-9.,])$NUMBER\\s?($tokenAlternation)(?![a-z])",
        RegexOption.IGNORE_CASE,
    )

    private val debitWords = Regex(
        "\\b(debited|debit(?:ed)? for|spent|paid|payment of|payment to|purchase[ds]?|withdrawn|withdrawal|" +
            "sent|txn of|transaction of|charged|deducted|used at|used for|dr\\.?)\\b|\\bpos\\b",
        RegexOption.IGNORE_CASE,
    )
    private val creditWords = Regex(
        "\\b(credited|received|deposited|refund(?:ed)?|reversed|reversal|cashback of|cr\\.?)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val otp = Regex(
        "(\\botp\\b|one[ -]?time password|verification code|security code|passcode)\\s*(is|:|for|to)|" +
            "is your (otp|code|one[ -]?time|verification)|\\botp\\b.{0,40}\\b(txn|transaction|payment)\\b.{0,40}\\b(at|of|for)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val notCompleted = Regex(
        "\\b(will be debited|will be credited|is due|due on|due date|minimum amount due|min amt due|" +
            "payment reminder|has been requested|requested money|request of|collect request|" +
            "failed|declined|unsuccessful|could not be|has not been|was not)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val promo = Regex(
        "\\b(pre-?approved|apply now|click here|limited period|get upto|get up to|offer ends|" +
            "win |congratulations|lucky draw|voucher worth|eligible for a loan)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val accountPattern = Regex(
        "\\b(?:a/c|a/c no\\.?|acct|account|card|ac)\\s*(?:no\\.?|number)?\\s*(?:ending(?: with| in)?)?\\s*[:#]?\\s*[x*.]*\\s*([0-9]{3,6})\\b",
        RegexOption.IGNORE_CASE,
    )

    private const val MERCHANT_CHARS = "[A-Za-z0-9&.'*_/\\- ]"
    private const val STOP =
        "(?=\\s+(?:on|via|ref|refno|ref no|using|from|avl|avbl|bal|is|was|txn|upi|dated|with|thru|through|by|and|for|in|of)\\b|" +
            "\\s+\\d{1,2}[-/]|[,;:()]|\\.(?:\\s|$)|$)"

    private val vpa = Regex("\\bvpa\\s*[:\\-]?\\s*([A-Za-z0-9._\\-]+)@", RegexOption.IGNORE_CASE)
    private val expenseMerchant = listOf(
        Regex("\\bat\\s+($MERCHANT_CHARS{2,40}?)$STOP", RegexOption.IGNORE_CASE),
        Regex("\\b(?:to|towards|paid to|sent to)\\s+($MERCHANT_CHARS{2,40}?)$STOP", RegexOption.IGNORE_CASE),
        Regex("\\binfo[:\\s]+($MERCHANT_CHARS{2,40}?)$STOP", RegexOption.IGNORE_CASE),
        Regex("\\bfor\\s+($MERCHANT_CHARS{2,40}?)$STOP", RegexOption.IGNORE_CASE),
    )
    private val incomeMerchant = listOf(
        Regex("\\bfrom\\s+($MERCHANT_CHARS{2,40}?)$STOP", RegexOption.IGNORE_CASE),
        Regex("\\bby\\s+($MERCHANT_CHARS{2,40}?)$STOP", RegexOption.IGNORE_CASE),
    )
    private val badMerchantStart = Regex(
        "^(your|you|a/c|ac |acct|account|card|the |rs|inr|bank|beneficiary|xx|\\*|\\d)",
        RegexOption.IGNORE_CASE,
    )

    fun parse(sender: String, body: String): Result? {
        val text = body.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return null

        if (otp.containsMatchIn(text)) return null
        if (notCompleted.containsMatchIn(text)) return null

        val debit = debitWords.find(text)
        val credit = creditWords.find(text)
        if (debit == null && credit == null) return null
        if (promo.containsMatchIn(text) && debit == null && credit?.value?.lowercase() != "credited") return null

        val kind = when {
            debit != null && credit != null -> if (debit.range.first <= credit.range.first) Kind.EXPENSE else Kind.INCOME
            debit != null -> Kind.EXPENSE
            else -> Kind.INCOME
        }

        val money = findAmount(text) ?: return null
        val merchant = findMerchant(text, kind) ?: senderName(sender)
        val account = accountPattern.find(text)?.groupValues?.get(1)

        val balance = findBalance(text)
        return Result(
            amount = money.first,
            currency = money.second,
            kind = kind,
            merchant = merchant,
            account = account,
            balance = balance?.amount,
            balanceCurrency = balance?.currency,
            balanceIsLimit = balance?.isLimit ?: false,
        )
    }

    private data class MoneyHit(val start: Int, val amount: Double, val currency: String, val before: String)

    private fun moneyHits(text: String): List<MoneyHit> {
        val raw = buildList {
            currencyThenNumber.findAll(text).forEach { add(Triple(it.range.first, it.groupValues[1], it.groupValues[2])) }
            numberThenCurrency.findAll(text).forEach { add(Triple(it.range.first, it.groupValues[2], it.groupValues[1])) }
        }.sortedBy { it.first }
        return raw.mapNotNull { (start, token, number) ->
            val value = number.replace(",", "").trimEnd('.').toDoubleOrNull() ?: return@mapNotNull null
            val code = currencyAliases[token.lowercase()] ?: return@mapNotNull null
            if (value <= 0.0) return@mapNotNull null
            MoneyHit(start, value, code, text.substring(maxOf(0, start - 22), start).lowercase())
        }
    }

    data class Balance(val amount: Double, val currency: String, val isLimit: Boolean)

    /** The "available balance" or "available limit" figure in a bank SMS. */
    internal fun findBalance(text: String): Balance? {
        val hit = moneyHits(text).firstOrNull { h ->
            listOf("bal", "avl", "avbl", "available").any { h.before.contains(it) } && !h.before.contains("outstanding")
        } ?: return null
        val isLimit = hit.before.contains("lmt") || hit.before.contains("limit")
        return Balance(hit.amount, hit.currency, isLimit)
    }

    private val dueWords = Regex(
        "\\b(is due|due on|due date|due by|payment due|pay by|amount due|amt due|due amt|payable by)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val numericDate = Regex("\\b(\\d{1,2})[-/.](\\d{1,2})(?:[-/.](\\d{2,4}))?\\b")
    private val dayMonthName = Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?[- ]?(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?[-, ]*(\\d{2,4})?", RegexOption.IGNORE_CASE)
    private val monthNameDay = Regex("\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.? (\\d{1,2})(?:st|nd|rd|th)?,? ?(\\d{4})?", RegexOption.IGNORE_CASE)

    /**
     * Recognises "your card bill of Rs 4,500 is due on 05-11" style reminders.
     * [received] anchors dates that have no year.
     */
    fun parseDue(sender: String, body: String, received: LocalDate): Due? {
        val text = body.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        if (otp.containsMatchIn(text)) return null
        val due = dueWords.find(text) ?: return null
        if (Regex("\\b(debited|spent|paid|credited|received)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) &&
            !Regex("\\b(bill|statement|emi|due)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        ) return null
        val money = moneyHits(text).firstOrNull { h -> !h.before.contains("min") && !h.before.contains("avl") && !h.before.contains("limit") }
            ?: return null
        // Prefer a date after the "due" phrase; fall back to any date in the message.
        val after = text.substring(due.range.first)
        val date = findDate(after, received) ?: findDate(text, received) ?: return null
        if (date.isBefore(received.minusDays(1))) return null
        return Due(money.amount, money.currency, date, accountPattern.find(text)?.groupValues?.get(1))
    }

    internal fun findDate(text: String, received: LocalDate): LocalDate? {
        fun fixYear(y: String?, m: Int, d: Int): LocalDate? = runCatching {
            if (y.isNullOrEmpty()) {
                val candidate = LocalDate.of(received.year, m, d)
                if (candidate.isBefore(received.minusDays(15))) candidate.plusYears(1) else candidate
            } else {
                val year = y.toInt().let { if (it < 100) 2000 + it else it }
                LocalDate.of(year, m, d)
            }
        }.getOrNull()

        val candidates = buildList {
            dayMonthName.find(text)?.let { m ->
                add(m.range.first to fixYear(m.groupValues[3], months.indexOf(m.groupValues[2].lowercase()) + 1, m.groupValues[1].toInt()))
            }
            monthNameDay.find(text)?.let { m ->
                add(m.range.first to fixYear(m.groupValues[3], months.indexOf(m.groupValues[1].lowercase()) + 1, m.groupValues[2].toInt()))
            }
            numericDate.findAll(text).forEach { m ->
                val a = m.groupValues[1].toInt()
                val b = m.groupValues[2].toInt()
                // Day-first is the norm for bank SMS in the Gulf and South Asia.
                if (b in 1..12 && a in 1..31) add(m.range.first to fixYear(m.groupValues[3], b, a))
            }
        }
        return candidates.filter { it.second != null }.minByOrNull { it.first }?.second
    }

    /** Returns (amount, ISO currency) for the first amount that is not a balance or limit. */
    internal fun findAmount(text: String): Pair<Double, String>? {
        data class Hit(val start: Int, val token: String, val number: String)

        val hits = buildList {
            currencyThenNumber.findAll(text).forEach { add(Hit(it.range.first, it.groupValues[1], it.groupValues[2])) }
            numberThenCurrency.findAll(text).forEach { add(Hit(it.range.first, it.groupValues[2], it.groupValues[1])) }
        }.sortedBy { it.start }

        for (hit in hits) {
            val before = text.substring(maxOf(0, hit.start - 22), hit.start).lowercase()
            if (BALANCE_HINTS.any { before.contains(it) }) continue
            val value = hit.number.replace(",", "").trimEnd('.').toDoubleOrNull() ?: continue
            if (value <= 0.0) continue
            val code = currencyAliases[hit.token.lowercase()] ?: continue
            return value to code
        }
        return null
    }

    private val BALANCE_HINTS = listOf("bal", "avl", "avbl", "available", "limit", "outstanding", "total due")

    private fun findMerchant(text: String, kind: Kind): String? {
        if (kind == Kind.EXPENSE) {
            vpa.find(text)?.let { return prettify(it.groupValues[1]) }
        }
        val patterns = if (kind == Kind.EXPENSE) expenseMerchant else incomeMerchant + expenseMerchant
        for (p in patterns) {
            for (m in p.findAll(text)) {
                val candidate = m.groupValues[1].trim().trim('.', '-', '*', '/')
                if (candidate.length < 2) continue
                if (badMerchantStart.containsMatchIn(candidate)) continue
                if (candidate.any { it.isDigit() } && candidate.count { it.isLetter() } < 3) continue
                return prettify(candidate)
            }
        }
        return null
    }

    private fun prettify(raw: String): String {
        val cleaned = raw.replace('_', ' ').replace(Regex("\\s+"), " ").trim()
        // Title-case shouty merchant names but keep things like "NETFLIX.COM" readable.
        return cleaned.split(' ').joinToString(" ") { w ->
            if (w.length > 3 && w.all { !it.isLetter() || it.isUpperCase() } && !w.contains('.')) {
                w.lowercase().replaceFirstChar { it.uppercase() }
            } else if (w.all { !it.isLetter() || it.isLowerCase() }) {
                w.replaceFirstChar { it.uppercase() }
            } else {
                w
            }
        }
    }

    /** "AD-HDFCBK" -> "HDFCBK", "+971501234567" -> null */
    fun senderName(sender: String): String? {
        val s = sender.substringAfterLast('-').trim()
        if (s.isEmpty() || s.all { it.isDigit() || it == '+' }) return null
        return s
    }
}
