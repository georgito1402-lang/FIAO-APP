package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.AiChatMessage
import com.example.ai.AiRepository
import com.example.ai.AiUiState
import com.example.ai.buildBusinessSnapshot
import com.example.data.BcvRateService
import com.example.data.BusinessSettings
import com.example.data.CartItemDraft
import com.example.data.CloudSyncStatus
import com.example.data.CustomerSummary
import com.example.data.LedgerRepository
import com.example.data.MovementType
import com.example.data.MovementWithItems
import com.example.data.NetworkMonitor
import com.example.data.PaymentMethodBreakdownItem
import com.example.data.ProductEntity
import com.example.data.ReceiptPreviewData
import com.example.data.RecentMovementDisplay
import com.example.data.SettingsPreferences
import com.example.data.StoreConfig
import com.example.data.TodayTotals
import com.example.data.unwrapFirestoreException
import com.example.util.formatRate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "LedgerViewModel"

data class DashboardMetrics(
    val todaySoldCents: Long = 0L,
    val todayExpenseCents: Long = 0L,
    val totalReceivableCents: Long = 0L,
    val debtorsCount: Int = 0,
    val totalCustomers: Int = 0,
    val todayPaymentCents: Long = 0L
)

class LedgerViewModel(
    private val repository: LedgerRepository,
    private val settingsPreferences: SettingsPreferences,
    private val networkMonitor: NetworkMonitor,
    private val aiRepository: AiRepository = AiRepository(),
    private val bcvRateService: BcvRateService = BcvRateService()
) : ViewModel() {

    private val _isRefreshingRate = MutableStateFlow(false)
    val isRefreshingRate: StateFlow<Boolean> = _isRefreshingRate.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _aiMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val aiMessages: StateFlow<List<AiChatMessage>> = _aiMessages.asStateFlow()

    private val _aiUiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val aiUiState: StateFlow<AiUiState> = _aiUiState.asStateFlow()

    private var nextAiMessageId = 1L
    private var storeBootstrapped = false

    private fun <T> Flow<T>.handleStreamErrors(fallback: T): Flow<T> = catch { error ->
        val root = unwrapFirestoreException(error)
        if (error is CancellationException && root === error) throw error
        Log.e(TAG, "Firestore stream error", root)
        _toastMessage.value = "Error de sincronización: ${root.localizedMessage ?: "revisa tu conexión"}"
        emit(fallback)
    }

    val storeConfig: StateFlow<StoreConfig> = repository.observeStoreConfig()
        .handleStreamErrors(StoreConfig(storeCode = repository.storeCode))
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StoreConfig(storeCode = repository.storeCode)
        )

    val businessSettings: StateFlow<BusinessSettings> = combine(
        storeConfig,
        settingsPreferences.isDarkModeFlow,
        settingsPreferences.enableSalesModuleFlow,
        settingsPreferences.enableExpensesModuleFlow
    ) { cfg, isDark, salesEnabled, expensesEnabled ->
        BusinessSettings(
            storeCode = cfg.storeCode.ifBlank { repository.storeCode },
            exchangeRate = cfg.exchangeRate,
            rateLabel = cfg.rateLabel,
            rateUpdatedAtMillis = cfg.rateUpdatedAtMillis,
            businessName = cfg.businessName,
            isDarkMode = isDark,
            enableSalesModule = salesEnabled,
            enableExpensesModule = expensesEnabled
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BusinessSettings(storeCode = repository.storeCode)
    )

    val syncStatus: StateFlow<CloudSyncStatus> = combine(
        networkMonitor.isOnlineFlow,
        repository.syncMetadataFlow
    ) { isOnline, meta ->
        when {
            !isOnline -> CloudSyncStatus.OFFLINE
            meta.hasPendingWrites -> CloudSyncStatus.SYNCING
            else -> CloudSyncStatus.SYNCED
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = if (networkMonitor.isCurrentlyOnline()) CloudSyncStatus.SYNCED else CloudSyncStatus.OFFLINE
    )

    val products: StateFlow<List<ProductEntity>> = repository.observeActiveProducts()
        .handleStreamErrors(emptyList())
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val customers: StateFlow<List<CustomerSummary>> = repository.observeCustomerSummaries()
        .handleStreamErrors(emptyList())
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val allMovements: StateFlow<List<RecentMovementDisplay>> =
        repository.observeAllMovements()
            .handleStreamErrors(emptyList())
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    val recentTodayMovements: StateFlow<List<RecentMovementDisplay>> =
        repository.observeTodayRecentMovements(limit = 5)
            .handleStreamErrors(emptyList())
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    val todayPaymentBreakdown: StateFlow<List<PaymentMethodBreakdownItem>> =
        repository.observeTodayPaymentBreakdown()
            .handleStreamErrors(emptyList())
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    val dashboardMetrics: StateFlow<DashboardMetrics> = combine(
        customers,
        repository.observeTodayTotals().handleStreamErrors(TodayTotals())
    ) { customerList, todayTotals ->
        val debtors = customerList.filter { it.balanceCents > 0L }
        DashboardMetrics(
            todaySoldCents = todayTotals.todaySoldCents + todayTotals.todayPaymentCents,
            todayExpenseCents = todayTotals.todayExpenseCents,
            totalReceivableCents = debtors.sumOf { it.balanceCents },
            debtorsCount = debtors.size,
            totalCustomers = customerList.size,
            todayPaymentCents = todayTotals.todayPaymentCents
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardMetrics()
    )

    init {
        viewModelScope.launch {
            if (networkMonitor.isCurrentlyOnline()) {
                bootstrapStoreIfNeeded()
                refreshBcvRate(isAutomatic = true)
            }
        }
        viewModelScope.launch {
            networkMonitor.isOnlineFlow.collect { isOnline ->
                if (isOnline && !storeBootstrapped) {
                    bootstrapStoreIfNeeded()
                    refreshBcvRate(isAutomatic = true)
                }
            }
        }
    }

    private suspend fun bootstrapStoreIfNeeded() {
        repository.ensureInitialStoreAndDemoData()
            .onSuccess { storeBootstrapped = true }
            .onFailure { err ->
                val root = unwrapFirestoreException(err)
                Log.w(TAG, "Store bootstrap deferred or failed: ${root.message}")
            }
    }

    fun refreshBcvRate(isAutomatic: Boolean = false) {
        if (_isRefreshingRate.value) return
        if (isAutomatic && !networkMonitor.isCurrentlyOnline()) return
        viewModelScope.launch {
            _isRefreshingRate.value = true
            try {
                val promedio = bcvRateService.fetchOfficialBcvPromedio()
                if (promedio != null) {
                    repository.updateBcvRate(promedio).onFailure { err ->
                        if (!isAutomatic) {
                            _toastMessage.value = unwrapFirestoreException(err).localizedMessage
                                ?: "No se pudo guardar la tasa en la nube"
                        }
                    }
                    if (!isAutomatic) {
                        _toastMessage.value = "Tasa BCV actualizada: ${formatRate(promedio)}"
                    }
                } else if (!isAutomatic) {
                    _toastMessage.value = "No se pudo obtener la tasa; se mantiene la guardada"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "BCV rate refresh failed, preserving saved rate", e)
                if (!isAutomatic) {
                    _toastMessage.value = "Sin conexión: se mantiene la tasa guardada"
                }
            } finally {
                _isRefreshingRate.value = false
            }
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun observeCustomer(customerId: String): Flow<CustomerSummary?> =
        repository.observeCustomerSummary(customerId).handleStreamErrors(null)

    fun observeCustomerMovements(customerId: String): Flow<List<MovementWithItems>> =
        repository.observeCustomerMovements(customerId).handleStreamErrors(emptyList())

    fun updateBusinessSettings(rate: Double, label: String, businessName: String) {
        viewModelScope.launch {
            repository.updateStoreSettings(rate, label, businessName)
                .onSuccess { _toastMessage.value = "Configuración sincronizada en la nube" }
                .onFailure { err ->
                    _toastMessage.value = unwrapFirestoreException(err).localizedMessage
                        ?: "No se pudo actualizar la configuración"
                }
        }
    }

    fun toggleDarkMode(isDark: Boolean) {
        viewModelScope.launch {
            settingsPreferences.setDarkMode(isDark)
        }
    }

    fun toggleSalesModule(enabled: Boolean) {
        viewModelScope.launch {
            settingsPreferences.setEnableSalesModule(enabled)
        }
    }

    fun toggleExpensesModule(enabled: Boolean) {
        viewModelScope.launch {
            settingsPreferences.setEnableExpensesModule(enabled)
        }
    }

    fun resetDemoData() {
        viewModelScope.launch {
            val rate = businessSettings.value.exchangeRate
            repository.resetAndSeedDemoData(rate)
                .onSuccess { _toastMessage.value = "Datos de demostración restaurados" }
                .onFailure { err ->
                    _toastMessage.value = unwrapFirestoreException(err).localizedMessage
                        ?: "No se pudieron restaurar los datos"
                }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
                .onSuccess { _toastMessage.value = "Todos los datos han sido eliminados" }
                .onFailure { err ->
                    _toastMessage.value = unwrapFirestoreException(err).localizedMessage
                        ?: "No se pudieron eliminar los datos"
                }
        }
    }

    fun saveProduct(
        id: String = "",
        name: String,
        priceCents: Long,
        costCents: Long,
        stockQuantity: Int,
        isUnlimitedStock: Boolean,
        category: String,
        unit: String
    ) {
        viewModelScope.launch {
            repository.saveProduct(
                id = id,
                name = name,
                priceCents = priceCents,
                costCents = costCents,
                stockQuantity = stockQuantity,
                isUnlimitedStock = isUnlimitedStock,
                category = category,
                unit = unit
            ).onSuccess {
                _toastMessage.value = if (id.isBlank()) "Producto agregado" else "Producto actualizado"
            }.onFailure { err ->
                _toastMessage.value = unwrapFirestoreException(err).localizedMessage
                    ?: "Error al guardar el producto"
            }
        }
    }

    fun adjustProductStock(product: ProductEntity, delta: Int) {
        viewModelScope.launch {
            repository.adjustProductStock(
                productId = product.id,
                currentStock = product.stockQuantity,
                isUnlimited = product.isUnlimitedStock,
                delta = delta
            ).onFailure { err ->
                _toastMessage.value = unwrapFirestoreException(err).localizedMessage
                    ?: "No se pudo ajustar el inventario"
            }
        }
    }

    fun archiveProduct(productId: String) {
        viewModelScope.launch {
            repository.archiveProduct(productId)
                .onSuccess { _toastMessage.value = "Producto eliminado del inventario" }
                .onFailure { err ->
                    _toastMessage.value = unwrapFirestoreException(err).localizedMessage
                        ?: "No se pudo eliminar el producto"
                }
        }
    }

    fun saveCustomer(
        id: String = "",
        name: String,
        phone: String,
        notes: String,
        onSaved: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.saveCustomer(
                id = id,
                name = name,
                phone = phone,
                notes = notes
            ).onSuccess { savedId ->
                _toastMessage.value = if (id.isBlank()) "Cliente agregado" else "Cliente actualizado"
                onSaved(savedId)
            }.onFailure { err ->
                _toastMessage.value = unwrapFirestoreException(err).localizedMessage
                    ?: "Error al guardar el cliente"
            }
        }
    }

    fun deleteCustomer(customerId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteCustomer(customerId)
                .onSuccess {
                    _toastMessage.value = "Cliente eliminado"
                    onDeleted()
                }
                .onFailure { err ->
                    _toastMessage.value = unwrapFirestoreException(err).localizedMessage
                        ?: "No se pudo eliminar el cliente"
                }
        }
    }

    fun recordQuickMovement(
        customerId: String?,
        type: String,
        totalCents: Long,
        paymentMethod: String,
        note: String,
        photoPath: String?,
        dueDateMillis: Long? = null,
        items: List<CartItemDraft> = emptyList(),
        onSaved: (String, ReceiptPreviewData?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val rate = businessSettings.value.exchangeRate
            repository.recordQuickMovement(
                customerId = customerId,
                type = type,
                totalCents = totalCents,
                paymentMethod = paymentMethod,
                note = note,
                photoPath = photoPath,
                rateUsed = rate,
                dueDateMillis = dueDateMillis,
                items = items,
                currentProducts = products.value
            ).onSuccess { movementId ->
                _toastMessage.value = when (type) {
                    MovementType.SALE -> "Venta registrada"
                    MovementType.CREDIT -> "Fiado anotado"
                    MovementType.EXPENSE -> "Gasto registrado"
                    MovementType.PAYMENT -> "Abono registrado"
                    else -> "Movimiento guardado"
                }

                val receipt = if ((type == MovementType.CREDIT || type == MovementType.PAYMENT) && !customerId.isNullOrBlank()) {
                    val cust = customers.value.firstOrNull { it.id == customerId }
                    if (cust != null) {
                        val newBalance = if (type == MovementType.CREDIT) {
                            cust.balanceCents + totalCents
                        } else {
                            cust.balanceCents - totalCents
                        }
                        val itemsSummary = if (items.isNotEmpty()) {
                            items.joinToString(", ") { "${it.quantity}x ${it.productName}" }
                        } else {
                            note.trim()
                        }
                        ReceiptPreviewData(
                            movementId = movementId,
                            customerName = cust.name,
                            customerPhone = cust.phone,
                            movementType = type,
                            amountCents = totalCents,
                            remainingBalanceCents = newBalance,
                            paymentMethod = paymentMethod,
                            note = note.trim(),
                            itemsSummary = itemsSummary,
                            createdAt = System.currentTimeMillis(),
                            dueDateMillis = dueDateMillis
                        )
                    } else null
                } else null

                onSaved(movementId, receipt)
            }.onFailure { err ->
                _toastMessage.value = unwrapFirestoreException(err).localizedMessage
                    ?: "No se pudo guardar el movimiento"
            }
        }
    }

    fun recordPayment(
        customer: CustomerSummary,
        amountCents: Long,
        paymentMethod: String,
        note: String,
        photoPath: String? = null,
        onReceiptReady: (ReceiptPreviewData) -> Unit = {}
    ) {
        recordQuickMovement(
            customerId = customer.id,
            type = MovementType.PAYMENT,
            totalCents = amountCents,
            paymentMethod = paymentMethod,
            note = note,
            photoPath = photoPath
        ) { _, receipt ->
            if (receipt != null) onReceiptReady(receipt)
        }
    }

    fun deleteMovement(movementId: String) {
        viewModelScope.launch {
            repository.deleteMovement(movementId)
                .onSuccess { _toastMessage.value = "Movimiento eliminado" }
                .onFailure { err ->
                    _toastMessage.value = unwrapFirestoreException(err).localizedMessage
                        ?: "No se pudo eliminar el movimiento"
                }
        }
    }

    fun askAi(rawQuestion: String) {
        val question = rawQuestion.trim()
        if (question.isEmpty()) return
        if (_aiUiState.value is AiUiState.Loading || _aiUiState.value is AiUiState.Streaming) return

        val historyBefore = _aiMessages.value
        val userMsg = AiChatMessage(id = nextAiMessageId++, isUser = true, text = question)
        _aiMessages.value = historyBefore + userMsg
        _aiUiState.value = AiUiState.Loading

        val snapshot = buildBusinessSnapshot(
            settings = businessSettings.value,
            metrics = dashboardMetrics.value,
            paymentBreakdown = todayPaymentBreakdown.value,
            customers = customers.value,
            products = products.value,
            allMovements = allMovements.value
        )

        viewModelScope.launch {
            var latestText = ""
            try {
                aiRepository.askStream(
                    question = question,
                    businessSnapshot = snapshot,
                    recentMessages = historyBefore
                ).collect { accumulated ->
                    latestText = accumulated
                    _aiUiState.value = AiUiState.Streaming(accumulated)
                }
                if (latestText.isNotBlank()) {
                    _aiMessages.value = _aiMessages.value + AiChatMessage(
                        id = nextAiMessageId++,
                        isUser = false,
                        text = latestText.trim()
                    )
                    _aiUiState.value = AiUiState.Idle
                } else {
                    _aiUiState.value = AiUiState.Error("El asistente no devolvió una respuesta. Intenta de nuevo.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "FIAO IA timeout", e)
                _aiUiState.value = AiUiState.Error(
                    "La consulta tardó más de 60 segundos. Verifica tu conexión e inténtalo de nuevo."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "FIAO IA inference failed", e)
                _aiUiState.value = AiUiState.Error(
                    e.localizedMessage ?: "No se pudo consultar a FIAO IA en este momento."
                )
            }
        }
    }

    fun clearAiChat() {
        _aiMessages.value = emptyList()
        _aiUiState.value = AiUiState.Idle
    }

    fun dismissAiError() {
        if (_aiUiState.value is AiUiState.Error) {
            _aiUiState.value = AiUiState.Idle
        }
    }

    companion object {
        fun factory(
            repository: LedgerRepository,
            settingsPreferences: SettingsPreferences,
            networkMonitor: NetworkMonitor
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LedgerViewModel(
                    repository = repository,
                    settingsPreferences = settingsPreferences,
                    networkMonitor = networkMonitor
                ) as T
            }
        }
    }
}
