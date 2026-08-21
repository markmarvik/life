package com.life.app.data

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToLong

object Money {

    fun defaultCurrencyCode(locale: Locale = Locale.getDefault()): String =
        try {
            Currency.getInstance(locale).currencyCode
        } catch (_: Exception) {
            "USD"
        }

    fun fractionDigits(currencyCode: String): Int =
        try {
            Currency.getInstance(currencyCode).defaultFractionDigits.coerceAtLeast(0)
        } catch (_: Exception) {
            2
        }

    fun toMinor(major: Double, currencyCode: String): Long {
        val scale = 10.0.pow(fractionDigits(currencyCode))
        return (major * scale).roundToLong()
    }

    fun toMajor(minor: Long, currencyCode: String): Double {
        val scale = 10.0.pow(fractionDigits(currencyCode))
        return minor / scale
    }

    /**
     * Parse a user-typed amount ("4.50", "4,50", "12") into minor units.
     * Returns null when empty or not a non-negative number.
     */
    fun parseToMinor(input: String, currencyCode: String): Long? {
        val t = input.trim().replace(" ", "").replace(',', '.')
        if (t.isEmpty()) return null
        val d = t.toDoubleOrNull() ?: return null
        if (d < 0.0 || !d.isFinite()) return null
        return toMinor(d, currencyCode)
    }

    fun formatMajorPlain(minor: Long, currencyCode: String): String {
        val digits = fractionDigits(currencyCode)
        val major = toMajor(minor, currencyCode)
        return if (digits == 0) {
            major.toLong().toString()
        } else {
            "%.${digits}f".format(Locale.US, major)
        }
    }

    fun format(
        minor: Long,
        currencyCode: String,
        locale: Locale = Locale.getDefault()
    ): String {
        val nf = NumberFormat.getCurrencyInstance(locale)
        try {
            nf.currency = Currency.getInstance(currencyCode)
        } catch (_: Exception) {
            // keep locale currency
        }
        return nf.format(toMajor(minor, currencyCode))
    }

    fun formatSigned(
        minor: Long,
        direction: MoneyDirection,
        currencyCode: String,
        locale: Locale = Locale.getDefault()
    ): String {
        val abs = format(kotlin.math.abs(minor), currencyCode, locale)
        return if (direction == MoneyDirection.IN) "+$abs" else "−$abs"
    }
}
