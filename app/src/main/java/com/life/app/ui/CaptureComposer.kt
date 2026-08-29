package com.life.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.life.app.data.AppData
import com.life.app.data.Fuzzy
import com.life.app.data.Money
import com.life.app.data.WantStatus
import com.life.app.data.balanceMinor
import com.life.app.needle.CaptureRuntime
import com.life.app.needle.NeedleContext
import com.life.app.needle.NeedleDecision
import com.life.app.needle.canAutoCommit
import com.life.app.needle.label
import com.life.app.util.rememberHaptics
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureComposer(
    data: AppData,
    vm: LifeViewModel,
    onUseForm: () -> Unit,
    onDismiss: () -> Unit,
    onNeedReflection: (com.life.app.data.LifeEvent) -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val status by CaptureRuntime.status.collectAsStateWithLifecycle()
    val sttReady by CaptureRuntime.sttReady.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<NeedleDecision?>(null) }
    var flash by remember { mutableStateOf<String?>(null) }
    var lang by remember { mutableStateOf("en") }

    val micPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) scope.launch { CaptureRuntime.ensureModels(context, install = true) }
    }

    LaunchedEffect(Unit) {
        CaptureRuntime.initContext(context)
        CaptureRuntime.ensureModels(context, install = false)
    }

    fun commit(decision: NeedleDecision) {
        when (decision) {
            is NeedleDecision.Habit -> {
                vm.logHabitNamed(decision.name)
                flash = "Logged ${decision.name}"
            }
            is NeedleDecision.Note -> {
                vm.logNote(decision.body, decision.tags, decision.wantDate != null, decision.wantDate)
                flash = if (decision.wantDate != null) "Want saved" else "Note saved"
            }
            is NeedleDecision.Finance -> {
                val amt = decision.amountMinor
                if (amt == null || amt <= 0L) {
                    pending = decision
                    return
                }
                vm.logFinance(decision.direction, amt, decision.payee, decision.category, decision.memo)
                flash = "Logged ${Money.formatSigned(amt, decision.direction, data.currencyCode)}"
            }
            is NeedleDecision.CompleteWant -> {
                val open = data.events.filter { it.isWant && it.wantStatus == WantStatus.OPEN }
                val hit = open.maxByOrNull { Fuzzy.score(decision.text, it.body) }
                if (hit != null && Fuzzy.score(decision.text, hit.body) >= 0.4) {
                    vm.completeWant(hit.id)
                    flash = "Done: ${hit.body}"
                } else {
                    flash = "No matching Want"
                    pending = decision
                    return
                }
            }
            is NeedleDecision.MissWant -> {
                val open = data.events.filter { it.isWant && it.wantStatus == WantStatus.OPEN }
                val hit = open.maxByOrNull { Fuzzy.score(decision.text, it.body) }
                if (hit != null) {
                    onNeedReflection(hit)
                    return
                }
                flash = "No matching Want"
            }
            is NeedleDecision.Balance -> {
                flash = "Balance ${Money.format(data.balanceMinor(), data.currencyCode)}"
            }
            is NeedleDecision.Unknown -> {
                pending = decision
                return
            }
        }
        haptics.success()
        pending = null
        text = ""
    }

    fun runText(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        lang = CaptureRuntime.detectLang(trimmed, lang)
        scope.launch {
            val decision = CaptureRuntime.dispatch(trimmed, NeedleContext(data))
            if (decision.canAutoCommit()) commit(decision) else pending = decision
        }
    }

    suspend fun finishMic() {
        listening = false
        val pcm = CaptureRuntime.mic.stop()
        val spoken = CaptureRuntime.transcribe(pcm, lang)
        if (spoken.isNullOrBlank()) {
            flash = if (sttReady) "Didn't catch that" else "Voice pack not installed — type instead"
        } else {
            text = spoken
            runText(spoken)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("What happened?", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            Spacer(Modifier.height(4.dp))
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("popio kreatin · spent 450 lidl") },
                    singleLine = true,
                    enabled = !listening
                )
                val micColor = if (listening) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(micColor)
                        .pointerInput(sttReady) {
                            detectTapGestures(
                                onPress = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!granted) {
                                        micPerm.launch(Manifest.permission.RECORD_AUDIO)
                                        return@detectTapGestures
                                    }
                                    if (!sttReady) {
                                        scope.launch { CaptureRuntime.ensureModels(context, install = true) }
                                    }
                                    haptics.tick()
                                    listening = true
                                    CaptureRuntime.mic.start()
                                    tryAwaitRelease()
                                    scope.launch { finishMic() }
                                }
                            )
                        }
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Hold to talk",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { runText(text) },
                    enabled = text.isNotBlank() && !listening
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Log")
                }
                TextButton(onClick = onUseForm) { Text("Use a form") }
            }
            pending?.let { p ->
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(14.dp)
                ) {
                    Text("Confirm", fontWeight = FontWeight.Medium)
                    Text(p.label(), fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { commit(p) }) { Text("Yes") }
                        OutlinedButton(onClick = { pending = null }) { Text("No") }
                    }
                }
            }
            flash?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
            if (listening) {
                Spacer(Modifier.height(8.dp))
                Text("Listening… release to log", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

