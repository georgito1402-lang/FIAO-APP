package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToLong

private val spanishLocale = Locale("es", "ES")

fun formatCents(cents: Long): String {
    val isNegative = cents < 0L
    val absCents = abs(cents)
    val symbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }
    val whole = absCents / 100L
    val remainder = absCents % 100L
    val formatted = if (remainder == 0L) {
        val df = DecimalFormat("#,##0", symbols)
        "$${df.format(whole)}"
    } else {
        val df = DecimalFormat("#,##0.00", symbols)
        "$${df.format(absCents / 100.0)}"
    }
    return if (isNegative) "-$formatted" else formatted
}

fun formatBsFromUsdCents(usdCents: Long, rate: Double): String {
    val bsValue = (usdCents / 100.0) * rate
    val symbols = DecimalFormatSymbols(spanishLocale).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
    val df = DecimalFormat("#,##0.00", symbols)
    return "Bs ${df.format(bsValue)}"
}

fun formatRate(rate: Double): String {
    val symbols = DecimalFormatSymbols(spanishLocale).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
    val df = DecimalFormat("#,##0.00", symbols)
    return "Bs ${df.format(rate)}"
}

fun bsInputToUsdCents(bsRaw: String, rate: Double): Long? {
    if (rate <= 0.0) return null
    val cleaned = bsRaw.trim()
        .replace("Bs", "", ignoreCase = true)
        .replace(" ", "")
        .replace(",", ".")
    if (cleaned.isEmpty()) return null
    val bsAmount = cleaned.toDoubleOrNull() ?: return null
    if (bsAmount.isNaN() || bsAmount.isInfinite() || bsAmount < 0.0) return null
    val usd = bsAmount / rate
    return (usd * 100.0).roundToLong()
}

fun centsToInputString(cents: Long): String {
    if (cents <= 0L) return ""
    val whole = cents / 100L
    val rem = cents % 100L
    return if (rem == 0L) {
        whole.toString()
    } else {
        String.format(Locale.US, "%.2f", cents / 100.0)
    }
}

fun parseAmountToCents(raw: String): Long? {
    val cleaned = raw.trim()
        .replace("$", "")
        .replace(" ", "")
        .replace(",", ".")
    if (cleaned.isEmpty()) return null
    val value = cleaned.toDoubleOrNull() ?: return null
    if (value.isNaN() || value.isInfinite() || value < 0.0) return null
    return (value * 100.0).roundToLong()
}

fun formatRelativeDateTime(timestampMillis: Long): String {
    val nowCal = Calendar.getInstance()
    val targetCal = Calendar.getInstance().apply { timeInMillis = timestampMillis }
    val timeFormat = SimpleDateFormat("HH:mm", spanishLocale)
    val timeStr = timeFormat.format(Date(timestampMillis))

    val sameYear = nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR)
    val nowDay = nowCal.get(Calendar.DAY_OF_YEAR)
    val targetDay = targetCal.get(Calendar.DAY_OF_YEAR)

    return when {
        sameYear && nowDay == targetDay -> "Hoy, $timeStr"
        sameYear && nowDay - targetDay == 1 -> "Ayer, $timeStr"
        sameYear -> SimpleDateFormat("d MMM, HH:mm", spanishLocale).format(Date(timestampMillis))
        else -> SimpleDateFormat("d MMM yyyy", spanishLocale).format(Date(timestampMillis))
    }
}

fun formatFullReceiptDate(timestampMillis: Long): String {
    return SimpleDateFormat("dd/MM/yyyy · HH:mm", spanishLocale).format(Date(timestampMillis))
}

fun formatShortDate(timestampMillis: Long): String {
    return SimpleDateFormat("dd/MM/yyyy", spanishLocale).format(Date(timestampMillis))
}

fun daysOffsetMillis(daysFromNow: Int, baseMillis: Long = System.currentTimeMillis()): Long {
    return baseMillis + TimeUnit.DAYS.toMillis(daysFromNow.toLong())
}

fun customerInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}

fun sanitizeWhatsAppPhone(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return when {
        digits.isEmpty() -> ""
        digits.startsWith("0") && digits.length == 11 -> "58" + digits.drop(1)
        else -> digits
    }
}
