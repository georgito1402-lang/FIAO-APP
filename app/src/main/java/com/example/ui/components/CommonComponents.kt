package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.data.BusinessSettings
import com.example.data.CustomerSummary
import com.example.data.DEFAULT_BUSINESS_NAME
import com.example.data.DebtSemaphore
import com.example.data.MovementType
import com.example.data.MovementWithItems
import com.example.data.PAYMENT_METHODS
import com.example.data.PRODUCT_CATEGORIES
import com.example.data.ProductEntity
import com.example.ui.theme.LocalLedgerColors
import com.example.util.bsInputToUsdCents
import com.example.util.centsToInputString
import com.example.util.customerInitials
import com.example.util.formatBsFromUsdCents
import com.example.util.formatCents
import com.example.util.formatRelativeDateTime
import com.example.util.formatShortDate
import com.example.util.parseAmountToCents
import java.io.File
import java.util.Locale

@Composable
fun CustomerAvatar(
    name: String,
    semaphore: DebtSemaphore,
    size: Dp = 46.dp,
    modifier: Modifier = Modifier
) {
    val ledgerColors = LocalLedgerColors.current
    val (bgColor, textColor) = when (semaphore) {
        DebtSemaphore.GREEN -> ledgerColors.settledContainer to ledgerColors.onSettledContainer
        DebtSemaphore.YELLOW -> ledgerColors.warningContainer to ledgerColors.onWarningContainer
        DebtSemaphore.RED -> ledgerColors.debtContainer to ledgerColors.onDebtContainer
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = customerInitials(name),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}

@Composable
fun SemaphoreStatusPill(
    customer: CustomerSummary,
    modifier: Modifier = Modifier
) {
    val ledgerColors = LocalLedgerColors.current
    val (bg, fg, dotColor, label) = when (customer.semaphore) {
        DebtSemaphore.GREEN -> Quad(
            ledgerColors.settledContainer,
            ledgerColors.onSettledContainer,
            ledgerColors.settled,
            if (customer.balanceCents < 0L) "A favor ${formatCents(-customer.balanceCents)}" else "Al día"
        )
        DebtSemaphore.YELLOW -> Quad(
            ledgerColors.warningContainer,
            ledgerColors.onWarningContainer,
            ledgerColors.warning,
            if (customer.debtAgeDays == 0) "Hoy" else "${customer.debtAgeDays}d"
        )
        DebtSemaphore.RED -> Quad(
            ledgerColors.debtContainer,
            ledgerColors.onDebtContainer,
            ledgerColors.debt,
            "${customer.debtAgeDays}d (+15d)"
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = fg
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun MovementCard(
    movementWithItems: MovementWithItems,
    exchangeRate: Double,
    customerName: String? = null,
    onDeleteClick: (() -> Unit)? = null,
    onViewReceiptClick: (() -> Unit)? = null,
    onPhotoClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ledgerColors = LocalLedgerColors.current
    val movement = movementWithItems.movement

    val (icon, title, badgeBg, badgeFg, amountColor, prefix) = when (movement.type) {
        MovementType.SALE -> Sextuple(
            Icons.Filled.PointOfSale,
            "Venta",
            ledgerColors.settledContainer,
            ledgerColors.onSettledContainer,
            ledgerColors.settled,
            "+"
        )
        MovementType.CREDIT -> Sextuple(
            Icons.Filled.ShoppingBag,
            "Fiado",
            ledgerColors.warningContainer,
            ledgerColors.onWarningContainer,
            ledgerColors.debt,
            ""
        )
        MovementType.PAYMENT -> Sextuple(
            Icons.Filled.Payments,
            "Abono",
            ledgerColors.settledContainer,
            ledgerColors.onSettledContainer,
            ledgerColors.settled,
            "+"
        )
        else -> Sextuple(
            Icons.Filled.TrendingDown,
            "Gasto",
            ledgerColors.debtContainer,
            ledgerColors.onDebtContainer,
            ledgerColors.debt,
            "-"
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ledgerColors.cardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = badgeFg,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (customerName != null) "$title · $customerName" else title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = formatRelativeDateTime(movement.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (movement.paymentMethod.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = movement.paymentMethod,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (movement.type == MovementType.CREDIT && movement.dueDateMillis != null) {
                            val isOverdue = movement.dueDateMillis < System.currentTimeMillis()
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isOverdue) ledgerColors.debtContainer else ledgerColors.warningContainer
                            ) {
                                Text(
                                    text = "Vence ${formatShortDate(movement.dueDateMillis)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOverdue) ledgerColors.onDebtContainer else ledgerColors.onWarningContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$prefix${formatCents(movement.totalCents)}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = amountColor
                    )
                    Text(
                        text = formatBsFromUsdCents(movement.totalCents, exchangeRate),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onDeleteClick != null) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.testTag("delete_movement_button_${movement.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Eliminar movimiento",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (movementWithItems.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    movementWithItems.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${item.quantity}x ${item.productName}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            Text(
                                text = formatCents(item.subtotalCents),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (movement.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = movement.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            val hasPhoto = !movement.photoPath.isNullOrBlank()
            val showReceiptBtn = onViewReceiptClick != null &&
                (movement.type == MovementType.CREDIT || movement.type == MovementType.PAYMENT)

            if (hasPhoto || showReceiptBtn) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasPhoto && onPhotoClick != null) {
                        OutlinedButton(
                            onClick = { onPhotoClick(movement.photoPath!!) },
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("view_photo_button_${movement.id}"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ver soporte")
                        }
                    }
                    if (showReceiptBtn) {
                        OutlinedButton(
                            onClick = onViewReceiptClick!!,
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("view_receipt_button_${movement.id}"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Comprobante")
                        }
                    }
                }
            }
        }
    }
}

private data class Sextuple<A, B, C, D, E, F>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F
)

@Composable
fun RateSettingsDialog(
    currentSettings: BusinessSettings,
    onDismiss: () -> Unit,
    onSave: (rate: Double, label: String, businessName: String) -> Unit,
    onToggleSalesModule: (Boolean) -> Unit = {},
    onToggleExpensesModule: (Boolean) -> Unit = {},
    onChangeStoreCodeClick: () -> Unit = {},
    onResetDemoClick: () -> Unit = {},
    onClearAllClick: () -> Unit = {}
) {
    val presets = listOf("Tasa BCV", "Paralelo", "Personalizada")
    var selectedLabel by remember { mutableStateOf(currentSettings.rateLabel) }
    var rateText by remember {
        mutableStateOf(String.format(Locale.US, "%.2f", currentSettings.exchangeRate))
    }
    var businessName by remember { mutableStateOf(currentSettings.businessName) }
    var rateError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Configuración y Tasa del Día", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Referencia de tasa",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = selectedLabel == preset,
                            onClick = { selectedLabel = preset },
                            label = { Text(preset) },
                            modifier = Modifier.testTag("rate_preset_$preset")
                        )
                    }
                }

                OutlinedTextField(
                    value = rateText,
                    onValueChange = {
                        rateText = it
                        rateError = null
                    },
                    label = { Text("Tasa (Bs por cada $1) *") },
                    leadingIcon = { Icon(Icons.Filled.CurrencyExchange, contentDescription = null) },
                    isError = rateError != null,
                    supportingText = rateError?.let { err -> { Text(err) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exchange_rate_input")
                )

                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text("Nombre de tu negocio") },
                    leadingIcon = { Icon(Icons.Filled.Storefront, contentDescription = null) },
                    placeholder = { Text("Ej. $DEFAULT_BUSINESS_NAME") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("business_name_input")
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                Text(
                    text = "Módulos activos",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Fiado y gestión de clientes",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Módulo principal obligatorio (siempre activo)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = LocalLedgerColors.current.settledContainer
                            ) {
                                Text(
                                    text = "Fijo",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = LocalLedgerColors.current.onSettledContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Habilitar registro de Ventas",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Muestra la pestaña Venta y Ventas de Hoy",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = currentSettings.enableSalesModule,
                                onCheckedChange = onToggleSalesModule,
                                modifier = Modifier.testTag("switch_enable_sales")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Habilitar registro de Gastos",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Muestra la pestaña Gasto y Gastos de Hoy",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = currentSettings.enableExpensesModule,
                                onCheckedChange = onToggleExpensesModule,
                                modifier = Modifier.testTag("switch_enable_expenses")
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                Text(
                    text = "Código de Tienda compartido",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = LocalLedgerColors.current.settledContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_store_code_card")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                            tint = LocalLedgerColors.current.settled,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentSettings.storeCode.ifBlank { "Sin código" },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = LocalLedgerColors.current.onSettledContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("settings_store_code_value")
                            )
                            Text(
                                text = "Usa este mismo código en otros celulares para compartir el negocio en tiempo real.",
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalLedgerColors.current.onSettledContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onChangeStoreCodeClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("change_store_code_button")
                ) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cambiar Código de Tienda")
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                Text(
                    text = "Gestión de datos en la nube",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = onResetDemoClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("settings_reset_demo_button")
                ) {
                    Text("Reiniciar datos de demostración")
                }

                OutlinedButton(
                    onClick = onClearAllClick,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("settings_clear_all_button")
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Limpiar todos los datos")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = rateText.trim().replace(",", ".").toDoubleOrNull()
                    if (parsed == null || parsed <= 0.0) {
                        rateError = "Ingresa una tasa válida mayor a 0"
                    } else {
                        onSave(parsed, selectedLabel, businessName.trim().ifEmpty { DEFAULT_BUSINESS_NAME })
                    }
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("save_rate_button")
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun CustomerFormDialog(
    initialCustomer: CustomerSummary? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, notes: String) -> Unit
) {
    var name by remember { mutableStateOf(initialCustomer?.name ?: "") }
    var phone by remember { mutableStateOf(initialCustomer?.phone ?: "") }
    var notes by remember { mutableStateOf(initialCustomer?.notes ?: "") }
    var nameError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialCustomer == null) "Agregar cliente" else "Editar cliente",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text("Nombre completo *") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    isError = nameError != null,
                    supportingText = nameError?.let { err -> { Text(err) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_name_input")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Teléfono / WhatsApp") },
                    leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                    placeholder = { Text("Ej. 0414 123 4567") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_phone_input")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Referencia o nota (opcional)") },
                    placeholder = { Text("Ej. Casa azul esquina") },
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_notes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        nameError = "Escribe el nombre del cliente"
                    } else {
                        onConfirm(trimmed, phone.trim(), notes.trim())
                    }
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("save_customer_button")
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("cancel_customer_button")
            ) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun ProductFormDialog(
    initialProduct: ProductEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        priceCents: Long,
        costCents: Long,
        stockQuantity: Int,
        isUnlimitedStock: Boolean,
        category: String,
        unit: String
    ) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val units = listOf("u", "kg", "lt", "paq", "caja")
    val categories = PRODUCT_CATEGORIES

    var name by remember { mutableStateOf(initialProduct?.name ?: "") }
    var priceText by remember {
        mutableStateOf(initialProduct?.let { centsToInputString(it.priceCents) } ?: "")
    }
    var costText by remember {
        mutableStateOf(
            initialProduct?.let {
                if (it.costCents > 0L) centsToInputString(it.costCents) else ""
            } ?: ""
        )
    }
    var isUnlimitedStock by remember {
        mutableStateOf(initialProduct?.isUnlimitedStock ?: false)
    }
    var stockText by remember {
        mutableStateOf(initialProduct?.stockQuantity?.toString() ?: "10")
    }
    var selectedUnit by remember { mutableStateOf(initialProduct?.unit ?: "u") }
    var selectedCategory by remember {
        mutableStateOf(
            initialProduct?.category?.takeIf { it in categories } ?: categories.first()
        )
    }
    var nameError by remember { mutableStateOf<String?>(null) }
    var priceError by remember { mutableStateOf<String?>(null) }

    val previewSaleCents = parseAmountToCents(priceText) ?: 0L
    val previewCostCents = parseAmountToCents(costText) ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialProduct == null) "Nuevo producto" else "Editar producto",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text("Nombre del producto *") },
                    leadingIcon = { Icon(Icons.Filled.Inventory2, contentDescription = null) },
                    placeholder = { Text("Ej. Empanada o Refresco 2L") },
                    isError = nameError != null,
                    supportingText = nameError?.let { err -> { Text(err) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_name_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = {
                            priceText = it
                            priceError = null
                        },
                        label = { Text("Precio venta ($) *") },
                        placeholder = { Text("0.00") },
                        isError = priceError != null,
                        supportingText = priceError?.let { err -> { Text(err) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_price_input")
                    )

                    OutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it },
                        label = { Text("Costo ($ opc.)") },
                        placeholder = { Text("0.00") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_cost_input")
                    )
                }

                if (previewSaleCents > 0L && previewCostCents > 0L) {
                    val profit = previewSaleCents - previewCostCents
                    val pct = ((profit.toDouble() / previewSaleCents.toDouble()) * 100.0).toInt()
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (profit >= 0) LocalLedgerColors.current.settledContainer
                        else LocalLedgerColors.current.debtContainer
                    ) {
                        Text(
                            text = "Ganancia estimada: ${formatCents(profit)} ($pct%)",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (profit >= 0) LocalLedgerColors.current.onSettledContainer
                            else LocalLedgerColors.current.onDebtContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                // Toggle: Cantidad indefinida (siempre disponible)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isUnlimitedStock) LocalLedgerColors.current.settledContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Cantidad indefinida (siempre disponible)",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isUnlimitedStock) LocalLedgerColors.current.onSettledContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Para comidas preparadas al momento",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUnlimitedStock) LocalLedgerColors.current.onSettledContainer.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isUnlimitedStock,
                            onCheckedChange = { isUnlimitedStock = it },
                            modifier = Modifier.testTag("product_unlimited_stock_switch")
                        )
                    }
                }

                if (!isUnlimitedStock) {
                    OutlinedTextField(
                        value = stockText,
                        onValueChange = { if (it.all { ch -> ch.isDigit() }) stockText = it },
                        label = { Text("Cantidad disponible") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("product_stock_input")
                    )
                }

                Text(
                    text = "Unidad",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    units.forEach { unit ->
                        FilterChip(
                            selected = selectedUnit == unit,
                            onClick = { selectedUnit = unit },
                            label = { Text(unit) },
                            modifier = Modifier.testTag("unit_chip_$unit")
                        )
                    }
                }

                Text(
                    text = "Categoría",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) },
                            modifier = Modifier.testTag("category_chip_$cat")
                        )
                    }
                }

                if (initialProduct != null && onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("delete_product_button")
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Eliminar producto")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    val parsedPrice = parseAmountToCents(priceText)
                    val parsedCost = parseAmountToCents(costText) ?: 0L
                    val parsedStock = if (isUnlimitedStock) 0 else (stockText.toIntOrNull() ?: 0)
                    if (trimmedName.isEmpty()) {
                        nameError = "Escribe el nombre del producto"
                    }
                    if (parsedPrice == null || parsedPrice <= 0L) {
                        priceError = "Ingresa un precio válido mayor a 0"
                    }
                    if (trimmedName.isNotEmpty() && parsedPrice != null && parsedPrice > 0L) {
                        onConfirm(
                            trimmedName,
                            parsedPrice,
                            parsedCost,
                            parsedStock,
                            isUnlimitedStock,
                            selectedCategory,
                            selectedUnit
                        )
                    }
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("save_product_button")
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("cancel_product_button")
            ) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun RegisterPaymentDialog(
    customerName: String,
    currentBalanceCents: Long,
    exchangeRate: Double,
    onDismiss: () -> Unit,
    onConfirm: (amountCents: Long, paymentMethod: String, note: String) -> Unit
) {
    var useBsInput by remember { mutableStateOf(false) }
    var amountText by remember {
        mutableStateOf(if (currentBalanceCents > 0L) centsToInputString(currentBalanceCents) else "")
    }
    var selectedMethod by remember { mutableStateOf(PAYMENT_METHODS.first()) }
    var note by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf<String?>(null) }

    val resolvedUsdCents = remember(amountText, useBsInput, exchangeRate) {
        if (useBsInput) bsInputToUsdCents(amountText, exchangeRate)
        else parseAmountToCents(amountText)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Registrar Abono", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = customerName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentBalanceCents > 0L) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = LocalLedgerColors.current.debtContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Deuda actual",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalLedgerColors.current.onDebtContainer
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatCents(currentBalanceCents),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = LocalLedgerColors.current.onDebtContainer
                                )
                                Text(
                                    text = formatBsFromUsdCents(currentBalanceCents, exchangeRate),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LocalLedgerColors.current.onDebtContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    useBsInput = false
                                    amountText = centsToInputString(currentBalanceCents)
                                    amountError = null
                                }
                                .testTag("pay_full_chip")
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Todo (${formatCents(currentBalanceCents)})",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (currentBalanceCents >= 200L) {
                            val halfCents = currentBalanceCents / 2L
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        useBsInput = false
                                        amountText = centsToInputString(halfCents)
                                        amountError = null
                                    }
                                    .testTag("pay_half_chip")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "Mitad (${formatCents(halfCents)})",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !useBsInput,
                        onClick = { useBsInput = false },
                        label = { Text("En $ (USD)") }
                    )
                    FilterChip(
                        selected = useBsInput,
                        onClick = { useBsInput = true },
                        label = { Text("En Bs") }
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = null
                    },
                    label = { Text(if (useBsInput) "Monto en Bolívares *" else "Monto en Dólares ($) *") },
                    leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) },
                    placeholder = { Text("0.00") },
                    isError = amountError != null,
                    supportingText = {
                        when {
                            amountError != null -> Text(amountError!!)
                            resolvedUsdCents != null && resolvedUsdCents > 0L -> {
                                val convertedText = if (useBsInput) {
                                    "Equivale a ${formatCents(resolvedUsdCents)}"
                                } else {
                                    "Equivale a ${formatBsFromUsdCents(resolvedUsdCents, exchangeRate)}"
                                }
                                Text(convertedText)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_amount_input")
                )

                Text(
                    text = "Método de pago",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PAYMENT_METHODS.forEach { method ->
                        FilterChip(
                            selected = selectedMethod == method,
                            onClick = { selectedMethod = method },
                            label = { Text(method) }
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Nota o referencia (opcional)") },
                    placeholder = { Text("Ej. Ref. 8942") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cents = resolvedUsdCents
                    if (cents == null || cents <= 0L) {
                        amountError = "Ingresa un monto válido mayor a 0"
                    } else {
                        onConfirm(cents, selectedMethod, note.trim())
                    }
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_payment_button")
            ) {
                Text("Registrar abono")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("cancel_payment_button")
            ) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun PhotoViewerDialog(
    photoPath: String,
    onDismiss: () -> Unit
) {
    val file = remember(photoPath) { File(photoPath) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Foto de soporte", style = MaterialTheme.typography.titleLarge) },
        text = {
            Image(
                painter = rememberAsyncImagePainter(model = Uri.fromFile(file)),
                contentDescription = "Foto de soporte de pago",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 420.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Fit
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_delete_button")
            ) {
                Text("Eliminar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("cancel_delete_button")
            ) {
                Text("Cancelar")
            }
        }
    )
}
