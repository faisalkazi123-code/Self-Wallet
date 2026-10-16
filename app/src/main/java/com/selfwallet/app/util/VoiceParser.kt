package com.selfwallet.app.util

/**
 * Turns a spoken sentence such as "lunch 45 dirhams corporate" into transaction fields.
 * Pure Kotlin, so it is unit tested.
 */
object VoiceParser {
    data class Result(
        val amount: Double?,
        val currency: String?,
        val corporate: Boolean?,
        val income: Boolean,
        val categoryName: String?,
        val merchant: String,
    )

    private val currencyWords = mapOf(
        "dirham" to "AED", "dirhams" to "AED", "aed" to "AED",
        "riyal" to "SAR", "riyals" to "SAR", "sar" to "SAR",
        "rupee" to "INR", "rupees" to "INR", "rs" to "INR", "inr" to "INR",
        "dollar" to "USD", "dollars" to "USD", "usd" to "USD", "bucks" to "USD",
        "euro" to "EUR", "euros" to "EUR", "eur" to "EUR",
        "pound" to "GBP", "pounds" to "GBP", "gbp" to "GBP",
        "dinar" to "KWD", "dinars" to "KWD", "kwd" to "KWD",
        "taka" to "BDT", "ringgit" to "MYR", "yen" to "JPY", "yuan" to "CNY",
    )
    private val corporateWords = setOf("corporate", "company", "office", "work", "business", "client")
    private val personalWords = setOf("personal", "myself", "private", "home")
    private val incomeWords = setOf("received", "income", "salary", "got", "refund")
    private val fillers = setOf("for", "at", "on", "spent", "paid", "the", "a", "an", "to", "of", "and", "in", "expense")

    fun parse(text: String, categoryNames: List<String>): Result {
        val words = text.lowercase().replace(",", "").split(Regex("\\s+")).filter { it.isNotBlank() }
        var amount: Double? = null
        var currency: String? = null
        var corporate: Boolean? = null
        var income = false
        val rest = mutableListOf<String>()
        for (w in words) {
            val clean = w.trim('.', '!', '?')
            val number = clean.removePrefix("$").removePrefix("₹").removePrefix("€").removePrefix("£").toDoubleOrNull()
            when {
                number != null && amount == null -> {
                    amount = number
                    when {
                        clean.startsWith("$") -> currency = "USD"
                        clean.startsWith("₹") -> currency = "INR"
                        clean.startsWith("€") -> currency = "EUR"
                        clean.startsWith("£") -> currency = "GBP"
                    }
                }
                clean in currencyWords -> currency = currencyWords[clean]
                clean in corporateWords -> corporate = true
                clean in personalWords -> corporate = false
                clean in incomeWords -> income = true
                clean in fillers -> Unit
                else -> rest += clean
            }
        }
        val spoken = rest.joinToString(" ")
        val category = categoryNames.firstOrNull { name ->
            val n = name.lowercase()
            rest.any { w -> w.length >= 3 && (n.contains(w) || w.contains(n.substringBefore(' '))) }
        }
        val merchant = spoken.split(' ').filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        return Result(amount, currency, corporate, income, category, merchant)
    }
}
