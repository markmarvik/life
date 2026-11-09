package com.life.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.life.app.data.AppData
import com.life.app.data.Money
import com.life.app.data.ThemeOptions
import com.life.app.needle.CaptureRuntime
import com.life.app.watch.DigestScheduler
import com.life.app.watch.GadgetbridgeClient
import com.life.app.watch.WatchPreferences
import com.life.app.watch.WatchSettings
import com.life.app.watch.WatchSettingsStore
import java.time.LocalDate
import kotlinx.coroutines.launch

private val COMMON_CURRENCIES = listOf("USD", "EUR", "GBP", "NOK", "SEK", "DKK", "JPY", "CAD", "AUD")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    data: AppData,
    vm: LifeViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packStatus by CaptureRuntime.status.collectAsStateWithLifecycle()
    val needleReady by CaptureRuntime.needleReady.collectAsStateWithLifecycle()
    val sttReady by CaptureRuntime.sttReady.collectAsStateWithLifecycle()
    var openingText by remember(data.openingBalanceMinor, data.currencyCode) {
        mutableStateOf(Money.formatMajorPlain(data.openingBalanceMinor, data.currencyCode))
    }
    var currency by remember(data.currencyCode) { mutableStateOf(data.currencyCode) }
    var status by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            vm.exportJson(context, uri)
            status = "Exported"
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.importJson(context, uri) { result ->
                status = result.fold({ "Imported" }, { "Import failed: ${it.message}" })
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Section("Money")
            OutlinedTextField(
                value = openingText,
                onValueChange = { openingText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Opening balance") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it.uppercase().take(3) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Currency") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                COMMON_CURRENCIES.forEach { code ->
                    TagChip(code, currency.equals(code, ignoreCase = true)) { currency = code }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val minor = Money.parseToMinor(openingText, currency) ?: 0L
                    vm.updateSettings(openingBalanceMinor = minor, currencyCode = currency.ifBlank { data.currencyCode })
                    status = "Saved"
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save money settings") }

            Spacer(Modifier.height(24.dp))
            Section("Look")
            Text("Background", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeOptions.backgrounds.forEach { bg ->
                    TagChip(bg.label, data.backgroundMode == bg.id) {
                        vm.updateSettings(backgroundMode = bg.id)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Accent", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeOptions.accents.forEach { acc ->
                    val selected = data.colorScheme == acc.id
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(acc.colorArgb))
                            .then(
                                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .clickable { vm.updateSettings(colorScheme = acc.id) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Section("On-device AI")
            Text(
                "Speak or type to log. Pattern matching always works offline. " +
                    "Needle + Whisper is a one-time install, then airplane-mode.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(packStatus, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        CaptureRuntime.ensureModels(context, install = true)
                        status = if (CaptureRuntime.needleReady.value) "Voice pack ready" else "Install failed — typed capture still works"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (needleReady && sttReady) "Re-check voice pack" else "Install Needle + Whisper")
            }

            Spacer(Modifier.height(24.dp))
            Section("Reminders")
            Text(
                "Day before at 20:00 and morning-of at 08:00 for Wants.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Want reminders", modifier = Modifier.weight(1f))
                Switch(
                    checked = data.wantRemindersEnabled,
                    onCheckedChange = { vm.updateSettings(wantRemindersEnabled = it) }
                )
            }

            Spacer(Modifier.height(24.dp))
            WatchSection()

            Spacer(Modifier.height(24.dp))
            Section("Backup")
            Text(
                "Everything stays on this phone. Export a JSON file you control.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { exportLauncher.launch("life-backup-${LocalDate.now()}.json") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Export JSON") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Import JSON") }

            Spacer(Modifier.height(24.dp))
            Section("About")
            Text(
                "Life is local-only. No account, no cloud, no ads. Habits, notes, and money on one timeline. Voice never leaves the phone.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            status?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prefs by remember { mutableStateOf(WatchPreferences()) }
    var packageText by remember { mutableStateOf(WatchSettings.DEFAULT_GB_PACKAGE) }
    var morningText by remember { mutableStateOf("8") }
    var eveningText by remember { mutableStateOf("20") }
    var statusLabel by remember { mutableStateOf("Off") }
    var debugText by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    val notificationsOff = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

    LaunchedEffect(Unit) {
        val store = WatchSettingsStore(context.applicationContext)
        val loaded = store.load()
        prefs = loaded
        packageText = loaded.gadgetbridgePackage
        morningText = loaded.morningHour.toString()
        eveningText = loaded.eveningHour.toString()
        statusLabel = GadgetbridgeClient.statusLabel(context.applicationContext, loaded)
        debugText = store.loadDebug()
    }

    fun hour(text: String, fallback: Int): Int = text.trim().toIntOrNull()?.coerceIn(0, 23) ?: fallback

    fun edited(base: WatchPreferences): WatchPreferences = base.copy(
        gadgetbridgePackage = packageText.trim().ifBlank { WatchSettings.DEFAULT_GB_PACKAGE },
        morningHour = hour(morningText, base.morningHour),
        eveningHour = hour(eveningText, base.eveningHour)
    )

    fun persist(next: WatchPreferences) {
        scope.launch {
            val app = context.applicationContext
            val store = WatchSettingsStore(app)
            val saved = next.copy(
                morningHour = next.morningHour.coerceIn(0, 23),
                eveningHour = next.eveningHour.coerceIn(0, 23),
                gadgetbridgePackage = next.gadgetbridgePackage.trim().ifBlank { WatchSettings.DEFAULT_GB_PACKAGE }
            )
            store.save(saved)
            prefs = saved
            packageText = saved.gadgetbridgePackage
            morningText = saved.morningHour.toString()
            eveningText = saved.eveningHour.toString()
            statusLabel = GadgetbridgeClient.registerListener(app, saved)
            DigestScheduler.scheduleAll(app, saved)
            debugText = store.loadDebug()
            notice = "Saved"
        }
    }

    Section("Watch")
    Text(
        "Listener talks to Gadgetbridge (Zepp test) until Nightly has the bridge.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Watch listener", modifier = Modifier.weight(1f))
        Switch(
            checked = prefs.enabled,
            onCheckedChange = { persist(edited(prefs).copy(enabled = it)) }
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(statusLabel, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = packageText,
        onValueChange = { packageText = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Gadgetbridge package") },
        singleLine = true
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Morning digest", modifier = Modifier.weight(1f))
        Switch(
            checked = prefs.morningDigestEnabled,
            onCheckedChange = { persist(edited(prefs).copy(morningDigestEnabled = it)) }
        )
    }
    OutlinedTextField(
        value = morningText,
        onValueChange = { morningText = it.filter { ch -> ch.isDigit() }.take(2) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Morning hour") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Evening digest", modifier = Modifier.weight(1f))
        Switch(
            checked = prefs.eveningDigestEnabled,
            onCheckedChange = { persist(edited(prefs).copy(eveningDigestEnabled = it)) }
        )
    }
    OutlinedTextField(
        value = eveningText,
        onValueChange = { eveningText = it.filter { ch -> ch.isDigit() }.take(2) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Evening hour") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { persist(edited(prefs)) },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Save watch settings") }
    if (notificationsOff) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Notification permission is off, so digests will not fire.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
    }
    Spacer(Modifier.height(12.dp))
    Text("Debug log", fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(6.dp))
    Text(
        debugText.ifBlank { "No watch events yet." },
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Copy debug log", debugText))
            notice = "Debug log copied"
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Copy debug log") }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, debugText)
            }
            context.startActivity(Intent.createChooser(send, "Copy debug log"))
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Share debug log") }
    notice?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
