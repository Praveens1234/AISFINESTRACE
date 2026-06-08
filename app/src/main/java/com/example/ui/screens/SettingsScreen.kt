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

        // GROUP 3: Alert Defaults
        item {
            SettingsGroupHeader("3. Alert Defaults", defaultsExpanded) { defaultsExpanded = !defaultsExpanded }
            if (defaultsExpanded) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Default crossing priority set for quick creation fields is labeled HIGH by default.", fontSize = 13.sp)
                        Text("Alarms and calling alerts will play notification audio coupled with device haptic vibrations.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            confirmButton = {
                Button(onClick = { showExportDialog = false }) { Text("Dismiss") }
            },
            title = { Text("Clipboard Backup Export") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Copy your exported crossing backup JSON below to safe-keep your rules:")
                    val backupJson = "{\"app\":\"FinTrace\",\"export_version\":1,\"timestamp\":${System.currentTimeMillis()},\"rules\":[]}"
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
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        // Simulating JSON parse importing safely
                        showImportDialog = false
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
                        onValueChange = { importInput = it },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        placeholder = { Text("Paste JSON backing string...") },
                        shape = RoundedCornerShape(8.dp)
                    )
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
