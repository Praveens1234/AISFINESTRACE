package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Alert
import com.example.data.model.PriceTick
import com.example.data.model.SymbolInfo
import com.example.data.model.SymbolState
import com.example.data.model.TriggerHistory
import com.example.data.repository.PriceMonitorManager
import com.example.service.NotificationHelper
import com.example.service.PriceTrackerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val monitor = PriceMonitorManager.getInstance(application)
    private val alertDao = monitor.db.alertDao()
    private val historyDao = monitor.db.triggerHistoryDao()
    private val symbolDao = monitor.db.symbolStateDao()
    private val settingsDao = monitor.db.appSettingDao()

    // ── DATA SOURCES EXPOSED AS REACTIVE FLOWS ──────────────────────────────
    val activeSymbols: StateFlow<List<String>> = monitor.activeSymbols
    val priceState: StateFlow<Map<String, PriceTick>> = monitor.priceState
    val connectionStatus: StateFlow<String> = monitor.connectionStatus
    val latencyMs: StateFlow<Long> = monitor.latencyMs

    // Live Alert List Flow
    val alertList: StateFlow<List<Alert>> = alertDao.getAllAlertsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live Trigger History Flow
    val triggerHistory: StateFlow<List<TriggerHistory>> = historyDao.getAllHistoryFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All symbols states configuration
    val symbolStates: StateFlow<List<SymbolState>> = symbolDao.getAllSymbolStatesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // State of key settings
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _priceUpdateIntervalMs = MutableStateFlow(500L)
    val priceUpdateIntervalMs: StateFlow<Long> = _priceUpdateIntervalMs.asStateFlow()

    private val _dashboardCardStyle = MutableStateFlow("Standard") // Standard, Compact, Detailed
    val dashboardCardStyle: StateFlow<String> = _dashboardCardStyle.asStateFlow()

    private val _themeMode = MutableStateFlow("AMOLED") // Light, AMOLED, System
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _hapticFeedbackEnabled = MutableStateFlow(true)
    val hapticFeedbackEnabled: StateFlow<Boolean> = _hapticFeedbackEnabled.asStateFlow()

    private val _autoStartOnBoot = MutableStateFlow(true)
    val autoStartOnBoot: StateFlow<Boolean> = _autoStartOnBoot.asStateFlow()

    private val _connectionLossThreshold = MutableStateFlow("30s") // 30s, 1m, 5m, 10m
    val connectionLossThreshold: StateFlow<String> = _connectionLossThreshold.asStateFlow()

    private val _liveTickerSymbols = MutableStateFlow<List<String>>(emptyList())
    val liveTickerSymbols: StateFlow<List<String>> = _liveTickerSymbols.asStateFlow()

    init {
        // Start background FGS automatically on launch
        PriceTrackerService.start(application)

        // Load settings values
        viewModelScope.launch(Dispatchers.IO) {
            _apiKey.value = monitor.getSetting("twelve_data_api_key") ?: ""
            _priceUpdateIntervalMs.value = monitor.getUiPriceIntervalSetting()
            _dashboardCardStyle.value = monitor.getSetting("dashboard_card_style") ?: "Standard"
            _themeMode.value = monitor.getSetting("theme_mode") ?: "AMOLED"
            _hapticFeedbackEnabled.value = (monitor.getSetting("haptic_feedback_enabled") ?: "true") == "true"
            _autoStartOnBoot.value = (monitor.getSetting("auto_start_on_boot") ?: "true") == "true"
            _connectionLossThreshold.value = monitor.getSetting("connection_loss_threshold") ?: "30s"
            
            val liveTickerRaw = monitor.getSetting("live_ticker_symbols") ?: "XAU/USD,EUR/USD"
            _liveTickerSymbols.value = liveTickerRaw.split(",").filter { it.isNotBlank() }
        }
    }

    // ── ALERT CONTROL MUTATIONS ────────────────────────────────────────────
    fun createAlert(
        symbol: String,
        condition: String,
        targetPrice: Double,
        title: String,
        message: String,
        isOneTime: Boolean,
        priority: String,
        colorTagIndex: Int,
        cooldownDurationMs: Long = 300000L,
        expiry: Long? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val alert = Alert(
                symbol = symbol,
                condition = condition,
                targetPrice = targetPrice,
                title = title,
                message = message,
                isActive = true,
                isOneTime = isOneTime,
                priority = priority,
                colorTagIndex = colorTagIndex,
                cooldownDurationMs = cooldownDurationMs,
                expiry = expiry
            )
            alertDao.insertAlert(alert)
        }
    }

    fun toggleAlertActive(alertId: Int, isActive: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            alertDao.updateAlertActiveStatus(alertId, isActive)
        }
    }

    fun deleteAlert(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            alertDao.deleteAlertById(id)
            historyDao.deleteHistoryForAlert(id)
        }
    }

    // ── BULK MANAGEMENT METHODS ───────────────────────────────────────────
    fun activateAllAlerts() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = alertActiveStateList()
            list.forEach { alert ->
                alertDao.updateAlertActiveStatus(alert.id, true)
            }
        }
    }

    fun deactivateAllAlerts() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = alertActiveStateList()
            list.forEach { alert ->
                alertDao.updateAlertActiveStatus(alert.id, false)
            }
        }
    }

    fun deleteAllAlerts() {
        viewModelScope.launch(Dispatchers.IO) {
            alertDao.deleteAllAlerts()
            historyDao.clearAllHistory()
        }
    }

    private suspend fun alertActiveStateList(): List<Alert> = withContext(Dispatchers.IO) {
        alertDao.getAllAlerts()
    }

    // ── SYMBOL CONFIG EVENTS ─────────────────────────────────────────────
    fun toggleSymbolActive(symbol: String, isActive: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = symbolDao.getAllSymbolStates()
            val match = list.find { it.symbol == symbol }
            if (match != null) {
                symbolDao.updateSymbolState(match.copy(isActive = isActive))
            } else {
                val newState = SymbolState(symbol, isActive, list.size)
                symbolDao.insertSymbolStates(listOf(newState))
            }
        }
    }

    // ── SETTINGS MUTATORS ─────────────────────────────────────────────────
    fun saveApiKey(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _apiKey.value = key
            monitor.saveSetting("twelve_data_api_key", key)
        }
    }

    fun savePriceUpdateInterval(intervalMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val validated = intervalMs.coerceIn(100L, 10000L)
            _priceUpdateIntervalMs.value = validated
            monitor.saveSetting("ui_price_update_interval_ms", validated.toString())
        }
    }

    fun saveDashboardCardStyle(style: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _dashboardCardStyle.value = style
            monitor.saveSetting("dashboard_card_style", style)
        }
    }

    fun saveThemeMode(mode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _themeMode.value = mode
            monitor.saveSetting("theme_mode", mode)
        }
    }

    fun saveHapticFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _hapticFeedbackEnabled.value = enabled
            monitor.saveSetting("haptic_feedback_enabled", enabled.toString())
        }
    }

    fun saveAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _autoStartOnBoot.value = enabled
            monitor.saveSetting("auto_start_on_boot", enabled.toString())
        }
    }

    fun saveConnectionLossThreshold(threshold: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _connectionLossThreshold.value = threshold
            monitor.saveSetting("connection_loss_threshold", threshold)
        }
    }

    fun saveLiveTickerSymbols(symbols: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            _liveTickerSymbols.value = symbols
            monitor.saveSetting("live_ticker_symbols", symbols.joinToString(","))
        }
    }

    fun saveSettingGeneric(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            monitor.saveSetting(key, value)
        }
    }

    // ── DIAGNOSTIC CLEANERS ────────────────────────────────────────────────
    fun clearTriggerHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.clearAllHistory()
        }
    }

    fun resetAllSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            settingsDao.clearSettings()
            _apiKey.value = ""
            _priceUpdateIntervalMs.value = 500L
            _dashboardCardStyle.value = "Standard"
            _themeMode.value = "AMOLED"
            _hapticFeedbackEnabled.value = true
            _autoStartOnBoot.value = true
            _connectionLossThreshold.value = "30s"
            _liveTickerSymbols.value = listOf("XAU/USD", "EUR/USD")
            
            // Reinitialize empty settings in database
            monitor.saveSetting("theme_mode", "AMOLED")
            monitor.saveSetting("ui_price_update_interval_ms", "500")
            monitor.saveSetting("live_ticker_symbols", "XAU/USD,EUR/USD")
        }
    }

    // ── DEMORE ALERT TESTING DISPATCH ──────────────────────────────────────
    fun testDemoAlert(method: String) {
        viewModelScope.launch {
            NotificationHelper.fireDemoAlertNotification(getApplication(), method)
        }
    }
}
