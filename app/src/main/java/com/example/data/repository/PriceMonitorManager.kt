package com.example.data.repository

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.example.data.database.AppDatabase
import com.example.data.model.Alert
import com.example.data.model.AppSetting
import com.example.data.model.PriceTick
import com.example.data.model.SymbolInfo
import com.example.data.model.SymbolState
import com.example.data.model.TriggerHistory
import com.example.service.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class PriceMonitorManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    val db: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "fintrace_database"
    ).fallbackToDestructiveMigration().build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var connectionJob: Job? = null
    private var simTickJob: Job? = null

    // Price Cache & State Flow
    private val _priceState = MutableStateFlow<Map<String, PriceTick>>(emptyMap())
    val priceState: StateFlow<Map<String, PriceTick>> = _priceState.asStateFlow()

    // Active symbols state cache
    private val _activeSymbols = MutableStateFlow<List<String>>(listOf("XAU/USD", "EUR/USD", "GBP/USD", "USD/JPY"))
    val activeSymbols: StateFlow<List<String>> = _activeSymbols.asStateFlow()

    // Connection Status State Flow
    // "LIVE", "RECONNECTING", "OFFLINE"
    private val _connectionStatus = MutableStateFlow("LIVE")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _latencyMs = MutableStateFlow(0L)
    val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

    private var activeWebSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    init {
        // Initialize Default Symbols and load current selection
        scope.launch(Dispatchers.IO) {
            setupInitialSymbolsIfNeeded()
            loadActiveSymbols()
            initializePriceCache()
            startMonitoringLoop()
        }
    }

    private suspend fun setupInitialSymbolsIfNeeded() {
        val existing = db.symbolStateDao().getAllSymbolStates()
        if (existing.isEmpty()) {
            val initialList = SymbolInfo.ALL.mapIndexed { index, info ->
                val isActive = info.symbol in listOf("XAU/USD", "EUR/USD", "GBP/USD", "USD/JPY")
                SymbolState(info.symbol, isActive, index)
            }
            db.symbolStateDao().insertSymbolStates(initialList)
        }
    }

    private suspend fun loadActiveSymbols() {
        db.symbolStateDao().getAllSymbolStatesFlow().collect { list ->
            val active = list.filter { it.isActive }.map { it.symbol }
            _activeSymbols.value = active
        }
    }

    private fun initializePriceCache() {
        val initialMap = mutableMapOf<String, PriceTick>()
        SymbolInfo.ALL.forEach { s ->
            initialMap[s.symbol] = PriceTick(
                symbol = s.symbol,
                price = s.defaultPrice,
                bid = s.defaultPrice - (0.0004 * s.defaultPrice),
                ask = s.defaultPrice + (0.0004 * s.defaultPrice),
                history = listOf(s.defaultPrice)
            )
        }
        _priceState.value = initialMap
    }

    fun startMonitoringLoop() {
        scope.launch {
            val apiKeySetting = getSetting("twelve_data_api_key")
            if (apiKeySetting.isNullOrBlank()) {
                _connectionStatus.value = "LIVE" // simulated live
                startSimulation()
            } else {
                connectToTwelveData(apiKeySetting)
            }
        }
    }

    fun stopMonitoring() {
        connectionJob?.cancel()
        simTickJob?.cancel()
        activeWebSocket?.close(1000, "App closed")
        activeWebSocket = null
    }

    // ── SIMULATION ENGINE ──────────────────────────────────────────────────
    private fun startSimulation() {
        connectionJob?.cancel()
        simTickJob?.cancel()
        simTickJob = scope.launch {
            while (true) {
                val delayTime = getUiPriceIntervalSetting()
                delay(delayTime)
                tickSimulatedPrices()
            }
        }
    }

    private suspend fun tickSimulatedPrices() {
        val activeList = _activeSymbols.value
        val currentPrices = _priceState.value.toMutableMap()

        activeList.forEach { sym ->
            val info = SymbolInfo.find(sym)
            val currentTick = currentPrices[sym] ?: PriceTick(sym, info.defaultPrice)
            val prevPrice = currentTick.price

            // Random walk simulation (fractional Brownian noise simulator)
            val volatility = when (info.category) {
                "Metals" -> 0.0003
                "Majors" -> 0.00008
                else -> 0.00015
            }
            val changePercent = (Random.nextDouble() - 0.5) * 2.0 * volatility
            val priceChange = prevPrice * changePercent
            val newPrice = (prevPrice + priceChange).coerceAtLeast(0.0001)

            // Calculate bid-ask
            val spreadFactor = when (info.category) {
                "Metals" -> 0.0002
                "Majors" -> 0.0001
                else -> 0.00015
            }
            val spreadVal = newPrice * spreadFactor
            val bid = newPrice - (spreadVal / 2)
            val ask = newPrice + (spreadVal / 2)

            val netChange = newPrice - info.defaultPrice
            val netChangePct = (netChange / info.defaultPrice) * 100

            val oldHistory = currentTick.history
            val newHistory = (oldHistory + newPrice).takeLast(20)

            val updatedTick = PriceTick(
                symbol = sym,
                price = newPrice,
                change = netChange,
                changePercent = netChangePct,
                bid = bid,
                ask = ask,
                history = newHistory
            )

            currentPrices[sym] = updatedTick
            evaluateAlerts(sym, prevPrice, newPrice)
        }

        _priceState.value = currentPrices
        _latencyMs.value = Random.nextLong(20, 150)
        
        // Sync Notification with compact detail
        NotificationHelper.updateTickerNotification(appContext, currentPrices, _activeSymbols.value)
        com.example.service.PriceWidgetProvider.updateWidgets(appContext, currentPrices)
    }

    // ── TWELVE DATA SOCKET INTEGRATION ────────────────────────────────────
    private fun connectToTwelveData(apiKey: String) {
        simTickJob?.cancel()
        connectionJob?.cancel()
        connectionJob = scope.launch {
            _connectionStatus.value = "CONNECTING"
            val request = Request.Builder()
                .url("wss://ws.twelvedata.com/v1/quotes/price?apikey=$apiKey")
                .build()

            activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    _connectionStatus.value = "LIVE"
                    // Subscribe active symbols
                    val activeList = _activeSymbols.value
                    if (activeList.isNotEmpty()) {
                        val symString = activeList.joinToString(",")
                        val subMsg = JSONObject()
                        subMsg.put("action", "subscribe")
                        val params = JSONObject()
                        params.put("symbols", symString)
                        subMsg.put("params", params)
                        webSocket.send(subMsg.toString())
                    }
                    
                    // Start heartbeat ticker
                    startHeartbeat(webSocket)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleWsMessage(text)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _connectionStatus.value = "OFFLINE"
                    attemptReconnect(apiKey)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    _connectionStatus.value = "RECONNECTING"
                    attemptReconnect(apiKey)
                }
            })
        }
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        scope.launch {
            while (_connectionStatus.value == "LIVE" && activeWebSocket == webSocket) {
                delay(25000)
                try {
                    val hb = JSONObject()
                    hb.put("action", "heartbeat")
                    webSocket.send(hb.toString())
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    private fun attemptReconnect(apiKey: String) {
        scope.launch {
            delay(5000)
            if (_connectionStatus.value != "LIVE") {
                connectToTwelveData(apiKey)
            }
        }
    }

    private fun handleWsMessage(text: String) {
        try {
            val json = JSONObject(text)
            val event = json.optString("event")
            if (event == "price") {
                val sym = json.optString("symbol")
                val priceVal = json.optDouble("price")
                if (!sym.isNullOrBlank() && !priceVal.isNaN()) {
                    scope.launch {
                        processReceivedPrice(sym, priceVal)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PriceMonitor", "WS Error: ${e.message}")
        }
    }

    private suspend fun processReceivedPrice(sym: String, newPrice: Double) {
        val info = SymbolInfo.find(sym)
        val currentPrices = _priceState.value.toMutableMap()
        val currentTick = currentPrices[sym] ?: PriceTick(sym, info.defaultPrice)
        val prevPrice = currentTick.price

        val netChange = newPrice - info.defaultPrice
        val netChangePct = (netChange / info.defaultPrice) * 100

        val bid = newPrice - (newPrice * 0.0001)
        val ask = newPrice + (newPrice * 0.0001)

        val oldHistory = currentTick.history
        val newHistory = (oldHistory + newPrice).takeLast(20)

        val updatedTick = PriceTick(
            symbol = sym,
            price = newPrice,
            change = netChange,
            changePercent = netChangePct,
            bid = bid,
            ask = ask,
            history = newHistory
        )

        currentPrices[sym] = updatedTick
        _priceState.update { currentPrices }
        
        // Evaluate alerts!
        evaluateAlerts(sym, prevPrice, newPrice)

        NotificationHelper.updateTickerNotification(appContext, currentPrices, _activeSymbols.value)
        com.example.service.PriceWidgetProvider.updateWidgets(appContext, currentPrices)
    }

    // ── EVALUATE ALERTS ───────────────────────────────────────────────────
    private suspend fun evaluateAlerts(symbol: String, prevPrice: Double, currentPrice: Double) {
        val activeAlerts = db.alertDao().getActiveAlertsForSymbol(symbol)
        val now = System.currentTimeMillis()

        activeAlerts.forEach { alert ->
            // Check cooldown
            if (alert.cooldownUntil != null && now < alert.cooldownUntil) {
                return@forEach
            }
            // Check expiration
            if (alert.expiry != null && now > alert.expiry) {
                db.alertDao().updateAlertActiveStatus(alert.id, false)
                return@forEach
            }

            var triggered = false
            when (alert.condition) {
                "CROSSING" -> {
                    triggered = (prevPrice < alert.targetPrice && currentPrice >= alert.targetPrice) ||
                            (prevPrice > alert.targetPrice && currentPrice <= alert.targetPrice)
                }
                "CROSSING_UP" -> {
                    triggered = prevPrice < alert.targetPrice && currentPrice >= alert.targetPrice
                }
                "CROSSING_DOWN" -> {
                    triggered = prevPrice > alert.targetPrice && currentPrice <= alert.targetPrice
                }
            }

            if (triggered) {
                // Fire notification based on configured method
                NotificationHelper.fireAlertNotification(appContext, alert, currentPrice)

                // Insert trigger history
                db.triggerHistoryDao().insertHistory(
                    TriggerHistory(
                        alertId = alert.id,
                        symbol = symbol,
                        priceAtTrigger = currentPrice,
                        triggeredAt = now,
                        method = alert.priority
                    )
                )

                // Cooldown / lifecycle management
                if (alert.isOneTime) {
                    db.alertDao().updateAlertActiveStatus(alert.id, false)
                } else {
                    val cooldownEnd = now + alert.cooldownDurationMs
                    db.alertDao().updateAlertCooldown(alert.id, cooldownEnd)
                }
            }
        }
    }

    // Settings helpers
    suspend fun getSetting(key: String): String? {
        return db.appSettingDao().getSetting(key)?.value
    }

    suspend fun saveSetting(key: String, value: String) {
        db.appSettingDao().insertSetting(AppSetting(key, value))
        // Auto restart loop if API key or update interval shifts
        if (key == "twelve_data_api_key") {
            startMonitoringLoop()
        } else if (key == "ui_price_update_interval_ms") {
            if (getSetting("twelve_data_api_key").isNullOrBlank()) {
                startSimulation() // restart simulation with new rate
            }
        }
    }

    suspend fun getUiPriceIntervalSetting(): Long {
        val raw = getSetting("ui_price_update_interval_ms")
        return raw?.toLongOrNull()?.coerceIn(100L, 10000L) ?: 500L
    }

    companion object {
        @Volatile
        private var INSTANCE: PriceMonitorManager? = null

        fun getInstance(context: Context): PriceMonitorManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PriceMonitorManager(context)
                INSTANCE = instance
                instance
            }
        }
    }
}
