package com.life.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.app.needle.NeedleDraft
import com.life.app.needle.NeedleKind

@Composable
fun CaptureComposer(
    text: String,
    onTextChange: (String) -> Unit,
    pending: List<NeedleDraft>,
    listening: Boolean,
    busy: Boolean,
    engineReady: Boolean,
    status: String?,
    onSend: () -> Unit,
    onConfirmDraft: (NeedleDraft) -> Unit,
    onDismissDraft: (NeedleDraft) -> Unit,
    onConfirmAll: () -> Unit,
    onPickType: (CaptureKind) -> Unit,
    onMicDown: () -> Unit,
    onMicUp: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (pending.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pending.forEach { draft ->
                    PreviewChip(
                        draft = draft,
                        onClick = { onConfirmDraft(draft) },
                        onLongClick = { onDismissDraft(draft) }
                    )
                }
                Text(
                    "Log",
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onConfirmAll)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FallbackChip("Habit") { onPickType(CaptureKind.HABIT) }
            FallbackChip("Note") { onPickType(CaptureKind.NOTE) }
            FallbackChip("Money") { onPickType(CaptureKind.FINANCE) }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        if (listening) "Listening…"
                        else "What happened?"
                    )
                },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                shape = RoundedCornerShape(20.dp)
            )
            MicButton(listening = listening, enabled = !busy, onDown = onMicDown, onUp = onMicUp)
            SendButton(
                armed = text.isNotBlank() || pending.isNotEmpty(),
                busy = busy,
                onClick = onSend
            )
        }
        val hint = status ?: if (engineReady) "Needle · hold mic" else "Rules · hold mic"
        Text(
            hint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
    }
}

@Composable
private fun MicButton(
    listening: Boolean,
    enabled: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit
) {
    val bg = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (listening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(bg)
            .pointerInput(enabled) {
                detectTapGestures(
                    onPress = {
                        if (!enabled) return@detectTapGestures
                        onDown()
                        try {
                            tryAwaitRelease()
                        } finally {
                            onUp()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (listening) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = "Hold to talk",
            tint = fg
        )
    }
}

@Composable
private fun SendButton(armed: Boolean, busy: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (armed) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(enabled = armed && !busy, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (armed) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FallbackChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PreviewChip(
    draft: NeedleDraft,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val prefix = when (draft.kind) {
        NeedleKind.HABIT -> "✓"
        NeedleKind.FINANCE -> if (draft.signOut) "−" else "+"
        NeedleKind.WANT_DONE -> "✓"
        NeedleKind.NOTE -> "✎"
        NeedleKind.BALANCE -> "="
    }
    Text(
        "$prefix ${draft.label}",
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
    )
}
