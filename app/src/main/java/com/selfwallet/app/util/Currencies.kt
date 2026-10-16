package com.selfwallet.app.util

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

data class CurrencyInfo(
    val code: String,
    val symbol: String,
    val name: String,
    val decimals: Int = 2,
)

object Currencies {
    val all: List<CurrencyInfo> = listOf(
        CurrencyInfo("USD", "$", "US Dollar"),
        CurrencyInfo("EUR", "€", "Euro"),
        CurrencyInfo("GBP", "£", "British Pound"),
        CurrencyInfo("INR", "₹", "Indian Rupee"),
        CurrencyInfo("AED", "AED", "UAE Dirham"),
        CurrencyInfo("SAR", "SAR", "Saudi Riyal"),
        CurrencyInfo("QAR", "QAR", "Qatari Riyal"),
        CurrencyInfo("KWD", "KD", "Kuwaiti Dinar", 3),
        CurrencyInfo("BHD", "BD", "Bahraini Dinar", 3),
        CurrencyInfo("OMR", "OMR", "Omani Rial", 3),
        CurrencyInfo("PKR", "Rs", "Pakistani Rupee"),
        CurrencyInfo("BDT", "৳", "Bangladeshi Taka"),
        CurrencyInfo("LKR", "Rs", "Sri Lankan Rupee"),
        CurrencyInfo("NPR", "Rs", "Nepalese Rupee"),
        CurrencyInfo("EGP", "E£", "Egyptian Pound"),
        CurrencyInfo("JOD", "JD", "Jordanian Dinar", 3),
        CurrencyInfo("TRY", "₺", "Turkish Lira"),
        CurrencyInfo("JPY", "¥", "Japanese Yen", 0),
        CurrencyInfo("CNY", "CN¥", "Chinese Yuan"),
        CurrencyInfo("HKD", "HK$", "Hong Kong Dollar"),
        CurrencyInfo("SGD", "S$", "Singapore Dollar"),
        CurrencyInfo("MYR", "RM", "Malaysian Ringgit"),
        CurrencyInfo("IDR", "Rp", "Indonesian Rupiah", 0),
        CurrencyInfo("THB", "฿", "Thai Baht"),
        CurrencyInfo("PHP", "₱", "Philippine Peso"),
        CurrencyInfo("VND", "₫", "Vietnamese Dong", 0),
        CurrencyInfo("KRW", "₩", "South Korean Won", 0),
        CurrencyInfo("AUD", "A$", "Australian Dollar"),
        CurrencyInfo("NZD", "NZ$", "New Zealand Dollar"),
        CurrencyInfo("CAD", "C$", "Canadian Dollar"),
        CurrencyInfo("MXN", "MX$", "Mexican Peso"),
        CurrencyInfo("BRL", "R$", "Brazilian Real"),
        CurrencyInfo("ZAR", "R", "South African Rand"),
        CurrencyInfo("NGN", "₦", "Nigerian Naira"),
        CurrencyInfo("KES", "KSh", "Kenyan Shilling"),
        CurrencyInfo("CHF", "CHF", "Swiss Franc"),
        CurrencyInfo("SEK", "kr", "Swedish Krona"),
        CurrencyInfo("NOK", "kr", "Norwegian Krone"),
        CurrencyInfo("DKK", "kr", "Danish Krone"),
        CurrencyInfo("PLN", "zł", "Polish Zloty"),
        CurrencyInfo("RUB", "₽", "Russian Ruble"),
    )

    private val byCode = all.associateBy { it.code }

    fun info(code: String): CurrencyInfo = byCode[code.uppercase()] ?: CurrencyInfo(code.uppercase(), code.uppercase(), code.uppercase())

    fun format(amount: Double, code: String, showSign: Boolean = false): String {
        val info = info(code)
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = info.decimals
            maximumFractionDigits = info.decimals
            isGroupingUsed = true
        }
        val sign = when {
            showSign && amount > 0 -> "+"
            amount < 0 -> "−"
            else -> ""
        }
        val sym = info.symbol
        val body = nf.format(abs(amount))
        // Letter symbols read better with a space ("AED 12.00"), glyph symbols without ("$12.00").
        val spaced = sym.last().isLetter()
        return if (spaced) "$sign$sym $body" else "$sign$sym$body"
    }

    /** Compact form for chart labels: 1.2K, 3.4M. */
    fun compact(amount: Double): String {
        val a = abs(amount)
        return when {
            a >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", amount / 1_000_000_000)
            a >= 1_000_000 -> String.format(Locale.US, "%.1fM", amount / 1_000_000)
            a >= 1_000 -> String.format(Locale.US, "%.1fK", amount / 1_000)
            else -> String.format(Locale.US, "%.0f", amount)
        }
    }
}
