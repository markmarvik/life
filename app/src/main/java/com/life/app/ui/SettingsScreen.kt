package com.life.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.app.data.AppData
import com.life.app.data.Money
import com.life.app.data.ThemeOptions
import java.time.LocalDate

private val COMMON_CURRENCIES = listOf("USD", "EUR", "GBP", "NOK", "SEK", "DKK", "JPY", "CAD", "AUD")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    data: AppData,
    vm: LifeViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
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
                "Life is local-only. No account, no cloud, no ads. Habits, notes, and money on one timeline.",
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

@Composable
private fun Section(title: String) {
    Text(
        title,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
