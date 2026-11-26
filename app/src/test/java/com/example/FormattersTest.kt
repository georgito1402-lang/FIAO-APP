package com.example

import com.example.ai.buildBusinessSnapshot
import com.example.data.BusinessSettings
import com.example.data.CustomerSummary
import com.example.data.DebtSemaphore
import com.example.data.MovementType
import com.example.data.PaymentMethodBreakdownItem
import com.example.data.ProductEntity
import com.example.data.ReceiptPreviewData
import com.example.data.paymentMethodDisplayName
import com.example.ui.DashboardMetrics
import com.example.ui.components.buildWhatsAppReceiptMessage
import com.example.util.bsInputToUsdCents
import com.example.util.centsToInputString
import com.example.util.customerInitials
import com.example.util.formatBsFromUsdCents
import com.example.util.formatCents
import com.example.util.parseAmountToCents
import com.example.util.sanitizeWhatsAppPhone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersTest {

    @Test
    fun `formatCents formats whole and fractional amounts cleanly`() {
        assertEquals("$0", formatCents(0L))
        assertEquals("$15", formatCents(1500L))
        assertEquals("$1,250", formatCents(125000L))
        assertEquals("$12.50", formatCents(1250L))
        assertEquals("-$5.25", formatCents(-525L))
    }

    @Test
    fun `parseAmountToCents handles dot, comma, currency symbol, and invalid inputs`() {
        assertEquals(1250L, parseAmountToCents("12.50"))
        assertEquals(1250L, parseAmountToCents("12,5"))
        assertEquals(500000L, parseAmountToCents("$5000"))
        assertNull(parseAmountToCents(""))
        assertNull(parseAmountToCents("abc"))
        assertNull(parseAmountToCents("-10"))
    }

    @Test
    fun `bs conversion converts cleanly between Bs and USD cents`() {
        assertEquals("Bs 450,00", formatBsFromUsdCents(1000L, 45.0))
        assertEquals(1000L, bsInputToUsdCents("450", 45.0))
        assertEquals(200L, bsInputToUsdCents("90,00", 45.0))
        assertNull(bsInputToUsdCents("100", 0.0))
    }

    @Test
    fun `sanitizeWhatsAppPhone formats local Venezuelan numbers and keeps international`() {
        assertEquals("584141234567", sanitizeWhatsAppPhone("0414-123 4567"))
        assertEquals("584129876543", sanitizeWhatsAppPhone("+58 412 987 6543"))
        assertEquals("", sanitizeWhatsAppPhone(""))
    }

    @Test
    fun `buildWhatsAppReceiptMessage includes business name, customer, amount, and remaining balance`() {
        val msg = buildWhatsAppReceiptMessage(
            receipt = ReceiptPreviewData(
                movementId = "1",
                customerName = "Carlos Pérez",
                customerPhone = "04141234567",
                movementType = MovementType.PAYMENT,
                amountCents = 1000L,
                remainingBalanceCents = 500L,
                paymentMethod = "Pago Móvil",
                note = "",
                itemsSummary = "Abono parcial",
                createdAt = 1764100000000L
            ),
            settings = BusinessSettings(exchangeRate = 45.0, rateLabel = "Tasa BCV", businessName = "Bodega Central")
        )
        assertTrue(msg.contains("Bodega Central"))
        assertTrue(msg.contains("Carlos Pérez"))
        assertTrue(msg.contains("$10"))
        assertTrue(msg.contains("Bs 450,00"))
        assertTrue(msg.contains("$5"))
    }

    @Test
    fun `centsToInputString omits trailing zeroes for whole amounts`() {
        assertEquals("", centsToInputString(0L))
        assertEquals("25", centsToInputString(2500L))
        assertEquals("25.50", centsToInputString(2550L))
    }

    @Test
    fun `customerInitials extracts one or two initials`() {
        assertEquals("ML", customerInitials("María López"))
        assertEquals("JU", customerInitials("Juan"))
        assertEquals("?", customerInitials("   "))
    }

    @Test
    fun `paymentMethodDisplayName maps Punto to Punto de Venta`() {
        assertEquals("Punto de Venta", paymentMethodDisplayName("Punto"))
        assertEquals("Pago Móvil", paymentMethodDisplayName("Pago Móvil"))
    }

    @Test
    fun `buildBusinessSnapshot includes debtors, payment breakdown, and low stock warnings`() {
        val snapshot = buildBusinessSnapshot(
            settings = BusinessSettings(exchangeRate = 45.0, rateLabel = "Tasa BCV", businessName = "Abasto Sol"),
            metrics = DashboardMetrics(todaySoldCents = 1500L, todayExpenseCents = 300L, totalReceivableCents = 2000L, debtorsCount = 1),
            paymentBreakdown = listOf(PaymentMethodBreakdownItem("Pago Móvil", "Pago Móvil", 1500L, 2)),
            customers = listOf(
                CustomerSummary(
                    id = "1",
                    name = "Ana Bolívar",
                    phone = "04241112233",
                    notes = "",
                    createdAt = 1L,
                    totalCreditCents = 2000L,
                    totalPaymentCents = 0L,
                    lastMovementAt = 1L,
                    debtSinceAt = 1L,
                    nearestDueDateMillis = null,
                    debtAgeDays = 18,
                    semaphore = DebtSemaphore.RED
                )
            ),
            products = listOf(
                ProductEntity(id = "1", name = "Empanada", priceCents = 150L, costCents = 80L, stockQuantity = 0, isUnlimitedStock = true, category = "Comidas", unit = "u"),
                ProductEntity(id = "2", name = "Chocolate", priceCents = 110L, costCents = 75L, stockQuantity = 3, isUnlimitedStock = false, category = "Dulces", unit = "paq")
            ),
            allMovements = emptyList()
        )
        assertTrue(snapshot.contains("Abasto Sol"))
        assertTrue(snapshot.contains("Ana Bolívar"))
        assertTrue(snapshot.contains("Pago Móvil: $15"))
        assertTrue(snapshot.contains("Siempre disponible (∞)"))
        assertTrue(snapshot.contains("1 con menos de 5 unidades"))
        assertTrue(snapshot.contains("[STOCK BAJO <5]"))
    }
}
