package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BusinessSettings
import com.example.data.PaymentMethodBreakdownItem
import com.example.data.RecentMovementDisplay
import com.example.ui.DashboardMetrics
import com.example.ui.components.MovementCard
import com.example.ui.theme.LocalLedgerColors
import com.example.util.formatBsFromUsdCents
import com.example.util.formatCents
import com.example.util.formatRate
import com.example.util.formatRelativeDateTime

@Composable
fun HomeScreen(
    settings: BusinessSettings,
    metrics: DashboardMetrics,
    paymentBreakdown: List<PaymentMethodBreakdownItem>,
    recentMovements: List<RecentMovementDisplay>,
    hasAnyData: Boolean,
    isRefreshingRate: Boolean,
    onRefreshBcvClick: () -> Unit,
    onEditRateClick: () -> Unit,
    onQuickRegisterClick: () -> Unit,
    onGoToFiadosClick: () -> Unit,
    onGoToMovementsClick: () -> Unit,
    onGoToInventoryClick: () -> Unit,
    onViewReceiptClick: (RecentMovementDisplay) -> Unit,
    onPhotoClick: (String) -> Unit,
    onDeleteMovementClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ledgerColors = LocalLedgerColors.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Cuadre de Caja",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Resumen financiero de hoy en ${settings.businessName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Banner superior con la Tasa del Día editable + botón Actualizar BCV
            item {
                ExchangeRateBanner(
                    settings = settings,
                    isRefreshingRate = isRefreshingRate,
                    onRefreshBcvClick = onRefreshBcvClick,
                    onEditClick = onEditRateClick
                )
            }

            // Tarjeta principal destacada: Por Cobrar (En la calle), más Ventas/Gastos de Hoy si están habilitados
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroReceivableCard(
                        totalReceivableCents = metrics.totalReceivableCents,
                        debtorsCount = metrics.debtorsCount,
                        exchangeRate = settings.exchangeRate,
                        onClick = onGoToFiadosClick
                    )

                    if (settings.enableSalesModule || settings.enableExpensesModule) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (settings.enableSalesModule) {
                                DailyMetricCard(
                                    title = "Ventas de Hoy",
                                    subtitle = "Entró a caja",
                                    amountCents = metrics.todaySoldCents,
                                    exchangeRate = settings.exchangeRate,
                                    icon = Icons.Filled.PointOfSale,
                                    iconBg = ledgerColors.settledContainer,
                                    iconTint = ledgerColors.settled,
                                    amountColor = ledgerColors.settled,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("card_sold_today")
                                )
                            }

                            if (settings.enableExpensesModule) {
                                DailyMetricCard(
                                    title = "Gastos de Hoy",
                                    subtitle = "Salidas de caja",
                                    amountCents = metrics.todayExpenseCents,
                                    exchangeRate = settings.exchangeRate,
                                    icon = Icons.Filled.TrendingDown,
                                    iconBg = ledgerColors.debtContainer,
                                    iconTint = ledgerColors.debt,
                                    amountColor = ledgerColors.debt,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("card_spent_today")
                                )
                            }
                        }
                    }
                }
            }

            // Desglose rápido por método de pago del día
            item {
                PaymentMethodBreakdownCard(
                    items = paymentBreakdown,
                    exchangeRate = settings.exchangeRate
                )
            }

            // Últimos 5 movimientos realizados hoy
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Últimos 5 movimientos de hoy",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (recentMovements.isNotEmpty()) {
                        Text(
                            text = "Ver todos",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onGoToMovementsClick)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("home_see_all_movements")
                        )
                    }
                }
            }

            if (recentMovements.isEmpty()) {
                item {
                    if (!hasAnyData) {
                        FirstLaunchBanner(
                            enableSales = settings.enableSalesModule,
                            enableExpenses = settings.enableExpensesModule,
                            onQuickRegisterClick = onQuickRegisterClick,
                            onGoToInventoryClick = onGoToInventoryClick
                        )
                    } else {
                        EmptyTodayMovementsCard(
                            enableSales = settings.enableSalesModule,
                            enableExpenses = settings.enableExpensesModule,
                            onQuickRegisterClick = onQuickRegisterClick
                        )
                    }
                }
            } else {
                items(recentMovements, key = { it.movementWithItems.movement.id }) { item ->
                    MovementCard(
                        movementWithItems = item.movementWithItems,
                        exchangeRate = settings.exchangeRate,
                        customerName = item.customerName,
                        onViewReceiptClick = if (item.customerName != null) {
                            { onViewReceiptClick(item) }
                        } else null,
                        onPhotoClick = onPhotoClick,
                        onDeleteClick = { onDeleteMovementClick(item.movementWithItems.movement.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentMethodBreakdownCard(
    items: List<PaymentMethodBreakdownItem>,
    exchangeRate: Double
) {
    val ledgerColors = LocalLedgerColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("payment_breakdown_card"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ledgerColors.cardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Desglose por método de pago (Hoy)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))

            items.forEachIndexed { idx, entry ->
                val icon = when (entry.method) {
                    "Efectivo $" -> Icons.Filled.Payments
                    "Efectivo Bs" -> Icons.Filled.Payments
                    "Pago Móvil" -> Icons.Filled.PhoneAndroid
                    "Punto" -> Icons.Filled.CreditCard
                    else -> Icons.Filled.SyncAlt
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (entry.totalCents > 0L) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.displayLabel,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (entry.count > 0) {
                            Text(
                                text = "${entry.count} ${if (entry.count == 1) "operación" else "operaciones"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatCents(entry.totalCents),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (entry.totalCents > 0L) ledgerColors.settled
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatBsFromUsdCents(entry.totalCents, exchangeRate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (idx < items.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                }
            }
        }
    }
}

@Composable
private fun ExchangeRateBanner(
    settings: BusinessSettings,
    isRefreshingRate: Boolean,
    onRefreshBcvClick: () -> Unit,
    onEditClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onEditClick)
            .testTag("exchange_rate_banner"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, LocalLedgerColors.current.warningBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.CurrencyExchange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tasa del Día (Bs/$) · ${settings.rateLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "$1 = ${formatRate(settings.exchangeRate)}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !isRefreshingRate, onClick = onRefreshBcvClick)
                            .testTag("refresh_bcv_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isRefreshingRate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Actualizar tasa BCV",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Text(
                                text = if (isRefreshingRate) "…" else "Actualizar BCV",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onEditClick)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Editar tasa manualmente",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Editar",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            val updatedText = if (settings.rateUpdatedAtMillis > 0L) {
                "Última actualización: ${formatRelativeDateTime(settings.rateUpdatedAtMillis)}"
            } else {
                "Última actualización: Predeterminada (sin sincronizar)"
            }
            Text(
                text = updatedText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                modifier = Modifier.testTag("rate_last_updated_text")
            )
        }
    }
}

@Composable
private fun HeroReceivableCard(
    totalReceivableCents: Long,
    debtorsCount: Int,
    exchangeRate: Double,
    onClick: () -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .testTag("card_total_receivable"),
        shape = MaterialTheme.shapes.large,
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(ledgerColors.heroStart, ledgerColors.heroEnd)
                    )
                )
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = size.height * 0.65f,
                    center = Offset(size.width * 0.92f, size.height * 0.15f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = size.height * 0.45f,
                    center = Offset(size.width * 0.12f, size.height * 0.95f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Por Cobrar (En la calle)",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = formatCents(totalReceivableCents),
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White
                    )
                    Text(
                        text = formatBsFromUsdCents(totalReceivableCents, exchangeRate),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.16f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = debtorsCount.toString(),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = if (debtorsCount == 1) "Deudor" else "Deudores",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyMetricCard(
    title: String,
    subtitle: String,
    amountCents: Long,
    exchangeRate: Double,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    amountColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, LocalLedgerColors.current.cardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = formatCents(amountCents),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = amountColor,
                maxLines = 1
            )
            Text(
                text = formatBsFromUsdCents(amountCents, exchangeRate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FirstLaunchBanner(
    enableSales: Boolean,
    enableExpenses: Boolean,
    onQuickRegisterClick: () -> Unit,
    onGoToInventoryClick: () -> Unit
) {
    val description = when {
        enableSales && enableExpenses -> "Registra fiados, ventas o gastos en segundos con cálculo automático en $ y Bs."
        enableSales -> "Registra fiados y ventas en segundos con cálculo automático en $ y Bs."
        enableExpenses -> "Registra fiados y gastos en segundos con cálculo automático en $ y Bs."
        else -> "Registra fiados y abonos en segundos con cálculo automático en $ y Bs."
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, LocalLedgerColors.current.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_ledger_empty_1795654923461),
                contentDescription = "Ilustración de libreta y tienda",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(165.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Tu libreta está limpia",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onGoToInventoryClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Inventario")
                }
                Button(
                    onClick = onQuickRegisterClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Anotar Fiado")
                }
            }
        }
    }
}

@Composable
private fun EmptyTodayMovementsCard(
    enableSales: Boolean,
    enableExpenses: Boolean,
    onQuickRegisterClick: () -> Unit
) {
    val hintText = when {
        enableSales && enableExpenses -> "Toca el botón + para anotar un fiado, una venta o un gasto."
        enableSales -> "Toca el botón + para anotar un fiado o una venta."
        enableExpenses -> "Toca el botón + para anotar un fiado o un gasto."
        else -> "Toca el botón + para anotar un fiado rápidamente."
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, LocalLedgerColors.current.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.ReceiptLong,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sin movimientos hoy todavía",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = hintText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onQuickRegisterClick,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Anotar fiado")
            }
        }
    }
}
