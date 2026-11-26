package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.BusinessSettings
import com.example.data.MovementType
import com.example.data.RecentMovementDisplay
import com.example.ui.components.MovementCard
import com.example.ui.theme.LocalLedgerColors
import com.example.util.formatCents

private enum class AllMovementFilter(val label: String, val type: String?) {
    ALL("Todos", null),
    SALES("Ventas", MovementType.SALE),
    CREDITS("Fiados", MovementType.CREDIT),
    PAYMENTS("Abonos", MovementType.PAYMENT),
    EXPENSES("Gastos", MovementType.EXPENSE)
}

@Composable
fun MovimientosScreen(
    movements: List<RecentMovementDisplay>,
    settings: BusinessSettings,
    onViewReceiptClick: (RecentMovementDisplay) -> Unit,
    onPhotoClick: (String) -> Unit,
    onDeleteMovementClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(AllMovementFilter.ALL) }
    val ledgerColors = LocalLedgerColors.current

    val filteredMovements = remember(movements, searchQuery, selectedFilter) {
        val q = searchQuery.trim()
        movements.filter { item ->
            val mov = item.movementWithItems.movement
            val matchesType = selectedFilter.type == null || mov.type == selectedFilter.type
            val matchesQuery = q.isEmpty() ||
                mov.note.contains(q, ignoreCase = true) ||
                mov.paymentMethod.contains(q, ignoreCase = true) ||
                (item.customerName?.contains(q, ignoreCase = true) == true) ||
                item.movementWithItems.items.any { it.productName.contains(q, ignoreCase = true) }
            matchesType && matchesQuery
        }
    }

    val filteredTotalCents = remember(filteredMovements) {
        filteredMovements.sumOf { it.movementWithItems.movement.totalCents }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Movimientos",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${movements.size} operaciones registradas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (filteredMovements.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${filteredMovements.size} · ${formatCents(filteredTotalCents)}",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            if (movements.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar concepto, cliente o método…") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Limpiar búsqueda")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("movements_search_input")
                        )

                        val visibleFilters = remember(movements, settings.enableSalesModule, settings.enableExpensesModule) {
                            AllMovementFilter.entries.filter { filter ->
                                when (filter) {
                                    AllMovementFilter.SALES -> settings.enableSalesModule ||
                                        movements.any { it.movementWithItems.movement.type == MovementType.SALE }
                                    AllMovementFilter.EXPENSES -> settings.enableExpensesModule ||
                                        movements.any { it.movementWithItems.movement.type == MovementType.EXPENSE }
                                    else -> true
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            visibleFilters.forEach { filter ->
                                val count = if (filter.type == null) movements.size
                                else movements.count { it.movementWithItems.movement.type == filter.type }
                                FilterChip(
                                    selected = selectedFilter == filter,
                                    onClick = { selectedFilter = filter },
                                    label = { Text("${filter.label} ($count)") },
                                    modifier = Modifier.testTag("movements_filter_${filter.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            if (filteredMovements.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, ledgerColors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (movements.isEmpty()) "Sin movimientos registrados" else "Sin resultados",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (movements.isEmpty()) {
                                    "Pulsa el botón verde + para registrar fiados."
                                } else {
                                    "Prueba con otro filtro o búsqueda."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredMovements, key = { it.movementWithItems.movement.id }) { item ->
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
