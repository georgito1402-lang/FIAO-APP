package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

const val DEFAULT_BUSINESS_NAME = "FIAO - INV. ANICAR"

object MovementType {
    const val SALE = "SALE"
    const val CREDIT = "CREDIT"
    const val PAYMENT = "PAYMENT"
    const val EXPENSE = "EXPENSE"
}

val PAYMENT_METHODS = listOf(
    "Efectivo $",
    "Efectivo Bs",
    "Pago Móvil",
    "Punto",
    "Transferencia"
)

val PRODUCT_CATEGORIES = listOf(
    "Comidas",
    "Bebidas",
    "Dulces"
)

fun paymentMethodDisplayName(method: String): String = when (method) {
    "Punto" -> "Punto de Venta"
    else -> method
}

enum class DebtSemaphore {
    GREEN,  // Al día
    YELLOW, // Menos de 15 días
    RED     // 15 días o más
}

enum class CloudSyncStatus {
    SYNCED,
    SYNCING,
    OFFLINE
}

// --- Firestore Deserialization DTOs (No `is`-prefixed Booleans; @DocumentId excluded from writes) ---

@Keep
@IgnoreExtraProperties
data class FirestoreClienteDoc(
    @DocumentId val id: String = "",
    val name: String = "",
    val phone: String = "",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreProductoDoc(
    @DocumentId val id: String = "",
    val name: String = "",
    val priceCents: Long = 0L,
    val costCents: Long = 0L,
    val stockQuantity: Long = 0L,
    val unlimitedStock: Boolean = false,
    val category: String = "Comidas",
    val unit: String = "u",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreTiendaConfigDoc(
    @DocumentId val id: String = "",
    val storeCode: String = "",
    val exchangeRate: Double = 45.0,
    val rateLabel: String = "Tasa BCV",
    val rateUpdatedAtMillis: Long = 0L,
    val businessName: String = DEFAULT_BUSINESS_NAME,
    val seededDemo: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

// --- App Domain Models ---

data class ProductEntity(
    val id: String = "",
    val name: String = "",
    val priceCents: Long = 0L,
    val costCents: Long = 0L,
    val stockQuantity: Int = 0,
    val isUnlimitedStock: Boolean = false,
    val category: String = "Comidas",
    val unit: String = "u",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class CustomerEntity(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class LedgerMovementEntity(
    val id: String = "",
    val customerId: String? = null,
    val type: String = MovementType.SALE,
    val totalCents: Long = 0L,
    val paymentMethod: String = "",
    val note: String = "",
    val photoPath: String? = null,
    val dueDateMillis: Long? = null,
    val rateUsed: Double = 1.0,
    val createdAt: Long = System.currentTimeMillis()
)

data class LedgerLineItemEntity(
    val id: String = "",
    val movementId: String = "",
    val productId: String? = null,
    val productName: String = "",
    val unitPriceCents: Long = 0L,
    val quantity: Int = 1,
    val subtotalCents: Long = 0L
)

data class CustomerSummary(
    val id: String,
    val name: String,
    val phone: String,
    val notes: String,
    val createdAt: Long,
    val totalCreditCents: Long,
    val totalPaymentCents: Long,
    val lastMovementAt: Long?,
    val debtSinceAt: Long?,
    val nearestDueDateMillis: Long?,
    val debtAgeDays: Int,
    val semaphore: DebtSemaphore
) {
    val balanceCents: Long
        get() = totalCreditCents - totalPaymentCents
}

data class MovementWithItems(
    val movement: LedgerMovementEntity,
    val items: List<LedgerLineItemEntity>
)

data class RecentMovementDisplay(
    val movementWithItems: MovementWithItems,
    val customerName: String?,
    val customerPhone: String?
)

data class TodayTotals(
    val todaySoldCents: Long = 0L,
    val todayExpenseCents: Long = 0L,
    val todayCreditCents: Long = 0L,
    val todayPaymentCents: Long = 0L
)

data class PaymentMethodBreakdownItem(
    val method: String,
    val displayLabel: String,
    val totalCents: Long,
    val count: Int
)

data class CartItemDraft(
    val key: String,
    val productId: String?,
    val productName: String,
    val unitPriceCents: Long,
    val unit: String,
    val quantity: Int
) {
    val subtotalCents: Long
        get() = unitPriceCents * quantity
}

data class ReceiptPreviewData(
    val movementId: String,
    val customerName: String,
    val customerPhone: String,
    val movementType: String,
    val amountCents: Long,
    val remainingBalanceCents: Long,
    val paymentMethod: String,
    val note: String,
    val itemsSummary: String,
    val createdAt: Long,
    val dueDateMillis: Long? = null
)

data class StoreConfig(
    val storeCode: String = "",
    val exchangeRate: Double = 45.0,
    val rateLabel: String = "Tasa BCV",
    val rateUpdatedAtMillis: Long = 0L,
    val businessName: String = DEFAULT_BUSINESS_NAME,
    val seededDemo: Boolean = false
)
