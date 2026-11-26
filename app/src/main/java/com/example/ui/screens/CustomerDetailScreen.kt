package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.BusinessSettings
import com.example.data.CustomerSummary
import com.example.data.DebtSemaphore
import com.example.data.MovementType
import com.example.data.MovementWithItems
import com.example.data.ReceiptPreviewData
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.CustomerAvatar
import com.example.ui.components.CustomerFormDialog
import com.example.ui.components.MovementCard
import com.example.ui.components.RegisterPaymentDialog
import com.example.ui.components.SemaphoreStatusPill
import com.example.ui.theme.LocalLedgerColors
import com.example.util.formatBsFromUsdCents
import com.example.util.formatCents
import com.example.util.formatShortDate

private enum class MovementFilter(val label: String) {
    ALL("Todos"),
    CREDITS("Fiados"),
    PAYMENTS("Abonos")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customer: CustomerSummary?,
    movements: List<MovementWithItems>,
    settings: BusinessSettings,
    onBack: () -> Unit,
    onFiadoMoreClick: (String) -> Unit,
    onRecordPayment: (customer: CustomerSummary, amountCents: Long, paymentMethod: String, note: String) -> Unit,
    onUpdateCustomer: (String, String, String, String) -> Unit,
    onDeleteCustomer: (String) -> Unit,
    onDeleteMovement: (String) -> Unit,
    onOpenReceipt: (ReceiptPreviewData) -> Unit,
    onPhotoClick: (String) -> Unit
) {
    val context = LocalContext.current
    val ledgerColors = LocalLedgerColors.current

    var selectedFilter by remember { mutableStateOf(MovementFilter.ALL) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteCustomerConfirm by remember { mutableStateOf(false) }
    var movementToDelete by remember { mutableStateOf<MovementWithItems?>(null) }

    val filteredMovements = remember(movements, selectedFilter) {
        when (selectedFilter) {
            MovementFilter.ALL -> movements
            MovementFilter.CREDITS -> movements.filter { it.movement.type == MovementType.CREDIT }
            MovementFilter.PAYMENTS -> movements.filter { it.movement.type == MovementType.PAYMENT }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = customer?.name ?: "Ficha de Cliente",
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    if (customer != null) {
                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.testTag("detail_edit_customer_button")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar cliente")
                        }
                        IconButton(
                            onClick = { showDeleteCustomerConfirm = true },
                            modifier = Modifier.testTag("detail_delete_customer_button")
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Eliminar cliente")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (customer == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Cargando ficha…", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

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
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, ledgerColors.cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CustomerAvatar(
                                    name = customer.name,
                                    semaphore = customer.semaphore,
                                    size = 54.dp
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = customer.name,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        text = customer.phone.ifBlank { "Sin número de contacto" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (customer.notes.isNotBlank()) {
                                        Text(
                                            text = customer.notes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                SemaphoreStatusPill(customer = customer)
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            val (balBg, balFg) = when (customer.semaphore) {
                                DebtSemaphore.GREEN -> ledgerColors.settledContainer to ledgerColors.onSettledContainer
                                DebtSemaphore.YELLOW -> ledgerColors.warningContainer to ledgerColors.onWarningContainer
                                DebtSemaphore.RED -> ledgerColors.debtContainer to ledgerColors.onDebtContainer
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = balBg
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = when {
                                                customer.balanceCents > 0L -> "Debe actualmente"
                                                customer.balanceCents < 0L -> "Saldo a favor"
                                                else -> "Cuenta al día"
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            color = balFg
                                        )
                                        Text(
                                            text = "Fiado: ${formatCents(customer.totalCreditCents)} · Abonos: ${formatCents(customer.totalPaymentCents)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = balFg.copy(alpha = 0.8f)
                                        )
                                        customer.nearestDueDateMillis?.let { due ->
                                            Text(
                                                text = "Fecha límite: ${formatShortDate(due)}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = balFg
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatCents(
                                                if (customer.balanceCents < 0L) -customer.balanceCents else customer.balanceCents
                                            ),
                                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                            color = balFg
                                        )
                                        if (customer.balanceCents > 0L) {
                                            Text(
                                                text = formatBsFromUsdCents(customer.balanceCents, settings.exchangeRate),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = balFg.copy(alpha = 0.9f)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Dos botones grandes: "Fiar más" y "Registrar Abono"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { onFiadoMoreClick(customer.id) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp)
                                        .testTag("detail_fiar_mas_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Filled.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Fiar más")
                                }

                                Button(
                                    onClick = { showPaymentDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp)
                                        .testTag("detail_register_payment_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Registrar Abono")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:${customer.phone}")
                                        }
                                        context.startActivity(intent)
                                    },
                                    enabled = customer.phone.isNotBlank(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("detail_call_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Llamar")
                                }

                                OutlinedButton(
                                    onClick = {
                                        val lastMov = movements.firstOrNull()
                                        val summaryText = lastMov?.items?.takeIf { it.isNotEmpty() }
                                            ?.joinToString(", ") { "${it.quantity}x ${it.productName}" }
                                            ?: lastMov?.movement?.note
                                            ?: "Estado de cuenta"
                                        onOpenReceipt(
                                            ReceiptPreviewData(
                                                movementId = lastMov?.movement?.id ?: "",
                                                customerName = customer.name,
                                                customerPhone = customer.phone,
                                                movementType = lastMov?.movement?.type ?: MovementType.CREDIT,
                                                amountCents = lastMov?.movement?.totalCents ?: customer.balanceCents,
                                                remainingBalanceCents = customer.balanceCents,
                                                paymentMethod = lastMov?.movement?.paymentMethod ?: "",
                                                note = lastMov?.movement?.note ?: "",
                                                itemsSummary = summaryText,
                                                createdAt = lastMov?.movement?.createdAt ?: System.currentTimeMillis(),
                                                dueDateMillis = lastMov?.movement?.dueDateMillis ?: customer.nearestDueDateMillis
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("detail_receipt_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Comprobante")
                                }
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Historial de fiados y abonos (${movements.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MovementFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = selectedFilter == filter,
                                    onClick = { selectedFilter = filter },
                                    label = { Text(filter.label) },
                                    modifier = Modifier.testTag("detail_filter_${filter.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                if (filteredMovements.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, ledgerColors.cardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ReceiptLong,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Sin movimientos registrados",
                                    style = MaterialTheme.typography.titleSmall,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Usa 'Fiar más' o 'Registrar Abono' para anotar el primer movimiento.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(filteredMovements, key = { it.movement.id }) { movementWithItems ->
                        MovementCard(
                            movementWithItems = movementWithItems,
                            exchangeRate = settings.exchangeRate,
                            onDeleteClick = { movementToDelete = movementWithItems },
                            onViewReceiptClick = {
                                val mov = movementWithItems.movement
                                val itemsSummary = movementWithItems.items
                                    .takeIf { it.isNotEmpty() }
                                    ?.joinToString(", ") { "${it.quantity}x ${it.productName}" }
                                    ?: mov.note
                                onOpenReceipt(
                                    ReceiptPreviewData(
                                        movementId = mov.id,
                                        customerName = customer.name,
                                        customerPhone = customer.phone,
                                        movementType = mov.type,
                                        amountCents = mov.totalCents,
                                        remainingBalanceCents = customer.balanceCents,
                                        paymentMethod = mov.paymentMethod,
                                        note = mov.note,
                                        itemsSummary = itemsSummary,
                                        createdAt = mov.createdAt,
                                        dueDateMillis = mov.dueDateMillis
                                    )
                                )
                            },
                            onPhotoClick = onPhotoClick
                        )
                    }
                }
            }
        }
    }

    if (showPaymentDialog && customer != null) {
        RegisterPaymentDialog(
            customerName = customer.name,
            currentBalanceCents = customer.balanceCents,
            exchangeRate = settings.exchangeRate,
            onDismiss = { showPaymentDialog = false },
            onConfirm = { amountCents, method, note ->
                onRecordPayment(customer, amountCents, method, note)
                showPaymentDialog = false
            }
        )
    }

    if (showEditDialog && customer != null) {
        CustomerFormDialog(
            initialCustomer = customer,
            onDismiss = { showEditDialog = false },
            onConfirm = { name, phone, notes ->
                onUpdateCustomer(customer.id, name, phone, notes)
                showEditDialog = false
            }
        )
    }

    if (showDeleteCustomerConfirm && customer != null) {
        ConfirmDeleteDialog(
            title = "Eliminar cliente",
            message = "¿Seguro que deseas eliminar a ${customer.name} y todo su historial? Esta acción no se puede deshacer.",
            onDismiss = { showDeleteCustomerConfirm = false },
            onConfirm = {
                showDeleteCustomerConfirm = false
                onDeleteCustomer(customer.id)
            }
        )
    }

    movementToDelete?.let { mov ->
        ConfirmDeleteDialog(
            title = "Eliminar movimiento",
            message = "¿Seguro que deseas eliminar este movimiento de ${formatCents(mov.movement.totalCents)}?",
            onDismiss = { movementToDelete = null },
            onConfirm = {
                val id = mov.movement.id
                movementToDelete = null
                onDeleteMovement(id)
            }
        )
    }
}
