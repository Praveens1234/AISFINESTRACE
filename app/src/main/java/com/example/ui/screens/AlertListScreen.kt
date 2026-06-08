package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Alert
import com.example.data.model.SymbolInfo
import com.example.data.model.formatPriceDynamic
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertListScreen(
    viewModel: MainViewModel
) {
    val alerts by viewModel.alertList.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // All, Active, Triggered

    Scaffold(
        floatingActionButton = {
            if (alerts.isEmpty()) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Alert")
                }
            }
        },
        bottomBar = {
            // Bulk actions footer if alerts exist
            if (alerts.isNotEmpty()) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.activateAllAlerts() }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Activate All",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deactivateAllAlerts() }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Deactivate All",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteAllAlerts() },
                                colors = IconButtonDefaults.iconButtonColors(contentColor = AlertCritical)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = "Delete All Alerts",
                                    tint = AlertCritical,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { showCreateDialog = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Create Alert")
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search Alerts...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                singleLine = true
            )

            // Filtering Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Active", "Triggered").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            // Alerts Stream filtering
            val filteredAlerts = alerts.filter { alert ->
                val matchesSearch = alert.symbol.contains(searchQuery, ignoreCase = true) ||
                        alert.title.contains(searchQuery, ignoreCase = true)
                val matchesFilter = when (selectedFilter) {
                    "Active" -> alert.isActive
                    "Triggered" -> !alert.isActive
                    else -> true
                }
                matchesSearch && matchesFilter
            }

            if (filteredAlerts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Alerts Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Create crossing targets to receive notifications",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredAlerts, key = { it.id }) { alert ->
                        AlertRuleItem(
                            alert = alert,
                            onToggleActive = { viewModel.toggleAlertActive(alert.id, it) },
                            onDelete = { viewModel.deleteAlert(alert.id) }
                        )
                    }
                }
            }
        }
    }

    // New Alert Modal dialog Box
    if (showCreateDialog) {
        CreateAlertDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { symbol, cond, price, isOneTime, priority ->
                viewModel.createAlert(
                    symbol = symbol,
                    condition = cond,
                    targetPrice = price,
                    title = "$symbol crossed target",
                    message = "Crossing detected. Price exceeded $price threshold.",
                    isOneTime = isOneTime,
                    priority = priority,
                    colorTagIndex = 0
                )
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun AlertRuleItem(
    alert: Alert,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val info = SymbolInfo.find(alert.symbol)
    val formattedPrice = alert.targetPrice.formatPriceDynamic(info.decimals)

    val condLabel = when (alert.condition) {
        "CROSSING_UP" -> "Crossing UP"
        "CROSSING_DOWN" -> "Crossing DOWN"
        else -> "Crossing"
    }

    val themeColor = when (alert.priority) {
        "LOW" -> AlertExpired
        "MEDIUM" -> AlertTriggered
        "HIGH" -> AlertActive
        "CRITICAL" -> AlertCritical
        else -> AlertActive
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (alert.isActive) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left priority vertical bar tag
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(6.dp)
                    .background(if (alert.isActive) themeColor else themeColor.copy(alpha = 0.4f))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = alert.symbol,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (alert.isActive) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(themeColor.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = alert.priority,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = themeColor,
                                fontSize = 9.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$condLabel at $$formattedPrice",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = PriceTextFontFamily,
                            fontWeight = FontWeight.Medium
                        ),
                        color = if (alert.isActive) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = if (alert.isOneTime) "One-time target" else "Repeating target",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Active status toggle switch
                    Switch(
                        checked = alert.isActive,
                        onCheckedChange = { onToggleActive(it) },
                        modifier = Modifier.scale(0.85f)
                    )
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Alert",
                            tint = AlertCritical.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAlertDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, Double, Boolean, String) -> Unit
) {
    var selectedSymbol by remember { mutableStateOf(SymbolInfo.ALL.first().symbol) }
    var condition by remember { mutableStateOf("CROSSING") } // "CROSSING", "CROSSING_UP", "CROSSING_DOWN"
    var targetPriceInput by remember { mutableStateOf("") }
    var isOneTime by remember { mutableStateOf(true) }
    var priority by remember { mutableStateOf("HIGH") } // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    
    var isExpandedSymbol by remember { mutableStateOf(false) }
    var isExpandedPriority by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val priceVal = targetPriceInput.toDoubleOrNull()
                    if (priceVal != null && priceVal > 0) {
                        onCreate(selectedSymbol, condition, priceVal, isOneTime, priority)
                    }
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("New Crossing Rule") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Symbol picker dropdown
                ExposedDropdownMenuBox(
                    expanded = isExpandedSymbol,
                    onExpandedChange = { isExpandedSymbol = !isExpandedSymbol }
                ) {
                    OutlinedTextField(
                        value = selectedSymbol,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Asset") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpandedSymbol) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = isExpandedSymbol,
                        onDismissRequest = { isExpandedSymbol = false }
                    ) {
                        SymbolInfo.ALL.forEach { s ->
                            DropdownMenuItem(
                                text = { Text("${s.symbol} (${s.name})") },
                                onClick = {
                                    selectedSymbol = s.symbol
                                    isExpandedSymbol = false
                                }
                            )
                        }
                    }
                }

                // Condition tabs
                Column {
                    Text("Trigger Condition", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("CROSSING", "CROSSING_UP", "CROSSING_DOWN").forEach { cond ->
                            val isSel = condition == cond
                            val color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            val textColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(color)
                                    .clickable { condition = cond }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cond.replace("_", " "),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Input target price field
                OutlinedTextField(
                    value = targetPriceInput,
                    onValueChange = { targetPriceInput = it },
                    label = { Text("Target Crossing Price") },
                    placeholder = { Text("e.g. 2318.50") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                // Priority switcher
                ExposedDropdownMenuBox(
                    expanded = isExpandedPriority,
                    onExpandedChange = { isExpandedPriority = !isExpandedPriority }
                ) {
                    OutlinedTextField(
                        value = priority,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Priority Level") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpandedPriority) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = isExpandedPriority,
                        onDismissRequest = { isExpandedPriority = false }
                    ) {
                        listOf("LOW", "MEDIUM", "HIGH", "CRITICAL").forEach { prio ->
                            DropdownMenuItem(
                                text = { Text(prio) },
                                onClick = {
                                    priority = prio
                                    isExpandedPriority = false
                                }
                            )
                        }
                    }
                }

                // Repeat toggler
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("One-Time Only", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Auto-mutes once the rule triggers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isOneTime,
                        onCheckedChange = { isOneTime = it }
                    )
                }
            }
        }
    )
}
