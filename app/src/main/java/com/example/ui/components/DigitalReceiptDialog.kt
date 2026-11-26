package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.BusinessSettings
import com.example.data.DEFAULT_BUSINESS_NAME
import com.example.data.MovementType
import com.example.data.ReceiptPreviewData
import com.example.ui.theme.LocalLedgerColors
import com.example.util.formatBsFromUsdCents
import com.example.util.formatCents
import com.example.util.formatFullReceiptDate
import com.example.util.formatShortDate
import com.example.util.sanitizeWhatsAppPhone
import java.net.URLEncoder

@Composable
fun DigitalReceiptDialog(
    receipt: ReceiptPreviewData,
    businessSettings: BusinessSettings,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val ledgerColors = LocalLedgerColors.current
    val isPayment = receipt.movementType == MovementType.PAYMENT

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Comprobante Digital",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_receipt_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar comprobante")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Receipt card preview
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, ledgerColors.cardBorder),
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // App logo + Business Name
                        Image(
                            painter = painterResource(id = R.drawable.app_icon_ledger_1795654922778),
                            contentDescription = "Logo de $DEFAULT_BUSINESS_NAME",
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = businessSettings.businessName.ifBlank { DEFAULT_BUSINESS_NAME },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = formatFullReceiptDate(receipt.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isPayment) ledgerColors.settledContainer else ledgerColors.warningContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isPayment) ledgerColors.settled else ledgerColors.warning
                                )
                                Text(
                                    text = if (isPayment) "COMPROBANTE DE ABONO" else "NOTA DE FIADO",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isPayment) ledgerColors.onSettledContainer else ledgerColors.onWarningContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(14.dp))

                        ReceiptRow(label = "Cliente", value = receipt.customerName)
                        if (receipt.paymentMethod.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            ReceiptRow(label = "Método", value = receipt.paymentMethod)
                        }
                        if (receipt.itemsSummary.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            ReceiptRow(label = "Concepto", value = receipt.itemsSummary)
                        }
                        if (!isPayment && receipt.dueDateMillis != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            ReceiptRow(label = "Fecha límite", value = formatShortDate(receipt.dueDateMillis))
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = if (isPayment) "Monto abonado" else "Monto fiado",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatCents(receipt.amountCents),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = formatBsFromUsdCents(receipt.amountCents, businessSettings.exchangeRate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = if (receipt.remainingBalanceCents > 0L) {
                                ledgerColors.debtContainer
                            } else {
                                ledgerColors.settledContainer
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (receipt.remainingBalanceCents > 0L) "Saldo restante" else "Estado",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (receipt.remainingBalanceCents > 0L) {
                                        ledgerColors.onDebtContainer
                                    } else {
                                        ledgerColors.onSettledContainer
                                    }
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (receipt.remainingBalanceCents > 0L) {
                                            formatCents(receipt.remainingBalanceCents)
                                        } else {
                                            "¡Cuenta al día!"
                                        },
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (receipt.remainingBalanceCents > 0L) {
                                            ledgerColors.onDebtContainer
                                        } else {
                                            ledgerColors.onSettledContainer
                                        }
                                    )
                                    if (receipt.remainingBalanceCents > 0L) {
                                        Text(
                                            text = formatBsFromUsdCents(
                                                receipt.remainingBalanceCents,
                                                businessSettings.exchangeRate
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ledgerColors.onDebtContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        val message = buildWhatsAppReceiptMessage(receipt, businessSettings)
                        openWhatsAppChat(context, receipt.customerPhone, message)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ledgerColors.whatsapp,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("send_whatsapp_receipt_button")
                ) {
                    Icon(Icons.Filled.Chat, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enviar Comprobante por WhatsApp",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        val message = buildWhatsAppReceiptMessage(receipt, businessSettings)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Compartir comprobante"))
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("share_receipt_other_button")
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compartir por otra app")
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

fun buildWhatsAppReceiptMessage(
    receipt: ReceiptPreviewData,
    settings: BusinessSettings
): String {
    val isPayment = receipt.movementType == MovementType.PAYMENT
    val title = if (isPayment) "Comprobante de Abono" else "Nota de Fiado"
    val displayBusinessName = settings.businessName.ifBlank { DEFAULT_BUSINESS_NAME }
    val sb = StringBuilder()
    sb.append("*$displayBusinessName* — $title\n")
    sb.append("Fecha: ${formatFullReceiptDate(receipt.createdAt)}\n")
    sb.append("Cliente: ${receipt.customerName}\n")
    if (receipt.itemsSummary.isNotBlank()) {
        sb.append("Concepto: ${receipt.itemsSummary}\n")
    }
    if (receipt.paymentMethod.isNotBlank()) {
        sb.append("Método: ${receipt.paymentMethod}\n")
    }
    if (!isPayment && receipt.dueDateMillis != null) {
        sb.append("Fecha límite: ${formatShortDate(receipt.dueDateMillis)}\n")
    }
    sb.append(
        "${if (isPayment) "Monto abonado" else "Monto fiado"}: " +
            "${formatCents(receipt.amountCents)} (${formatBsFromUsdCents(receipt.amountCents, settings.exchangeRate)})\n"
    )
    if (receipt.remainingBalanceCents > 0L) {
        sb.append(
            "*Saldo pendiente:* ${formatCents(receipt.remainingBalanceCents)} " +
                "(${formatBsFromUsdCents(receipt.remainingBalanceCents, settings.exchangeRate)})\n"
        )
    } else {
        sb.append("*Saldo pendiente:* ¡Cuenta al día!\n")
    }
    sb.append("\n¡Gracias por su preferencia!")
    return sb.toString()
}

private fun openWhatsAppChat(context: Context, rawPhone: String, message: String) {
    val cleanPhone = sanitizeWhatsAppPhone(rawPhone)
    val encoded = URLEncoder.encode(message, "UTF-8")
    if (cleanPhone.isNotEmpty()) {
        val url = "https://wa.me/$cleanPhone?text=$encoded"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            context.startActivity(intent)
            return
        } catch (_: Exception) {
            // Fall back to generic share below
        }
    }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
        setPackage("com.whatsapp")
    }
    try {
        context.startActivity(sendIntent)
    } catch (_: Exception) {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(fallback, "Enviar comprobante"))
    }
}
