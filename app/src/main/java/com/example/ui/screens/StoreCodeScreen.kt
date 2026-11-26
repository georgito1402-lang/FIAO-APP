package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.isValidStoreCode
import com.example.data.normalizeStoreCode
import com.example.ui.theme.LocalLedgerColors

@Composable
fun StoreCodeScreen(
    previousStoreCode: String = "",
    onConfirmStoreCode: (String) -> Unit,
    onCancelChange: (() -> Unit)? = null
) {
    val ledgerColors = LocalLedgerColors.current
    var codeInput by remember(previousStoreCode) { mutableStateOf("") }
    var codeError by remember { mutableStateOf<String?>(null) }

    fun submitCode() {
        val normalized = normalizeStoreCode(codeInput)
        if (!isValidStoreCode(normalized)) {
            codeError = "Usa de 4 a 32 caracteres: letras, números, '-' o '_' (ej. ANICAR2026)"
        } else {
            codeError = null
            onConfirmStoreCode(normalized)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, ledgerColors.cardBorder),
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_icon_ledger_1795654922778),
                        contentDescription = "Logo de FIAO",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Text(
                        text = "FIAO - Ventas y Fiados",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = if (previousStoreCode.isBlank()) {
                            "Ingresa un Código de Tienda para crear tu negocio o conectarte al mismo negocio desde varios celulares."
                        } else {
                            "Tienda actual: $previousStoreCode. Ingresa el nuevo Código de Tienda para cambiar o conectar otro negocio."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { raw ->
                            codeInput = raw.uppercase().filter { ch ->
                                ch.isLetterOrDigit() || ch == '-' || ch == '_'
                            }.take(32)
                            codeError = null
                        },
                        label = { Text("Código de Tienda *") },
                        placeholder = { Text("Ej. ANICAR2026") },
                        leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                        isError = codeError != null,
                        supportingText = {
                            Text(
                                text = codeError ?: "Sin correos ni contraseñas. Solo escribe el mismo código en cada celular."
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submitCode() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("store_code_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ejemplo rápido:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilterChip(
                            selected = codeInput == "ANICAR2026",
                            onClick = {
                                codeInput = "ANICAR2026"
                                codeError = null
                            },
                            label = { Text("ANICAR2026") },
                            modifier = Modifier.testTag("store_code_example_chip")
                        )
                    }

                    Button(
                        onClick = { submitCode() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("store_code_submit_button")
                    ) {
                        Icon(Icons.Filled.Storefront, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Entrar a la tienda",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    if (onCancelChange != null && previousStoreCode.isNotBlank()) {
                        OutlinedButton(
                            onClick = onCancelChange,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("store_code_cancel_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Volver a $previousStoreCode")
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StoreCodeFeatureRow(
                            icon = Icons.Filled.Devices,
                            text = "Todos los celulares con este código comparten los mismos datos al instante"
                        )
                        StoreCodeFeatureRow(
                            icon = Icons.Filled.CloudDone,
                            text = "Clientes, fiados, abonos, ventas, inventario y tasa en tiempo real"
                        )
                        StoreCodeFeatureRow(
                            icon = Icons.Filled.OfflinePin,
                            text = "Funciona sin internet y sincroniza al reconectar"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreCodeFeatureRow(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
