package com.example.ai

import com.example.data.BusinessSettings
import com.example.data.CustomerSummary
import com.example.data.DebtSemaphore
import com.example.data.MovementType
import com.example.data.PaymentMethodBreakdownItem
import com.example.data.ProductEntity
import com.example.data.RecentMovementDisplay
import com.example.ui.DashboardMetrics
import com.example.util.formatBsFromUsdCents
import com.example.util.formatCents
import com.example.util.formatRate
import com.example.util.formatRelativeDateTime
import com.example.util.formatShortDate
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

data class AiChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String
)

sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Streaming(val partialText: String) : AiUiState
    data class Error(val message: String) : AiUiState
}

class AiRepository {

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    Eres FIAO IA, el asistente financiero integrado de una bodega / abasto en la app "FIAO - INV. ANICAR".
                    Reglas obligatorias:
                    1. Responde siempre en español natural, directo, amable y fácil de leer en un mostrador.
                    2. Basa TODAS tus cifras, nombres de clientes, productos y métodos de pago exclusivamente en el REPORTE EN VIVO proporcionado en el mensaje.
                    3. Nunca inventes ventas, clientes, productos ni montos. Si no hay datos sobre algo, dilo con claridad.
                    4. Cuando menciones montos, muestra el valor en Dólares ($) y su equivalente en Bolívares (Bs) si aporta claridad.
                    5. Sé conciso (máximo 4-6 líneas o viñetas cortas), destacando primero el dato exacto que pidió el comerciante.
                    """.trimIndent()
                )
            }
        )
    }

    fun askStream(
        question: String,
        businessSnapshot: String,
        recentMessages: List<AiChatMessage>
    ): Flow<String> = flow {
        val historyTurns = recentMessages
            .takeLast(6)
            .joinToString("\n") { msg ->
                val speaker = if (msg.isUser) "Comerciante" else "FIAO IA"
                "$speaker: ${msg.text}"
            }

        val fullPrompt = buildString {
            appendLine("=== REPORTE EN VIVO DEL NEGOCIO ===")
            appendLine(businessSnapshot)
            if (historyTurns.isNotBlank()) {
                appendLine("\n=== CONVERSACIÓN RECIENTE ===")
                appendLine(historyTurns)
            }
            appendLine("\n=== PREGUNTA ACTUAL DEL COMERCIANTE ===")
            appendLine(question.trim())
        }

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            model.generateContentStream(fullPrompt).collect { chunk ->
                val delta = chunk.text.orEmpty()
                if (delta.isNotEmpty()) {
                    accumulated.append(delta)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("El asistente no devolvió respuesta. Intenta de nuevo.")
        }
    }
}

fun buildBusinessSnapshot(
    settings: BusinessSettings,
    metrics: DashboardMetrics,
    paymentBreakdown: List<PaymentMethodBreakdownItem>,
    customers: List<CustomerSummary>,
    products: List<ProductEntity>,
    allMovements: List<RecentMovementDisplay>
): String = buildString {
    appendLine("Negocio: ${settings.businessName}")
    appendLine("Tasa del día (${settings.rateLabel}): $1 = ${formatRate(settings.exchangeRate)}")
    appendLine(
        "Resumen de HOY: Ventas/Ingresos en caja=${formatCents(metrics.todaySoldCents)} " +
            "(${formatBsFromUsdCents(metrics.todaySoldCents, settings.exchangeRate)}), " +
            "Gastos=${formatCents(metrics.todayExpenseCents)} " +
            "(${formatBsFromUsdCents(metrics.todayExpenseCents, settings.exchangeRate)}), " +
            "Total por cobrar en la calle=${formatCents(metrics.totalReceivableCents)} " +
            "(${formatBsFromUsdCents(metrics.totalReceivableCents, settings.exchangeRate)}) " +
            "entre ${metrics.debtorsCount} deudores."
    )

    appendLine("\nDesglose de ingresos de HOY por método de pago:")
    paymentBreakdown.forEach { item ->
        appendLine(
            "- ${item.displayLabel}: ${formatCents(item.totalCents)} " +
                "(${formatBsFromUsdCents(item.totalCents, settings.exchangeRate)}) [${item.count} ops]"
        )
    }

    appendLine("\nClientes y Semáforo de Deudas (${customers.size} clientes):")
    if (customers.isEmpty()) {
        appendLine("- No hay clientes registrados.")
    } else {
        customers.forEach { c ->
            val status = when (c.semaphore) {
                DebtSemaphore.GREEN -> "Verde (Al día)"
                DebtSemaphore.YELLOW -> "Amarillo (${c.debtAgeDays} días)"
                DebtSemaphore.RED -> "Rojo (${c.debtAgeDays} días, +15d vencida)"
            }
            val dueStr = c.nearestDueDateMillis?.let { ", vence ${formatShortDate(it)}" }.orEmpty()
            appendLine(
                "- ${c.name} (${c.phone.ifBlank { "sin teléfono" }}): debe ${formatCents(c.balanceCents)} " +
                    "(${formatBsFromUsdCents(c.balanceCents, settings.exchangeRate)}) · $status$dueStr"
            )
        }
    }

    val lowStock = products.filter { !it.isUnlimitedStock && it.stockQuantity < 5 }
    appendLine("\nInventario (${products.size} productos; ${lowStock.size} con menos de 5 unidades):")
    if (products.isEmpty()) {
        appendLine("- Inventario vacío.")
    } else {
        products.forEach { p ->
            val stockLabel = if (p.isUnlimitedStock) {
                "Siempre disponible (∞)"
            } else {
                val lowFlag = if (p.stockQuantity < 5) " [STOCK BAJO <5]" else ""
                "${p.stockQuantity} ${p.unit}$lowFlag"
            }
            appendLine(
                "- ${p.name} (${p.category}): stock=$stockLabel, " +
                    "precio=${formatCents(p.priceCents)} (${formatBsFromUsdCents(p.priceCents, settings.exchangeRate)}), " +
                    "costo=${formatCents(p.costCents)}"
            )
        }
    }

    appendLine("\nMovimientos recientes (${allMovements.take(15).size} de ${allMovements.size} totales):")
    if (allMovements.isEmpty()) {
        appendLine("- Sin movimientos registrados.")
    } else {
        allMovements.take(15).forEach { entry ->
            val m = entry.movementWithItems.movement
            val typeLabel = when (m.type) {
                MovementType.SALE -> "Venta"
                MovementType.CREDIT -> "Fiado"
                MovementType.PAYMENT -> "Abono"
                MovementType.EXPENSE -> "Gasto"
                else -> m.type
            }
            val cust = entry.customerName?.let { " [$it]" }.orEmpty()
            val method = m.paymentMethod.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            val itemsDesc = entry.movementWithItems.items
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ") { "${it.quantity}x ${it.productName}" }
                ?: m.note
            val desc = itemsDesc.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            appendLine(
                "- ${formatRelativeDateTime(m.createdAt)} · $typeLabel$cust$method: " +
                    "${formatCents(m.totalCents)}$desc"
            )
        }
    }
}
