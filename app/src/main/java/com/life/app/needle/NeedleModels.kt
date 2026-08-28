package com.life.app.needle

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

@Serializable
data class NeedleCall(
    val name: String,
    val arguments: Map<String, JsonElement> = emptyMap()
)

@Serializable
data class NeedleResponse(
    val type: String = "call",
    val success: Boolean = true,
    val error: String? = null,
    @SerialName("function_calls")
    val functionCalls: List<NeedleCall> = emptyList(),
    val reasoning: String? = null,
    val confidence: Double? = null
)

enum class NeedleKind { HABIT, NOTE, FINANCE, WANT_DONE, BALANCE }

data class NeedleDraft(
    val raw: String,
    val kind: NeedleKind,
    val label: String,
    val confidence: Double?,
    val needsConfirm: Boolean,
    val habitName: String = "",
    val noteBody: String = "",
    val isWant: Boolean = false,
    val dueDate: String? = null,
    val wantEventId: String? = null,
    val signOut: Boolean = true,
    val amountMinor: Long? = null,
    val payee: String = "",
    val category: String = "",
    val balanceLabel: String = ""
)

fun JsonElement?.asString(): String = when (this) {
    null, is JsonNull -> ""
    is JsonPrimitive -> content
    else -> toString().trim('"')
}

fun JsonElement?.asDouble(): Double? = when (this) {
    null, is JsonNull -> null
    is JsonPrimitive -> doubleOrNull ?: content.toDoubleOrNull()
    else -> null
}
