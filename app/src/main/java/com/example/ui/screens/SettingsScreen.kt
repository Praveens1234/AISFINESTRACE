package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.data.model.SymbolInfo
import com.example.ui.theme.AlertCritical
import com.example.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateToAboutApp: () -> Unit,
    onNavigateToAboutDeveloper: () -> Unit,
    onNavigateToPermissions: () -> Unit
) {
    // Collect all setting values from the ViewModel
    val apiKey by viewModel.apiKey.collectAsState()
    val updateInterval by viewModel.priceUpdateIntervalMs.collectAsState()
    val cardStyle by viewModel.dashboardCardStyle.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val hapticEnabled by viewModel.hapticFeedbackEnabled.collectAsState()
    val autoStart by viewModel.autoStartOnBoot.collectAsState()
    val connLossThreshold by viewModel.connectionLossThreshold.collectAsState()
    val liveTickerSub by viewModel.liveTickerSymbols.collectAsState()
    val alerts by viewModel.alertList.collectAsState()

    val alertSoundUri by viewModel.alertSoundUri.collectAsState()
    val alertSoundTitle by viewModel.alertSoundTitle.collectAsState()
    val alertRingDurationSec by viewModel.alertRingDurationSec.collectAsState()
    val alertSoundMode by viewModel.alertSoundMode.collectAsState()

    val prioritySoundUris by viewModel.prioritySoundUris.collectAsState()
    val prioritySoundTitles by viewModel.prioritySoundTitles.collectAsState()
    val priorityRingDurations by viewModel.priorityRingDurations.collectAsState()
    val prioritySoundModes by viewModel.prioritySoundModes.collectAsState()

    var selectedScope by remember { mutableStateOf("Global") } // "Global", "Low", "Medium", "High", "Critical"
    var editingScopeForRingtone by remember { mutableStateOf("Global") }

    val context = androidx.compose.ui.platform.LocalContext.current

    val ringtonePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                @Suppress("DEPRECATION")
                val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    result.data?.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI, android.net.Uri::class.java)
                } else {
                    result.data?.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                }
                if (uri != null) {
                    val uriStr = uri.toString()
                    val title = android.media.RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "Custom Selected Tone"
                    if (editingScopeForRingtone == "Global") {
                        viewModel.saveAlertSoundUri(uriStr)
                        viewModel.saveAlertSoundTitle(title)
                    } else {
                        viewModel.savePrioritySoundUri(editingScopeForRingtone, uriStr)
                        viewModel.savePrioritySoundTitle(editingScopeForRingtone, title)
                    }
                } else {
                    if (editingScopeForRingtone == "Global") {
                        viewModel.saveAlertSoundUri("")
                        viewModel.saveAlertSoundTitle("Default System Tone")
                    } else {
                        viewModel.savePrioritySoundUri(editingScopeForRingtone, "")
                        viewModel.savePrioritySoundTitle(editingScopeForRingtone, "")
                    }
                }
            }
        }
    )

    var billingExpanded by remember { mutableStateOf(false) }
    var notificationExpanded by remember { mutableStateOf(false) }
    var defaultsExpanded by remember { mutableStateOf(false) }
    var testingExpanded by remember { mutableStateOf(false) }
    var appearanceExpanded by remember { mutableStateOf(false) }
    var behaviourExpanded by remember { mutableStateOf(false) }
    var dataStorageExpanded by remember { mutableStateOf(false) }
    var aboutExpanded by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // GROUP 1: Data Source Settings
        item {
            SettingsGroupHeader("1. Data Source Settings", billingExpanded) { billingExpanded = !billingExpanded }
            if (billingExpanded) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { viewModel.saveApiKey(it) },
                            label = { Text("Twelve Data API Key") },
                            placeholder = { Text("e.g. your_twelve_data_key") },
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Provide your own Twelve Data key for real market pricing feeds. If left blank, FinTrace uses Simulated Demo pricing mode automatically.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Divider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Price Throttle (ms)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("UI refresh rate (100ms - 10s)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { viewModel.savePriceUpdateInterval(updateInterval - 50) }) {
                                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = null)
                                }
                                Text("$updateInterval ms", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                                IconButton(onClick = { viewModel.savePriceUpdateInterval(updateInterval + 50) }) {
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }
        }

        // GROUP 2: Notifications
        item {
            SettingsGroupHeader("2. Notification Settings", notificationExpanded) { notificationExpanded = !notificationExpanded }
            if (notificationExpanded) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Shade Live Price Ticker", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Renders selected symbols dynamically", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val isEnabled = liveTickerSub.isNotEmpty()
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = {
                                    if (it) {
                                        viewModel.saveLiveTickerSymbols(listOf("XAU/USD", "EUR/USD"))
                                    } else {
                                        viewModel.saveLiveTickerSymbols(emptyList())
                                    }
                                }
                            )
                        }

                        if (liveTickerSub.isNotEmpty()) {
                            Text("Active Ticker Symbols (Select up to 5):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            FlowRowLayout(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SymbolInfo.ALL.take(10).forEach { sym ->
                                    val isChecked = liveTickerSub.contains(sym.symbol)
                                    FilterChip(
                                        selected = isChecked,
                                        onClick = {
                                            val newList = liveTickerSub.toMutableList()
                                            if (isChecked) newList.remove(sym.symbol) else newList.add(sym.symbol)
                                            viewModel.saveLiveTickerSymbols(newList.take(5))
                                        },
                                        label = { Text(sym.symbol, fontSize = 11.sp) },
                                        modifier = Modifier.padding(2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // GROUP 3: Alert Sounds & Speech
        item {
            SettingsGroupHeader("3. Alert Sounds & Speech", defaultsExpanded) { defaultsExpanded = !defaultsExpanded }
            if (defaultsExpanded) {
                val suffix = selectedScope.lowercase(java.util.Locale.US)
                val activeSoundMode = if (selectedScope == "Global") {
                    alertSoundMode
                } else {
                    prioritySoundModes[suffix]?.ifBlank { "" } ?: ""
                }

                val activeSoundTitle = if (selectedScope == "Global") {
                    alertSoundTitle
                } else {
                    prioritySoundTitles[suffix]?.ifBlank { "" } ?: ""
                }

                val activeSoundUri = if (selectedScope == "Global") {
                    alertSoundUri
                } else {
                    prioritySoundUris[suffix] ?: ""
                }

                val activeRingDurationSec = if (selectedScope == "Global") {
                    alertRingDurationSec
                } else {
                    priorityRingDurations[suffix] ?: alertRingDurationSec
                }

                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Scope Selector (Global vs Low vs Medium vs High vs Critical)
                        Text(
                            "Configuration Scope",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Global", "LOW", "MEDIUM", "HIGH", "CRITICAL").forEach { scope ->
                                val isSelected = selectedScope == scope
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedScope = scope },
                                    label = { Text(scope, fontSize = 12.sp) }
                                )
                            }
                        }

                        Text(
                            text = if (selectedScope == "Global") {
                                "Editing default settings applied as the baseline across all alerts."
                            } else {
                                "Editing custom override rules applied exclusively to $selectedScope priority alerts."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )

                        Divider()

                        // Sound Playback Mode Choices
                        Text(
                            "Sound Feedback Style",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        val modeOptions = if (selectedScope == "Global") {
                            listOf("Both Tone and Voice", "Tone alert only", "TTS voice only", "Silent")
                        } else {
                            listOf("Inherit Global", "Both Tone and Voice", "Tone alert only", "TTS voice only", "Silent")
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            modeOptions.forEach { mode ->
                                val selected = if (selectedScope == "Global") {
                                    activeSoundMode == mode
                                } else {
                                    if (mode == "Inherit Global") activeSoundMode.isEmpty() else activeSoundMode == mode
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            val modeToSave = if (mode == "Inherit Global") "" else mode
                                            if (selectedScope == "Global") {
                                                viewModel.saveAlertSoundMode(modeToSave)
                                            } else {
                                                viewModel.savePrioritySoundMode(suffix, modeToSave)
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = {
                                            val modeToSave = if (mode == "Inherit Global") "" else mode
                                            if (selectedScope == "Global") {
                                                viewModel.saveAlertSoundMode(modeToSave)
                                            } else {
                                                viewModel.savePrioritySoundMode(suffix, modeToSave)
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = mode,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                        val desc = when (mode) {
                                            "Inherit Global" -> "Default settings are inherited from global alert behavior (${alertSoundMode})."
                                            "Both Tone and Voice" -> "Plays your custom sound combined with spoke-aloud price crossing announcements."
                                            "Tone alert only" -> "Plays only your selected warning chime/sound without reading anything."
                                            "TTS voice only" -> "Speaks the crossing prices aloud without playing ringtone audio."
                                            "Silent" -> "Disables all alert soundtracks (uses device visual notifications only)."
                                            else -> ""
                                        }
                                        Text(
                                            text = desc,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Divider()

                        // Custom Device Ringtone Picker
                        Text(
                            "Selected Alarm sound",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    val intent = android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_ALL)
                                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alert Sound ($selectedScope)")
                                        try {
                                            if (activeSoundUri.isNotEmpty()) {
                                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, android.net.Uri.parse(activeSoundUri))
                                            }
                                        } catch (e: Exception) {}
                                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                    }
                                    editingScopeForRingtone = selectedScope
                                    ringtonePickerLauncher.launch(intent)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Device Sound Tone", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = if (selectedScope != "Global" && activeSoundTitle.isEmpty()) {
                                            "Inherit Global (${alertSoundTitle.ifBlank { "Default System Tone" }})"
                                        } else {
                                            activeSoundTitle.ifBlank { "Default System Tone" }
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Text(
                                "CHANGE",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (selectedScope != "Global" && activeSoundUri.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    viewModel.savePrioritySoundUri(suffix, "")
                                    viewModel.savePrioritySoundTitle(suffix, "")
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Reset to Inherited Sound", fontSize = 12.sp)
                            }
                        }

                        Divider()

                        // Dynamic Alert Ringing Playback limits
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Alarm Ring Duration",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "$activeRingDurationSec seconds",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Slider(
                            value = activeRingDurationSec.toFloat(),
                            onValueChange = {
                                val valInt = it.toInt()
                                if (selectedScope == "Global") {
                                    viewModel.saveAlertRingDurationSec(valInt)
                                } else {
                                    viewModel.savePriorityRingDurationSec(suffix, valInt)
                                }
                            },
                            valueRange = 1f..60f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Quick Select Chips for easy tuning
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(2, 5, 10, 15, 30).forEach { sec ->
                                val isSel = activeRingDurationSec == sec
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        if (selectedScope == "Global") {
                                            viewModel.saveAlertRingDurationSec(sec)
                                        } else {
                                            viewModel.savePriorityRingDurationSec(suffix, sec)
                                        }
                                    },
                                    label = { Text("${sec}s") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // GROUP 4: Alert Testing (DEMO ALERTS)
        item {
            SettingsGroupHeader("4. Verification & Testing", testingExpanded) { testingExpanded = !testingExpanded }
            if (testingExpanded) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Immediately fire sample deliveries to test and verify your system configurations out of the box.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        Button(
                            onClick = { viewModel.testDemoAlert("DEFAULT_NOTIFICATION") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Test Default Heads-up")
                        }
                        
                        Button(
                            onClick = { viewModel.testDemoAlert("ALARM") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Test Priority Alarm banner")
                        }
                    }
                }
            }
        }

        // GROUP 5: Appearance settings
        item {
            SettingsGroupHeader("5. Appearance & Styling", appearanceExpanded) { appearanceExpanded = !appearanceExpanded }
            if (appearanceExpanded) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text("Theme Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Light", "AMOLED", "System").forEach { mode ->
                                    val isSel = themeMode == mode
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { viewModel.saveThemeMode(mode) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(mode, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }

                        Divider()

                        Column {
                            Text("Dashboard Card Style", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Standard", "Compact").forEach { style ->
                                    val isSel = cardStyle == style
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { viewModel.saveDashboardCardStyle(style) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(style, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // GROUP 6: App Behaviour toggle
        item {
            SettingsGroupHeader("6. App Operations", behaviourExpanded) { behaviourExpanded = !behaviourExpanded }
            if (behaviourExpanded) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Haptic Feedback Toggles", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Vibrate discreetly on price fluctuation ticks", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = hapticEnabled,
                                onCheckedChange = { viewModel.saveHapticFeedbackEnabled(it) }
                            )
                        }

                        Divider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Auto-start on Boot", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Restores FGS tracker automatically after restart", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = autoStart,
                                onCheckedChange = { viewModel.saveAutoStartOnBoot(it) }
                            )
                        }
                    }
                }
            }
        }

        // GROUP 7: Data & storage, reset, copy paste backup configuration
        item {
            SettingsGroupHeader("7. Storage & Portfolio Backups", dataStorageExpanded) { dataStorageExpanded = !dataStorageExpanded }
            if (dataStorageExpanded) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { showExportDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export Rules", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { showImportDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Input, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import Rules", fontSize = 12.sp)
                            }
                        }

                        Divider()

                        Button(
                            onClick = { viewModel.clearTriggerHistory() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear Alerts History Logs")
                        }

                        Button(
                            onClick = { viewModel.resetAllSettings() },
                            colors = ButtonDefaults.buttonColors(containerColor = AlertCritical, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Restore Preferences Defaults")
                        }
                    }
                }
            }
        }

        // GROUP 8: About information
        item {
            SettingsGroupHeader("8. General & Support", aboutExpanded) { aboutExpanded = !aboutExpanded }
            if (aboutExpanded) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column {
                        ListItem(
                            headlineContent = { Text("About FinTrace Info", fontWeight = FontWeight.Bold) },
                            supportingContent = { Text("Version stack, licenses, architecture summary") },
                            leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                            modifier = Modifier.clickable(onClick = onNavigateToAboutApp)
                        )
                        ListItem(
                            headlineContent = { Text("About Developer Contact", fontWeight = FontWeight.Bold) },
                            supportingContent = { Text("Portfolio, contact channels, licensing profiles") },
                            leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                            modifier = Modifier.clickable(onClick = onNavigateToAboutDeveloper)
                        )
                        ListItem(
                            headlineContent = { Text("Check System Permissions", fontWeight = FontWeight.Bold) },
                            supportingContent = { Text("Exemption whitelist, notification flags verification") },
                            leadingContent = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                            modifier = Modifier.clickable(onClick = onNavigateToPermissions)
                        )
                    }
                }
            }
        }
    }

    // Export rule Dialog
    if (showExportDialog) {
        val backupJson = remember(alerts) {
            try {
                val rootObj = org.json.JSONObject()
                rootObj.put("app", "FinTrace")
                rootObj.put("export_version", 1)
                rootObj.put("timestamp", System.currentTimeMillis())
                val rulesArray = org.json.JSONArray()
                alerts.forEach { alert ->
                    val alertObj = org.json.JSONObject()
                    alertObj.put("symbol", alert.symbol)
                    alertObj.put("condition", alert.condition)
                    alertObj.put("targetPrice", alert.targetPrice)
                    alertObj.put("title", alert.title)
                    alertObj.put("message", alert.message)
                    alertObj.put("isActive", alert.isActive)
                    alertObj.put("isOneTime", alert.isOneTime)
                    alertObj.put("priority", alert.priority)
                    alertObj.put("colorTagIndex", alert.colorTagIndex)
                    alertObj.put("cooldownDurationMs", alert.cooldownDurationMs)
                    if (alert.expiry != null) {
                        alertObj.put("expiry", alert.expiry)
                    }
                    rulesArray.put(alertObj)
                }
                rootObj.put("rules", rulesArray)
                rootObj.toString(2)
            } catch (e: Exception) {
                "{\"app\":\"FinTrace\",\"export_version\":1,\"rules\":[]}"
            }
        }

        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            confirmButton = {
                Button(onClick = { showExportDialog = false }) { Text("Dismiss") }
            },
            title = { Text("Clipboard Backup Export") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Copy your exported crossing backup JSON below to safe-keep your rules:")
                    OutlinedTextField(
                        value = backupJson,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Button(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(backupJson))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy to Clipboard")
                    }
                }
            }
        )
    }

    // Import rule Dialog
    if (showImportDialog) {
        var importInput by remember { mutableStateOf("") }
        var isSuccess by remember { mutableStateOf<Boolean?>(null) }

        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val ok = viewModel.importAlertsFromJson(importInput)
                        isSuccess = ok
                        if (ok) {
                            showImportDialog = false
                        }
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            },
            title = { Text("Backup Crossing Import") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste your exported configuration JSON backup below to restore your triggers:")
                    OutlinedTextField(
                        value = importInput,
                        onValueChange = { 
                            importInput = it
                            isSuccess = null
                        },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        placeholder = { Text("Paste JSON backing string...") },
                        shape = RoundedCornerShape(8.dp)
                    )
                    if (isSuccess == false) {
                        Text("Invalid JSON structure! Make sure to copy the exact exported text.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }
        )
    }
}

@Composable
fun SettingsGroupHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun FlowRowLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}
