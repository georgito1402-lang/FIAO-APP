package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.example.data.BusinessSettings
import com.example.data.CartItemDraft
import com.example.data.CustomerSummary
import com.example.data.MovementType
import com.example.data.PAYMENT_METHODS
import com.example.data.ProductEntity
import com.example.ui.components.CustomerAvatar
import com.example.ui.components.CustomerFormDialog
import com.example.ui.theme.LocalLedgerColors
import com.example.util.bsInputToUsdCents
import com.example.util.centsToInputString
import com.example.util.daysOffsetMillis
import com.example.util.formatBsFromUsdCents
import com.example.util.formatCents
import com.example.util.formatShortDate
import com.example.util.parseAmountToCents
import java.io.File
import java.io.FileOutputStream

private enum class QuickRegisterTab(val label: String, val type: String) {
    CREDIT("Fiado", MovementType.CREDIT),
    SALE("Venta", MovementType.SALE),
    EXPENSE("Gasto", MovementType.EXPENSE)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickRegisterScreen(
    initialTabType: String,
    preselectedCustomerId: String?,
    preselectedProductId: String?,
    settings: BusinessSettings,
    customers: List<CustomerSummary>,
    products: List<ProductEntity>,
    onBack: () -> Unit,
    onCreateCustomer: (name: String, phone: String, notes: String, onSaved: (String) -> Unit) -> Unit,
    onSaveMovement: (
        customerId: String?,
        type: String,
        totalCents: Long,
        paymentMethod: String,
        note: String,
        photoPath: String?,
        dueDateMillis: Long?,
        items: List<CartItemDraft>
    ) -> Unit
) {
    val context = LocalContext.current
    val ledgerColors = LocalLedgerColors.current

    val availableTabs = remember(settings.enableSalesModule, settings.enableExpensesModule) {
        buildList {
            add(QuickRegisterTab.CREDIT)
            if (settings.enableSalesModule) add(QuickRegisterTab.SALE)
            if (settings.enableExpensesModule) add(QuickRegisterTab.EXPENSE)
        }
    }

    var activeTab by remember(initialTabType, availableTabs) {
        mutableStateOf(
            availableTabs.firstOrNull { it.type == initialTabType } ?: QuickRegisterTab.CREDIT
        )
    }

    var useBsInput by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var selectedCustomerId by remember(preselectedCustomerId) { mutableStateOf(preselectedCustomerId) }
    var customerSearch by remember { mutableStateOf("") }
    var selectedPaymentMethod by remember { mutableStateOf(PAYMENT_METHODS.first()) }
    var selectedDueDateMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var showInventoryPicker by remember(preselectedProductId) {
        mutableStateOf(preselectedProductId != null)
    }
    var productSearch by remember { mutableStateOf("") }
    var showNewCustomerDialog by remember { mutableStateOf(false) }

    var savedPhotoPath by remember { mutableStateOf<String?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    val cartItems = remember(preselectedProductId, products) {
        mutableStateMapOf<String, CartItemDraft>().apply {
            val pre = preselectedProductId?.let { id -> products.firstOrNull { it.id == id } }
            if (pre != null) {
                put(
                    pre.id,
                    CartItemDraft(
                        key = "prod_${pre.id}",
                        productId = pre.id,
                        productName = pre.name,
                        unitPriceCents = pre.priceCents,
                        unit = pre.unit,
                        quantity = 1
                    )
                )
            }
        }
    }

    // Sync initial amount when coming directly from an inventory product
    var amountInitializedFromProduct by remember { mutableStateOf(false) }
    if (!amountInitializedFromProduct && cartItems.isNotEmpty() && amountText.isEmpty()) {
        val sum = cartItems.values.sumOf { it.subtotalCents }
        amountText = centsToInputString(sum)
        amountInitializedFromProduct = true
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            copyUriToSupportFile(context, uri)?.let { path ->
                savedPhotoPath = path
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraFile?.let { file ->
                if (file.exists() && file.length() > 0L) {
                    savedPhotoPath = file.absolutePath
                }
            }
        }
    }

    val resolvedUsdCents = remember(amountText, useBsInput, settings.exchangeRate) {
        if (useBsInput) bsInputToUsdCents(amountText, settings.exchangeRate)
        else parseAmountToCents(amountText)
    }

    val selectedCustomer = remember(customers, selectedCustomerId) {
        customers.firstOrNull { it.id == selectedCustomerId }
    }

    fun adjustProductInCart(product: ProductEntity, delta: Int) {
        val existing = cartItems[product.id]
        val newQty = (existing?.quantity ?: 0) + delta
        if (newQty <= 0) {
            cartItems.remove(product.id)
        } else {
            cartItems[product.id] = CartItemDraft(
                key = "prod_${product.id}",
                productId = product.id,
                productName = product.name,
                unitPriceCents = product.priceCents,
                unit = product.unit,
                quantity = newQty
            )
        }
        val cartSum = cartItems.values.sumOf { it.subtotalCents }
        if (cartSum > 0L) {
            useBsInput = false
            amountText = centsToInputString(cartSum)
        } else if (cartItems.isEmpty()) {
            amountText = ""
        }
    }

    val canSave = (resolvedUsdCents ?: 0L) > 0L &&
        (activeTab != QuickRegisterTab.CREDIT || selectedCustomer != null)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(if (availableTabs.size == 1) "Registrar Fiado" else "Registro rápido")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("quick_register_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, ledgerColors.cardBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = activeTab.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatCents(resolvedUsdCents ?: 0L),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = formatBsFromUsdCents(resolvedUsdCents ?: 0L, settings.exchangeRate),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                val cents = resolvedUsdCents ?: return@Button
                                val itemsList = if (activeTab != QuickRegisterTab.EXPENSE) {
                                    cartItems.values.toList()
                                } else emptyList()
                                val finalNote = note.trim().ifEmpty {
                                    itemsList.joinToString(", ") { "${it.quantity}x ${it.productName}" }
                                }
                                onSaveMovement(
                                    if (activeTab == QuickRegisterTab.CREDIT) selectedCustomer?.id else null,
                                    activeTab.type,
                                    cents,
                                    if (activeTab == QuickRegisterTab.CREDIT) "" else selectedPaymentMethod,
                                    finalNote,
                                    savedPhotoPath,
                                    if (activeTab == QuickRegisterTab.CREDIT) selectedDueDateMillis else null,
                                    itemsList
                                )
                            },
                            enabled = canSave,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .heightIn(min = 52.dp)
                                .testTag("quick_register_save_button")
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Guardar ${activeTab.label}")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Selector superior de pestañas (Fiado primero; Venta y Gasto solo si están activos)
                if (availableTabs.size > 1) {
                    item {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            availableTabs.forEachIndexed { index, tab ->
                                SegmentedButton(
                                    selected = activeTab == tab,
                                    onClick = { activeTab = tab },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = availableTabs.size
                                    ),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("register_tab_${tab.name.lowercase()}")
                                ) {
                                    Text(tab.label, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // 2. Campo grande para el Monto ($ o Bs)
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, ledgerColors.cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Monto",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = !useBsInput,
                                        onClick = {
                                            if (useBsInput) {
                                                val usd = bsInputToUsdCents(amountText, settings.exchangeRate)
                                                useBsInput = false
                                                if (usd != null) amountText = centsToInputString(usd)
                                            }
                                        },
                                        label = { Text("$ USD") },
                                        modifier = Modifier.testTag("currency_usd_chip")
                                    )
                                    FilterChip(
                                        selected = useBsInput,
                                        onClick = {
                                            if (!useBsInput) {
                                                val usd = parseAmountToCents(amountText)
                                                useBsInput = true
                                                if (usd != null && usd > 0L) {
                                                    val bsVal = (usd / 100.0) * settings.exchangeRate
                                                    amountText = String.format(java.util.Locale.US, "%.2f", bsVal)
                                                }
                                            }
                                        },
                                        label = { Text("Bs") },
                                        modifier = Modifier.testTag("currency_bs_chip")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it },
                                placeholder = {
                                    Text(
                                        text = if (useBsInput) "0.00 Bs" else "0.00 $",
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                },
                                prefix = {
                                    Text(
                                        text = if (useBsInput) "Bs " else "$ ",
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                                    focusedContainerColor = MaterialTheme.colorScheme.background
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("quick_amount_input")
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val conversionLabel = when {
                                resolvedUsdCents == null || resolvedUsdCents <= 0L ->
                                    "Tasa actual (${settings.rateLabel}): Bs ${String.format(java.util.Locale.US, "%.2f", settings.exchangeRate)}"
                                useBsInput ->
                                    "Conversión al instante: ${formatCents(resolvedUsdCents)}"
                                else ->
                                    "Conversión al instante: ${formatBsFromUsdCents(resolvedUsdCents, settings.exchangeRate)}"
                            }
                            Text(
                                text = conversionLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 3. Selector de Cliente (si es Fiado) o Método de Pago (si es Venta o Gasto)
                if (activeTab == QuickRegisterTab.CREDIT) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, ledgerColors.cardBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Cliente que fía *",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(
                                        onClick = { showNewCustomerDialog = true },
                                        modifier = Modifier.testTag("quick_add_customer_button")
                                    ) {
                                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Nuevo cliente")
                                    }
                                }

                                if (customers.isEmpty()) {
                                    Text(
                                        text = "Agrega un cliente primero con el botón 'Nuevo cliente'.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    OutlinedTextField(
                                        value = customerSearch,
                                        onValueChange = { customerSearch = it },
                                        placeholder = { Text("Buscar cliente…") },
                                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val matching = customers.filter {
                                        customerSearch.isBlank() || it.name.contains(customerSearch.trim(), ignoreCase = true)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        matching.forEach { cust ->
                                            val isSelected = cust.id == selectedCustomerId
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { selectedCustomerId = cust.id },
                                                leadingIcon = {
                                                    CustomerAvatar(
                                                        name = cust.name,
                                                        semaphore = cust.semaphore,
                                                        size = 22.dp
                                                    )
                                                },
                                                label = { Text(cust.name) },
                                                modifier = Modifier.testTag("pick_customer_chip_${cust.id}")
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Fecha límite de pago (opcional)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedDueDateMillis == null,
                                        onClick = { selectedDueDateMillis = null },
                                        label = { Text("Sin límite") },
                                        modifier = Modifier.testTag("due_none_chip")
                                    )
                                    listOf(7 to "7 días", 15 to "15 días", 30 to "30 días").forEach { (days, label) ->
                                        FilterChip(
                                            selected = false,
                                            onClick = { selectedDueDateMillis = daysOffsetMillis(days) },
                                            label = { Text(label) },
                                            modifier = Modifier.testTag("due_${days}d_chip")
                                        )
                                    }
                                    FilterChip(
                                        selected = selectedDueDateMillis != null,
                                        onClick = { showDatePicker = true },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.CalendarMonth,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                selectedDueDateMillis?.let { formatShortDate(it) } ?: "Elegir fecha"
                                            )
                                        },
                                        modifier = Modifier.testTag("due_custom_chip")
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, ledgerColors.cardBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Método de pago",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PAYMENT_METHODS.forEach { method ->
                                        FilterChip(
                                            selected = selectedPaymentMethod == method,
                                            onClick = { selectedPaymentMethod = method },
                                            label = { Text(method) },
                                            modifier = Modifier.testTag("payment_method_$method")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Optional Inventory Product Picker for Venta / Fiado
                if (activeTab != QuickRegisterTab.EXPENSE && products.isNotEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, ledgerColors.cardBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showInventoryPicker = !showInventoryPicker },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Inventory2,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = if (cartItems.isEmpty()) "Elegir productos del inventario (opc.)"
                                            else "Productos elegidos (${cartItems.values.sumOf { it.quantity }})",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                    Text(
                                        text = if (showInventoryPicker) "Ocultar" else "Abrir",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (showInventoryPicker) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = productSearch,
                                        onValueChange = { productSearch = it },
                                        placeholder = { Text("Buscar producto…") },
                                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val matchingProds = products.filter {
                                        productSearch.isBlank() || it.name.contains(productSearch.trim(), ignoreCase = true)
                                    }.take(12)

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        matchingProds.forEach { prod ->
                                            val qty = cartItems[prod.id]?.quantity ?: 0
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { adjustProductInCart(prod, +1) }
                                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = prod.name,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    val stockInfo = if (prod.isUnlimitedStock) {
                                                        "Siempre disponible (∞)"
                                                    } else {
                                                        "${prod.stockQuantity} disp."
                                                    }
                                                    Text(
                                                        text = "${formatCents(prod.priceCents)} · $stockInfo",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (qty > 0) {
                                                    IconButton(onClick = { adjustProductInCart(prod, -1) }) {
                                                        Icon(Icons.Filled.Remove, contentDescription = "Quitar")
                                                    }
                                                    Text(
                                                        text = qty.toString(),
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                                    )
                                                }
                                                IconButton(onClick = { adjustProductInCart(prod, +1) }) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Add,
                                                        contentDescription = "Agregar",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Nota / Concepto + Foto de soporte (capture de transferencia)
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, ledgerColors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                label = { Text("Nota o concepto (opcional)") },
                                placeholder = { Text("Ej. 2 harinas, 1 queso") },
                                maxLines = 2,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("quick_note_input")
                            )

                            Text(
                                text = "Foto de soporte (capture de transferencia)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (savedPhotoPath != null) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Image(
                                        painter = rememberAsyncImagePainter(model = Uri.fromFile(File(savedPhotoPath!!))),
                                        contentDescription = "Soporte adjunto",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp)
                                            .clip(MaterialTheme.shapes.medium),
                                        contentScale = ContentScale.Crop
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                    ) {
                                        IconButton(onClick = { savedPhotoPath = null }) {
                                            Icon(Icons.Filled.Close, contentDescription = "Quitar foto")
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val file = createCameraTempFile(context)
                                            pendingCameraFile = file
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            cameraLauncher.launch(uri)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("take_support_photo_button"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Tomar foto")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("pick_support_photo_button"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Capture")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewCustomerDialog) {
        CustomerFormDialog(
            onDismiss = { showNewCustomerDialog = false },
            onConfirm = { name, phone, notes ->
                onCreateCustomer(name, phone, notes) { newId ->
                    selectedCustomerId = newId
                }
                showNewCustomerDialog = false
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDueDateMillis ?: daysOffsetMillis(7)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            selectedDueDateMillis = it
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun createCameraTempFile(context: Context): File {
    val dir = File(context.filesDir, "supports").apply { mkdirs() }
    return File(dir, "support_${System.currentTimeMillis()}.jpg")
}

private fun copyUriToSupportFile(context: Context, uri: Uri): String? {
    return try {
        val dir = File(context.filesDir, "supports").apply { mkdirs() }
        val dest = File(dir, "support_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
        dest.absolutePath
    } catch (_: Exception) {
        null
    }
}
