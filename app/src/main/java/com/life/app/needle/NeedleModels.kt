package com.life.app.needle

import com.life.app.data.AppData
import com.life.app.data.MoneyDirection
import java.time.LocalDate

enum class NeedleEngine { PATTERN, CACTUS }

data class NeedleContext(
    val data: AppData,
    val today: LocalDate = LocalDate.now(),
    val currencyCode: String = data.currencyCode
)

sealed class NeedleDecision {
    abstract val confidence: Double
    abstract val engine: NeedleEngine

    data class Habit(
        val name: String,
        override val confidence: Double,
        override val engine: NeedleEngine
    ) : NeedleDecision()

    data class Note(
        val body: String,
        val tags: List<String> = emptyList(),
        val wantDate: String? = null,
        override val confidence: Double,
        override val engine: NeedleEngine
    ) : NeedleDecision()

    data class Finance(
        val direction: MoneyDirection,
        val amountMinor: Long?,
        val payee: String = "",
        val category: String = "",
        val memo: String = "",
        override val confidence: Double,
        override val engine: NeedleEngine
    ) : NeedleDecision()

    data class CompleteWant(
        val text: String,
        override val confidence: Double,
        override val engine: NeedleEngine
    ) : NeedleDecision()

    data class MissWant(
        val text: String,
        override val confidence: Double,
        override val engine: NeedleEngine
    ) : NeedleDecision()

    data class Balance(
        override val confidence: Double,
        override val engine: NeedleEngine
    ) : NeedleDecision()

    data class Unknown(
        val text: String,
        override val confidence: Double = 0.0,
        override val engine: NeedleEngine = NeedleEngine.PATTERN
    ) : NeedleDecision()
}

fun NeedleDecision.label(): String = when (this) {
    is NeedleDecision.Habit -> "Habit · $name"
    is NeedleDecision.Note -> if (wantDate != null) "Want · $body" else "Note · $body"
    is NeedleDecision.Finance -> {
        val dir = if (direction == MoneyDirection.IN) "+" else "−"
        "Finance · $dir${amountMinor ?: "?"} $payee $category".trim()
    }
    is NeedleDecision.CompleteWant -> "Done · $text"
    is NeedleDecision.MissWant -> "Didn't · $text"
    is NeedleDecision.Balance -> "Balance"
    is NeedleDecision.Unknown -> "Not sure"
}

fun NeedleDecision.canAutoCommit(): Boolean = when (this) {
    is NeedleDecision.Unknown -> false
    is NeedleDecision.MissWant -> false
    is NeedleDecision.Finance -> amountMinor != null && amountMinor > 0L && confidence >= 0.55
    else -> confidence >= 0.55
}
