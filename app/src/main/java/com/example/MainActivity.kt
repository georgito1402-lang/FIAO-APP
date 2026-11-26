package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ai.configureAppCheck
import com.example.data.CloudSyncStatus
import com.example.data.LedgerRepository
import com.example.data.MovementType
import com.example.data.NetworkMonitor
import com.example.data.ProductEntity
import com.example.data.ReceiptPreviewData
import com.example.data.RecentMovementDisplay
import com.example.data.SettingsPreferences
import com.example.ui.LedgerViewModel
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.CustomerFormDialog
import com.example.ui.components.DigitalReceiptDialog
import com.example.ui.components.FiaoAiDialog
import com.example.ui.components.PhotoViewerDialog
import com.example.ui.components.ProductFormDialog
import com.example.ui.components.RateSettingsDialog
import com.example.ui.screens.CustomerDetailScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MovimientosScreen
import com.example.ui.screens.ProductsScreen
import com.example.ui.screens.QuickRegisterScreen
import com.example.ui.screens.StoreCodeScreen
import com.example.ui.theme.LocalLedgerColors
import com.example.ui.theme.MiLibretaTheme
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import kotlinx.coroutines.launch

object AppRoutes {
    const val ROUTE_HOME = "home"
    const val ROUTE_FIADOS = "fiados"
    const val ROUTE_MOVEMENTS = "movimientos"
    const val ROUTE_INVENTORY = "inventario"
    const val ROUTE_CUSTOMER_DETAIL = "customer_detail/{customerId}"
    const val ROUTE_QUICK_REGISTER = "quick_register?type={type}&customerId={customerId}&productId={productId}"

    fun customerDetail(customerId: String) = "customer_detail/${Uri.encode(customerId)}"
    fun quickRegister(
        type: String = MovementType.SALE,
        customerId: String? = null,
        productId: String? = null
    ): String {
        val cid = Uri.encode(customerId.orEmpty())
        val pid = Uri.encode(productId.orEmpty())
        return "quick_register?type=${Uri.encode(type)}&customerId=$cid&productId=$pid"
    }
}

private enum class TopTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(AppRoutes.ROUTE_HOME, "Inicio", Icons.Filled.Home, Icons.Outlined.Home),
    FIADOS(AppRoutes.ROUTE_FIADOS, "Fiados", Icons.Filled.Group, Icons.Outlined.Group),
    MOVEMENTS(AppRoutes.ROUTE_MOVEMENTS, "Movimientos", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
    INVENTORY(AppRoutes.ROUTE_INVENTORY, "Inventario", Icons.Filled.Inventory2, Icons.Outlined.Inventory2)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            FiaoAppRoot()
        }
    }
}

