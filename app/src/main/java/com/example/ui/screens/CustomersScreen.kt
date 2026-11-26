package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BusinessSettings
import com.example.data.CustomerSummary
import com.example.data.DebtSemaphore
import com.example.ui.components.CustomerAvatar
import com.example.ui.components.SemaphoreStatusPill
import com.example.ui.theme.LocalLedgerColors
import com.example.util.formatBsFromUsdCents
import com.example.util.formatCents

private enum class DebtFilter(val label: String) {
    ALL("Todos"),
    WITH_DEBT("Con Deuda"),
    SETTLED("Al Día")
}

@Composable
fun CustomersScreen(
    customers: List<CustomerSummary>,
    settings: BusinessSettings,
    onCustomerClick: (String) -> Unit,
    onAddCustomerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(DebtFilter.ALL) }
    val ledgerColors = LocalLedgerColors.current

    val filteredCustomers = remember(customers, searchQuery, selectedFilter) {
        val q = searchQuery.trim()
        customers.filter { customer ->
            val matchesFilter = when (selectedFilter) {
                DebtFilter.ALL -> true
                DebtFilter.WITH_DEBT -> customer.balanceCents > 0L
                DebtFilter.SETTLED -> customer.balanceCents <= 0L
            }
            val matchesQuery = q.isEmpty() ||
                customer.name.contains(q, ignoreCase = true) ||
                customer.phone.contains(q, ignoreCase = true)
            matchesFilter && matchesQuery
        }
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
                            text = "Fiados",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Clientes y Semáforo de Deudas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onAddCustomerClick,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("customers_add_button")
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cliente")
                    }
                }
            }

            if (customers.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar por nombre o teléfono…") },
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
                                .testTag("customers_search_input")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DebtFilter.entries.forEach { filter ->
                                val count = when (filter) {
                                    DebtFilter.ALL -> customers.size
                                    DebtFilter.WITH_DEBT -> customers.count { it.balanceCents > 0L }
                                    DebtFilter.SETTLED -> customers.count { it.balanceCents <= 0L }
                                }
                                FilterChip(
                                    selected = selectedFilter == filter,
                                    onClick = { selectedFilter = filter },
                                    label = { Text("${filter.label} ($count)") },
                                    modifier = Modifier.testTag("semaphore_filter_${filter.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            if (customers.isEmpty()) {
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
                                imageVector = Icons.Filled.Group,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Sin clientes registrados",
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Agrega tus clientes para llevar sus fiados con semáforo verde, amarillo (<15d) o rojo (+15d).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onAddCustomerClick,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("customers_empty_add_button")
                            ) {
                                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Agregar primer cliente")
                            }
                        }
                    }
                }
            } else if (filteredCustomers.isEmpty()) {
                item {
                    Text(
                        text = "No hay clientes que coincidan con el filtro.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(filteredCustomers, key = { it.id }) { customer ->
                    val (cardBg, cardBorder, amountColor) = when (customer.semaphore) {
                        DebtSemaphore.GREEN -> Triple(
                            ledgerColors.settledContainer.copy(alpha = 0.45f),
                            ledgerColors.settledBorder,
                            ledgerColors.settled
                        )
                        DebtSemaphore.YELLOW -> Triple(
                            ledgerColors.warningContainer.copy(alpha = 0.7f),
                            ledgerColors.warningBorder,
                            ledgerColors.warning
                        )
                        DebtSemaphore.RED -> Triple(
                            ledgerColors.debtContainer.copy(alpha = 0.75f),
                            ledgerColors.debtBorder,
                            ledgerColors.debt
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onCustomerClick(customer.id) }
                            .testTag("customer_semaphore_card_${customer.id}"),
                        shape = MaterialTheme.shapes.medium,
                        color = cardBg,
                        border = BorderStroke(1.2.dp, cardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CustomerAvatar(
                                name = customer.name,
                                semaphore = customer.semaphore
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = customer.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (customer.phone.isNotBlank()) {
                                        Icon(
                                            imageVector = Icons.Filled.Phone,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = customer.phone.ifBlank { "Sin teléfono" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                SemaphoreStatusPill(customer = customer)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (customer.balanceCents > 0L) {
                                        formatCents(customer.balanceCents)
                                    } else {
                                        "$0"
                                    },
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = amountColor
                                )
                                if (customer.balanceCents > 0L) {
                                    Text(
                                        text = formatBsFromUsdCents(customer.balanceCents, settings.exchangeRate),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
