package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PriceTick
import com.example.data.model.SymbolInfo
import com.example.data.model.formatPriceDynamic
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.text.DecimalFormat

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onSymbolSelected: (String) -> Unit,
    onQuickAlertRequest: (String) -> Unit
) {
    val activeSub by viewModel.activeSymbols.collectAsState()
    val priceState by viewModel.priceState.collectAsState()
    val connStatus by viewModel.connectionStatus.collectAsState()
    val latency by viewModel.latencyMs.collectAsState()
    val cardStyle by viewModel.dashboardCardStyle.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Connection & Diagnostic Status Bar
        ConnectionStatusBar(
            status = connStatus,
            latency = latency,
            activeCount = activeSub.size
        )

        if (activeSub.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.AddChart,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Active Symbols",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Go to Settings -> Choose Assets to active your portfolio assets for real-time monitoring.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val gridCells = if (cardStyle == "Compact") GridCells.Fixed(2) else GridCells.Fixed(1)
            LazyVerticalGrid(
                columns = gridCells,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val activeTicks = activeSub.mapNotNull { priceState[it] }
                items(activeTicks, key = { it.symbol }) { tick ->
                    PriceMetricCard(
                        tick = tick,
                        cardStyle = cardStyle,
                        onTap = { onSymbolSelected(tick.symbol) },
                        onLongPress = { onQuickAlertRequest(tick.symbol) }
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusBar(
    status: String,
    latency: Long,
    activeCount: Int
) {
    val (dotColor, statusText) = when (status) {
        "LIVE" -> Pair(ConnectionLive, "LIVE")
        "CONNECTING" -> Pair(ConnectionReconnecting, "CONNECTING")
        else -> Pair(ConnectionOffline, "OFFLINE")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$statusText  ·  $activeCount Assets",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Text(
            text = "ping: ${latency}ms",
            style = MaterialTheme.typography.labelMedium,
            color = if (latency < 300) ConnectionLive else ConnectionReconnecting
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PriceMetricCard(
    tick: PriceTick,
    cardStyle: String,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val info = SymbolInfo.find(tick.symbol)


    // Flashing animations on price updates
    var prevPrice by remember { mutableStateOf(tick.price) }
    var flashColor by remember { mutableStateOf(Color.Transparent) }

    LaunchedEffect(tick.price) {
        if (tick.price > prevPrice) {
            flashColor = PriceUpDark.copy(alpha = 0.25f)
            delay(120)
            flashColor = Color.Transparent
        } else if (tick.price < prevPrice) {
            flashColor = PriceDownDark.copy(alpha = 0.25f)
            delay(120)
            flashColor = Color.Transparent
        }
        prevPrice = tick.price
    }

    val arrow = if (tick.change >= 0) "▲" else "▼"
    val changeColor = if (tick.change >= 0) PriceUpDark else PriceDownDark

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(flashColor)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = tick.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = info.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(changeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$arrow ${String.format("%.2f%%", kotlin.math.abs(tick.changePercent))}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = changeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Price Display
            Text(
                text = tick.price.formatPriceDynamic(info.decimals),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = PriceTextFontFamily,
                    fontSize = if (cardStyle == "Compact") 26.sp else 34.sp
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (cardStyle != "Compact") {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bid / Ask summary
                    Text(
                        text = "B: ${tick.bid.formatPriceDynamic(info.decimals)}  |  A: ${tick.ask.formatPriceDynamic(info.decimals)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = PriceTextFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Age / Timestamp
                    Text(
                        text = "Live",
                        style = MaterialTheme.typography.labelSmall,
                        color = ConnectionLive
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Canvas sparkline showing latest history of prices
                if (tick.history.size > 1) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .padding(vertical = 4.dp)
                    ) {
                        val width = size.width
                        val height = size.height

                        val minVal = tick.history.minOrNull() ?: 0.0
                        val maxVal = tick.history.maxOrNull() ?: 1.0
                        val diff = if (maxVal - minVal > 0.0) maxVal - minVal else 1.0

                        val stepX = width / (tick.history.size - 1)
                        val path = Path()

                        tick.history.forEachIndexed { i, p ->
                            val x = i * stepX
                            val normalizeY = (p - minVal) / diff
                            val y = height - (normalizeY * height).toFloat()

                            if (i == 0) {
                                path.moveTo(x, y)
                            } else {
                                path.lineTo(x, y)
                            }
                        }

                        drawPath(
                            path = path,
                            color = changeColor,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}