@Composable
fun FiaoAppRoot() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val settingsPreferences = remember(context) { SettingsPreferences(context) }
    val isDarkMode by settingsPreferences.isDarkModeFlow.collectAsStateWithLifecycle(initialValue = false)
    val savedStoreCode by settingsPreferences.storeCodeFlow.collectAsStateWithLifecycle(initialValue = null)
    var previousStoreCode by remember { mutableStateOf("") }

    val firestoreDb = remember(context) {
        val databaseId = context.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(databaseId).apply {
            try {
                firestoreSettings = firestoreSettings {
                    setLocalCacheSettings(persistentCacheSettings {})
                }
            } catch (_: IllegalStateException) {
                // Settings were already locked by a prior Firestore call
            }
        }
    }
    val networkMonitor = remember(context) { NetworkMonitor(context) }

    MiLibretaTheme(darkTheme = isDarkMode) {
        when (val activeCode = savedStoreCode) {
            null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            "" -> {
                if (previousStoreCode.isNotBlank()) {
                    BackHandler {
                        scope.launch { settingsPreferences.setStoreCode(previousStoreCode) }
                    }
                }
                StoreCodeScreen(
                    previousStoreCode = previousStoreCode,
                    onConfirmStoreCode = { code ->
                        scope.launch { settingsPreferences.setStoreCode(code) }
                    },
                    onCancelChange = if (previousStoreCode.isNotBlank()) {
                        { scope.launch { settingsPreferences.setStoreCode(previousStoreCode) } }
                    } else {
                        null
                    }
                )
            }

            else -> {
                key(activeCode) {
                    val repository = remember(activeCode, firestoreDb) {
                        LedgerRepository(db = firestoreDb, rawStoreCode = activeCode)
                    }
                    val viewModel: LedgerViewModel = viewModel(
                        key = activeCode,
                        factory = LedgerViewModel.factory(
                            repository = repository,
                            settingsPreferences = settingsPreferences,
                            networkMonitor = networkMonitor
                        )
                    )
                    FiaoAppContent(
                        viewModel = viewModel,
                        onChangeStoreCodeClick = {
                            previousStoreCode = activeCode
                            scope.launch { settingsPreferences.clearStoreCode() }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FiaoAppContent(
    viewModel: LedgerViewModel,
    onChangeStoreCodeClick: () -> Unit
) {

    val settings by viewModel.businessSettings.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val allMovements by viewModel.allMovements.collectAsStateWithLifecycle()
    val recentTodayMovements by viewModel.recentTodayMovements.collectAsStateWithLifecycle()
    val todayPaymentBreakdown by viewModel.todayPaymentBreakdown.collectAsStateWithLifecycle()
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()
    val isRefreshingRate by viewModel.isRefreshingRate.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val aiMessages by viewModel.aiMessages.collectAsStateWithLifecycle()
    val aiUiState by viewModel.aiUiState.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMessage) {
        val msg = toastMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearToast()
    }

    var showRateDialog by remember { mutableStateOf(false) }
    var showConfirmClearAll by remember { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }
    var showCustomerDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var showNewProductDialog by remember { mutableStateOf(false) }
    var productToDelete by remember { mutableStateOf<ProductEntity?>(null) }
    var movementIdToDelete by remember { mutableStateOf<String?>(null) }
    var activeReceipt by remember { mutableStateOf<ReceiptPreviewData?>(null) }
    var viewingPhotoPath by remember { mutableStateOf<String?>(null) }

    val topTabRoutes = remember { TopTab.entries.map { it.route }.toSet() }
    val isTopLevelRoute = currentRoute in topTabRoutes

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openReceiptFromMovement(item: RecentMovementDisplay) {
        val mov = item.movementWithItems.movement
        val cid = mov.customerId
        val cust = customers.firstOrNull { it.id == cid }
        val summaryText = item.movementWithItems.items
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { "${it.quantity}x ${it.productName}" }
            ?: mov.note
        activeReceipt = ReceiptPreviewData(
            movementId = mov.id,
            customerName = item.customerName ?: cust?.name ?: "Cliente",
            customerPhone = item.customerPhone ?: cust?.phone ?: "",
            movementType = mov.type,
            amountCents = mov.totalCents,
            remainingBalanceCents = cust?.balanceCents ?: mov.totalCents,
            paymentMethod = mov.paymentMethod,
            note = mov.note,
            itemsSummary = summaryText,
            createdAt = mov.createdAt,
            dueDateMillis = mov.dueDateMillis
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavigationRail && isTopLevelRoute) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxHeight(),
                    header = {
                        FloatingActionButton(
                            onClick = { navController.navigate(AppRoutes.quickRegister(MovementType.CREDIT)) },
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .testTag("rail_fab_quick_register")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Registrar operación")
                        }
                    }
                ) {
                    Spacer(modifier = Modifier.weight(0.15f))
                    TopTab.entries.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateToTab(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label, maxLines = 1, softWrap = false) },
                            modifier = Modifier.testTag("nav_rail_${tab.route}")
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = if (isTopLevelRoute) WindowInsets.safeDrawing else WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    if (isTopLevelRoute) {
                        FiaoTopUtilityBar(
                            businessName = settings.businessName,
                            storeCode = settings.storeCode,
                            syncStatus = syncStatus,
                            isDarkMode = settings.isDarkMode,
                            onToggleDarkMode = { viewModel.toggleDarkMode(!settings.isDarkMode) },
                            onOpenSettings = { showRateDialog = true },
                            onOpenAi = { showAiDialog = true }
                        )
                    }
                },
                bottomBar = {
                    if (!useNavigationRail && isTopLevelRoute) {
                        FiaoBottomNavBar(
                            currentRoute = currentRoute,
                            onTabSelect = ::navigateToTab,
                            onCenterAddClick = {
                                navController.navigate(AppRoutes.quickRegister(MovementType.CREDIT))
                            }
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = AppRoutes.ROUTE_HOME
                    ) {
                        composable(AppRoutes.ROUTE_HOME) {
                            HomeScreen(
                                settings = settings,
                                metrics = metrics,
                                paymentBreakdown = todayPaymentBreakdown,
                                recentMovements = recentTodayMovements,
                                hasAnyData = customers.isNotEmpty() || products.isNotEmpty() || allMovements.isNotEmpty(),
                                isRefreshingRate = isRefreshingRate,
                                onRefreshBcvClick = { viewModel.refreshBcvRate(isAutomatic = false) },
                                onEditRateClick = { showRateDialog = true },
                                onQuickRegisterClick = {
                                    navController.navigate(AppRoutes.quickRegister(MovementType.CREDIT))
                                },
                                onGoToFiadosClick = { navigateToTab(AppRoutes.ROUTE_FIADOS) },
                                onGoToMovementsClick = { navigateToTab(AppRoutes.ROUTE_MOVEMENTS) },
                                onGoToInventoryClick = { navigateToTab(AppRoutes.ROUTE_INVENTORY) },
                                onViewReceiptClick = ::openReceiptFromMovement,
                                onPhotoClick = { path -> viewingPhotoPath = path },
                                onDeleteMovementClick = { movId -> movementIdToDelete = movId }
                            )
                        }

                        composable(AppRoutes.ROUTE_FIADOS) {
                            CustomersScreen(
                                customers = customers,
                                settings = settings,
                                onCustomerClick = { customerId ->
                                    navController.navigate(AppRoutes.customerDetail(customerId))
                                },
                                onAddCustomerClick = { showCustomerDialog = true }
                            )
                        }

                        composable(AppRoutes.ROUTE_MOVEMENTS) {
                            MovimientosScreen(
                                movements = allMovements,
                                settings = settings,
                                onViewReceiptClick = ::openReceiptFromMovement,
                                onPhotoClick = { path -> viewingPhotoPath = path },
                                onDeleteMovementClick = { movId -> movementIdToDelete = movId }
                            )
                        }

                        composable(AppRoutes.ROUTE_INVENTORY) {
                            ProductsScreen(
                                products = products,
                                settings = settings,
                                onAddProductClick = { showNewProductDialog = true },
                                onEditProductClick = { product -> editingProduct = product },
                                onAdjustStock = { product, delta ->
                                    viewModel.adjustProductStock(product, delta)
                                },
                                onSellProductDirect = { product ->
                                    navController.navigate(
                                        AppRoutes.quickRegister(
                                            type = MovementType.CREDIT,
                                            productId = product.id
                                        )
                                    )
                                }
                            )
                        }

                        composable(
                            route = AppRoutes.ROUTE_CUSTOMER_DETAIL,
                            arguments = listOf(navArgument("customerId") { type = NavType.StringType })
                        ) { entry ->
                            val customerId = entry.arguments?.getString("customerId").orEmpty()
                            val customer by remember(customerId) {
                                viewModel.observeCustomer(customerId)
                            }.collectAsStateWithLifecycle(initialValue = null)
                            val movements by remember(customerId) {
                                viewModel.observeCustomerMovements(customerId)
                            }.collectAsStateWithLifecycle(initialValue = emptyList())

                            CustomerDetailScreen(
                                customer = customer,
                                movements = movements,
                                settings = settings,
                                onBack = { navController.popBackStack() },
                                onFiadoMoreClick = { cid ->
                                    navController.navigate(
                                        AppRoutes.quickRegister(
                                            type = MovementType.CREDIT,
                                            customerId = cid
                                        )
                                    )
                                },
                                onRecordPayment = { cust, amountCents, method, note ->
                                    viewModel.recordPayment(
                                        customer = cust,
                                        amountCents = amountCents,
                                        paymentMethod = method,
                                        note = note
                                    ) { receipt ->
                                        activeReceipt = receipt
                                    }
                                },
                                onUpdateCustomer = { cid, name, phone, notes ->
                                    viewModel.saveCustomer(id = cid, name = name, phone = phone, notes = notes)
                                },
                                onDeleteCustomer = { cid ->
                                    viewModel.deleteCustomer(cid) {
                                        navController.popBackStack()
                                    }
                                },
                                onDeleteMovement = { movId ->
                                    viewModel.deleteMovement(movId)
                                },
                                onOpenReceipt = { receipt -> activeReceipt = receipt },
                                onPhotoClick = { path -> viewingPhotoPath = path }
                            )
                        }

                        composable(
                            route = AppRoutes.ROUTE_QUICK_REGISTER,
                            arguments = listOf(
                                navArgument("type") {
                                    type = NavType.StringType
                                    defaultValue = MovementType.CREDIT
                                },
                                navArgument("customerId") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("productId") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                }
                            )
                        ) { entry ->
                            val type = entry.arguments?.getString("type") ?: MovementType.CREDIT
                            val cidArg = entry.arguments?.getString("customerId").orEmpty()
                            val pidArg = entry.arguments?.getString("productId").orEmpty()

                            QuickRegisterScreen(
                                initialTabType = type,
                                preselectedCustomerId = cidArg.takeIf { it.isNotBlank() },
                                preselectedProductId = pidArg.takeIf { it.isNotBlank() },
                                settings = settings,
                                customers = customers,
                                products = products,
                                onBack = { navController.popBackStack() },
                                onCreateCustomer = { name, phone, notes, onSaved ->
                                    viewModel.saveCustomer(
                                        name = name,
                                        phone = phone,
                                        notes = notes,
                                        onSaved = onSaved
                                    )
                                },
                                onSaveMovement = { cid, movType, totalCents, method, note, photoPath, dueDateMillis, items ->
                                    viewModel.recordQuickMovement(
                                        customerId = cid,
                                        type = movType,
                                        totalCents = totalCents,
                                        paymentMethod = method,
                                        note = note,
                                        photoPath = photoPath,
                                        dueDateMillis = dueDateMillis,
                                        items = items
                                    ) { _, receipt ->
                                        navController.popBackStack()
                                        if (receipt != null) {
                                            activeReceipt = receipt
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRateDialog) {
        RateSettingsDialog(
            currentSettings = settings,
            onDismiss = { showRateDialog = false },
            onSave = { rate, label, businessName ->
                viewModel.updateBusinessSettings(rate, label, businessName)
                showRateDialog = false
            },
            onToggleSalesModule = { enabled ->
                viewModel.toggleSalesModule(enabled)
            },
            onToggleExpensesModule = { enabled ->
                viewModel.toggleExpensesModule(enabled)
            },
            onChangeStoreCodeClick = {
                showRateDialog = false
                onChangeStoreCodeClick()
            },
            onResetDemoClick = {
                viewModel.resetDemoData()
                showRateDialog = false
            },
            onClearAllClick = {
                showRateDialog = false
                showConfirmClearAll = true
            }
        )
    }

    if (showConfirmClearAll) {
        ConfirmDeleteDialog(
            title = "Limpiar todos los datos",
            message = "¿Seguro que deseas borrar todos tus clientes, fiados, movimientos y productos? Esta acción no se puede deshacer.",
            onDismiss = { showConfirmClearAll = false },
            onConfirm = {
                viewModel.clearAllData()
                showConfirmClearAll = false
            }
        )
    }

    if (showAiDialog) {
        FiaoAiDialog(
            messages = aiMessages,
            uiState = aiUiState,
            onSendQuestion = { q -> viewModel.askAi(q) },
            onClearChat = { viewModel.clearAiChat() },
            onDismissError = { viewModel.dismissAiError() },
            onDismiss = { showAiDialog = false }
        )
    }

    if (showCustomerDialog) {
        CustomerFormDialog(
            onDismiss = { showCustomerDialog = false },
            onConfirm = { name, phone, notes ->
                viewModel.saveCustomer(name = name, phone = phone, notes = notes)
                showCustomerDialog = false
            }
        )
    }

    if (showNewProductDialog) {
        ProductFormDialog(
            onDismiss = { showNewProductDialog = false },
            onConfirm = { name, priceCents, costCents, stock, isUnlimited, category, unit ->
                viewModel.saveProduct(
                    name = name,
                    priceCents = priceCents,
                    costCents = costCents,
                    stockQuantity = stock,
                    isUnlimitedStock = isUnlimited,
                    category = category,
                    unit = unit
                )
                showNewProductDialog = false
            }
        )
    }

    editingProduct?.let { prod ->
        ProductFormDialog(
            initialProduct = prod,
            onDismiss = { editingProduct = null },
            onConfirm = { name, priceCents, costCents, stock, isUnlimited, category, unit ->
                viewModel.saveProduct(
                    id = prod.id,
                    name = name,
                    priceCents = priceCents,
                    costCents = costCents,
                    stockQuantity = stock,
                    isUnlimitedStock = isUnlimited,
                    category = category,
                    unit = unit
                )
                editingProduct = null
            },
            onDelete = {
                editingProduct = null
                productToDelete = prod
            }
        )
    }

    productToDelete?.let { prod ->
        ConfirmDeleteDialog(
            title = "Eliminar producto",
            message = "¿Seguro que deseas quitar \"${prod.name}\" de tu inventario?",
            onDismiss = { productToDelete = null },
            onConfirm = {
                viewModel.archiveProduct(prod.id)
                productToDelete = null
            }
        )
    }

    movementIdToDelete?.let { movId ->
        ConfirmDeleteDialog(
            title = "Eliminar movimiento",
            message = "¿Seguro que deseas eliminar este movimiento de tu cuadre?",
            onDismiss = { movementIdToDelete = null },
            onConfirm = {
                viewModel.deleteMovement(movId)
                movementIdToDelete = null
            }
        )
    }

    activeReceipt?.let { receipt ->
        DigitalReceiptDialog(
            receipt = receipt,
            businessSettings = settings,
            onDismiss = { activeReceipt = null }
        )
    }

    viewingPhotoPath?.let { path ->
        PhotoViewerDialog(
            photoPath = path,
            onDismiss = { viewingPhotoPath = null }
        )
    }
}

@Composable
private fun FiaoTopUtilityBar(
    businessName: String,
    storeCode: String,
    syncStatus: CloudSyncStatus,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAi: () -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ledgerColors.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_short_name),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "· $businessName",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Indicador discreto de sincronización en tiempo real / modo offline y Código de Tienda
                val (syncIcon, syncText, syncBg, syncFg) = when (syncStatus) {
                    CloudSyncStatus.SYNCED -> SyncUiStyle(
                        icon = Icons.Filled.CloudDone,
                        label = "Sincronizado en la nube",
                        bg = ledgerColors.settledContainer,
                        fg = ledgerColors.onSettledContainer
                    )
                    CloudSyncStatus.SYNCING -> SyncUiStyle(
                        icon = Icons.Filled.CloudSync,
                        label = "Sincronizando…",
                        bg = ledgerColors.warningContainer,
                        fg = ledgerColors.onWarningContainer
                    )
                    CloudSyncStatus.OFFLINE -> SyncUiStyle(
                        icon = Icons.Filled.CloudOff,
                        label = "Sin conexión - guardando local",
                        bg = ledgerColors.warningContainer,
                        fg = ledgerColors.onWarningContainer
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = syncBg,
                        modifier = Modifier.testTag("cloud_sync_status_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = syncIcon,
                                contentDescription = syncText,
                                tint = syncFg,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = syncText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = syncFg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (storeCode.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.testTag("header_store_code_badge")
                        ) {
                            Text(
                                text = storeCode,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Interruptor visible Modo Claro / Modo Oscuro
            IconButton(
                onClick = onToggleDarkMode,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("theme_toggle_button")
            ) {
                Icon(
                    imageVector = if (isDarkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription = if (isDarkMode) "Cambiar a modo claro" else "Cambiar a modo oscuro",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Configuración (Tasa, Negocio, Código de Tienda)
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Configuración",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Botón flotante discreto en la esquina superior: "Consultar IA"
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, ledgerColors.settledBorder),
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpenAi)
                    .testTag("consult_ai_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "IA",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

private data class SyncUiStyle(
    val icon: ImageVector,
    val label: String,
    val bg: Color,
    val fg: Color
)

@Composable
private fun FiaoBottomNavBar(
    currentRoute: String?,
    onTabSelect: (String) -> Unit,
    onCenterAddClick: () -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, ledgerColors.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            BottomTabItem(
                tab = TopTab.HOME,
                selected = currentRoute == TopTab.HOME.route,
                onClick = { onTabSelect(TopTab.HOME.route) },
                modifier = Modifier.weight(1f)
            )
            BottomTabItem(
                tab = TopTab.FIADOS,
                selected = currentRoute == TopTab.FIADOS.route,
                onClick = { onTabSelect(TopTab.FIADOS.route) },
                modifier = Modifier.weight(1f)
            )

            // Botón flotante central grande "+" en verde esmeralda
            Surface(
                shape = CircleShape,
                color = Color(0xFF10B981),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(54.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCenterAddClick)
                    .testTag("fab_quick_register")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Registrar operación",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            BottomTabItem(
                tab = TopTab.MOVEMENTS,
                selected = currentRoute == TopTab.MOVEMENTS.route,
                onClick = { onTabSelect(TopTab.MOVEMENTS.route) },
                modifier = Modifier.weight(1f)
            )
            BottomTabItem(
                tab = TopTab.INVENTORY,
                selected = currentRoute == TopTab.INVENTORY.route,
                onClick = { onTabSelect(TopTab.INVENTORY.route) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BottomTabItem(
    tab: TopTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag("nav_bar_${tab.route}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
            contentDescription = tab.label,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
