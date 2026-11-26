package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class SyncMetadataState(
    val hasPendingWrites: Boolean = false,
    val isFromCache: Boolean = true
)

class LedgerRepository(
    private val db: FirebaseFirestore,
    rawStoreCode: String
) {
    constructor(context: Context, rawStoreCode: String) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        rawStoreCode
    )

    val storeCode: String = normalizeStoreCode(rawStoreCode)

    private val storeRef = db.collection("tiendas").document(storeCode)
    private val clientesCol = storeRef.collection("clientes")
    private val inventarioCol = storeRef.collection("inventario")
    private val movimientosCol = storeRef.collection("movimientos")
    private val tiendaConfigDoc = storeRef.collection("configuracion").document("tienda")

    private val _clientesMeta = MutableStateFlow(SyncMetadataState())
    private val _inventarioMeta = MutableStateFlow(SyncMetadataState())
    private val _movimientosMeta = MutableStateFlow(SyncMetadataState())
    private val _configMeta = MutableStateFlow(SyncMetadataState())

    val syncMetadataFlow: Flow<SyncMetadataState> = combine(
        _clientesMeta,
        _inventarioMeta,
        _movimientosMeta,
        _configMeta
    ) { c, i, m, cfg ->
        SyncMetadataState(
            hasPendingWrites = c.hasPendingWrites || i.hasPendingWrites || m.hasPendingWrites || cfg.hasPendingWrites,
            isFromCache = c.isFromCache && i.isFromCache && m.isFromCache && cfg.isFromCache
        )
    }

    private fun updateMeta(target: MutableStateFlow<SyncMetadataState>, snapshot: QuerySnapshot) {
        target.value = SyncMetadataState(
            hasPendingWrites = snapshot.metadata.hasPendingWrites(),
            isFromCache = snapshot.metadata.isFromCache()
        )
    }

    // --- Real-time `onSnapshot` / `.snapshots()` Streams ---

    fun observeStoreConfig(): Flow<StoreConfig> {
        val path = tiendaConfigDoc.path
        return tiendaConfigDoc
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                _configMeta.value = SyncMetadataState(
                    hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                    isFromCache = snapshot.metadata.isFromCache()
                )
                if (!snapshot.exists()) {
                    StoreConfig(storeCode = storeCode)
                } else {
                    val dto = snapshot.toObject(
                        FirestoreTiendaConfigDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    StoreConfig(
                        storeCode = dto?.storeCode?.ifBlank { storeCode } ?: storeCode,
                        exchangeRate = (dto?.exchangeRate ?: 45.0).coerceAtLeast(0.01),
                        rateLabel = dto?.rateLabel?.ifBlank { "Tasa BCV" } ?: "Tasa BCV",
                        rateUpdatedAtMillis = dto?.rateUpdatedAtMillis ?: 0L,
                        businessName = dto?.businessName?.ifBlank { DEFAULT_BUSINESS_NAME } ?: DEFAULT_BUSINESS_NAME,
                        seededDemo = dto?.seededDemo ?: false
                    )
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null) {
                    handleFirestoreError(exceptionToLog, OperationType.GET, path)
                }
                throw error
            }
    }

    fun observeActiveProducts(): Flow<List<ProductEntity>> {
        val path = inventarioCol.path
        return inventarioCol
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                updateMeta(_inventarioMeta, snapshot)
                snapshot.documents.mapNotNull { doc ->
                    val dto = doc.toObject(
                        FirestoreProductoDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: return@mapNotNull null
                    if (!dto.active) return@mapNotNull null
                    ProductEntity(
                        id = doc.id,
                        name = dto.name,
                        priceCents = dto.priceCents,
                        costCents = dto.costCents,
                        stockQuantity = dto.stockQuantity.toInt(),
                        isUnlimitedStock = dto.unlimitedStock,
                        category = dto.category.takeIf { it in PRODUCT_CATEGORIES } ?: PRODUCT_CATEGORIES.first(),
                        unit = dto.unit.ifBlank { "u" },
                        isActive = dto.active,
                        createdAt = dto.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                    )
                }.sortedBy { it.name.lowercase() }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    private fun observeRawCustomers(): Flow<List<CustomerEntity>> {
        val path = clientesCol.path
        return clientesCol
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                updateMeta(_clientesMeta, snapshot)
                snapshot.documents.mapNotNull { doc ->
                    val dto = doc.toObject(
                        FirestoreClienteDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: return@mapNotNull null
                    CustomerEntity(
                        id = doc.id,
                        name = dto.name,
                        phone = dto.phone,
                        notes = dto.notes,
                        createdAt = dto.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                    )
                }.sortedBy { it.name.lowercase() }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    private fun observeRawMovements(): Flow<List<MovementWithItems>> {
        val path = movimientosCol.path
        return movimientosCol
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                updateMeta(_movimientosMeta, snapshot)
                snapshot.documents.map { doc -> docToMovementWithItems(doc) }
                    .sortedByDescending { it.movement.createdAt }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    private fun docToMovementWithItems(doc: DocumentSnapshot): MovementWithItems {
        val data = doc.data ?: emptyMap()
        val estTimestamp = doc.getTimestamp(
            "createdAt",
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )?.toDate()?.time
        val activityAt = (data["activityAtMillis"] as? Number)?.toLong()
            ?: estTimestamp
            ?: System.currentTimeMillis()
        val movement = LedgerMovementEntity(
            id = doc.id,
            customerId = data["customerId"] as? String,
            type = (data["type"] as? String) ?: MovementType.SALE,
            totalCents = (data["totalCents"] as? Number)?.toLong() ?: 0L,
            paymentMethod = (data["paymentMethod"] as? String) ?: "",
            note = (data["note"] as? String) ?: "",
            photoPath = data["photoPath"] as? String,
            dueDateMillis = (data["dueDateMillis"] as? Number)?.toLong(),
            rateUsed = (data["rateUsed"] as? Number)?.toDouble() ?: 1.0,
            createdAt = activityAt
        )
        val rawItems = (data["items"] as? List<*>) ?: emptyList<Any?>()
        val lineItems = rawItems.mapIndexedNotNull { idx, raw ->
            val map = raw as? Map<*, *> ?: return@mapIndexedNotNull null
            LedgerLineItemEntity(
                id = "${doc.id}_$idx",
                movementId = doc.id,
                productId = map["productId"] as? String,
                productName = (map["productName"] as? String) ?: "",
                unitPriceCents = (map["unitPriceCents"] as? Number)?.toLong() ?: 0L,
                quantity = (map["quantity"] as? Number)?.toInt() ?: 1,
                subtotalCents = (map["subtotalCents"] as? Number)?.toLong() ?: 0L
            )
        }
        return MovementWithItems(movement = movement, items = lineItems)
    }

    fun observeCustomerSummaries(): Flow<List<CustomerSummary>> = combine(
        observeRawCustomers(),
        observeRawMovements()
    ) { customers, allMovements ->
        val movementsByCustomer = allMovements
            .map { it.movement }
            .filter { !it.customerId.isNullOrBlank() }
            .groupBy { it.customerId!! }

        val now = System.currentTimeMillis()
        customers.map { c ->
            computeCustomerSummary(
                customer = c,
                movements = movementsByCustomer[c.id].orEmpty(),
                nowMillis = now
            )
        }.sortedWith(
            compareByDescending<CustomerSummary> { it.balanceCents }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
    }

    fun observeCustomerSummary(customerId: String): Flow<CustomerSummary?> =
        observeCustomerSummaries().map { summaries ->
            summaries.firstOrNull { it.id == customerId }
        }

    fun observeCustomerMovements(customerId: String): Flow<List<MovementWithItems>> =
        observeRawMovements().map { all ->
            all.filter { it.movement.customerId == customerId }
        }

    fun observeAllMovements(): Flow<List<RecentMovementDisplay>> = combine(
        observeRawMovements(),
        observeRawCustomers()
    ) { movements, customers ->
        val customerMap = customers.associateBy { it.id }
        movements.map { m ->
            val cust = m.movement.customerId?.let { customerMap[it] }
            RecentMovementDisplay(
                movementWithItems = m,
                customerName = cust?.name,
                customerPhone = cust?.phone
            )
        }
    }

    fun observeTodayRecentMovements(limit: Int = 5): Flow<List<RecentMovementDisplay>> =
        observeAllMovements().map { all ->
            val startToday = startOfTodayMillis()
            all.filter { it.movementWithItems.movement.createdAt >= startToday }
                .take(limit)
        }

    fun observeTodayPaymentBreakdown(): Flow<List<PaymentMethodBreakdownItem>> =
        observeRawMovements().map { movements ->
            val startToday = startOfTodayMillis()
            val todayCashIn = movements
                .map { it.movement }
                .filter {
                    it.createdAt >= startToday &&
                        (it.type == MovementType.SALE || it.type == MovementType.PAYMENT)
                }

            val totalsByMethod = mutableMapOf<String, Pair<Long, Int>>()
            PAYMENT_METHODS.forEach { method -> totalsByMethod[method] = 0L to 0 }

            for (m in todayCashIn) {
                val normalized = normalizePaymentMethod(m.paymentMethod)
                val current = totalsByMethod[normalized] ?: (0L to 0)
                totalsByMethod[normalized] = (current.first + m.totalCents) to (current.second + 1)
            }

            PAYMENT_METHODS.map { method ->
                val pair = totalsByMethod[method] ?: (0L to 0)
                PaymentMethodBreakdownItem(
                    method = method,
                    displayLabel = paymentMethodDisplayName(method),
                    totalCents = pair.first,
                    count = pair.second
                )
            }
        }

    fun observeTodayTotals(): Flow<TodayTotals> = observeRawMovements().map { movements ->
        val startToday = startOfTodayMillis()
        var sold = 0L
        var expense = 0L
        var credit = 0L
        var payment = 0L
        for (entry in movements) {
            val m = entry.movement
            if (m.createdAt < startToday) continue
            when (m.type) {
                MovementType.SALE -> sold += m.totalCents
                MovementType.EXPENSE -> expense += m.totalCents
                MovementType.CREDIT -> credit += m.totalCents
                MovementType.PAYMENT -> payment += m.totalCents
            }
        }
        TodayTotals(
            todaySoldCents = sold,
            todayExpenseCents = expense,
            todayCreditCents = credit,
            todayPaymentCents = payment
        )
    }

    // --- Store Bootstrap & Idempotent Demo Seeding (No Duplication Across Phones) ---

    suspend fun ensureInitialStoreAndDemoData(): Result<Boolean> {
        val path = tiendaConfigDoc.path
        return try {
            require(isValidStoreCode(storeCode)) { "Código de Tienda inválido" }
            val tiendaSnap = tiendaConfigDoc.get().await()
            if (!tiendaSnap.exists()) {
                val tiendaPayload = mapOf(
                    "storeCode" to storeCode,
                    "exchangeRate" to 45.0,
                    "rateLabel" to "Tasa BCV",
                    "rateUpdatedAtMillis" to 0L,
                    "businessName" to DEFAULT_BUSINESS_NAME,
                    "seededDemo" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                tiendaConfigDoc.set(tiendaPayload).await()
                seedDeterministicDemoData(rate = 45.0)
            }
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateStoreSettings(
        exchangeRate: Double,
        rateLabel: String,
        businessName: String
    ): Result<Unit> {
        val path = tiendaConfigDoc.path
        return try {
            require(isValidStoreCode(storeCode)) { "Código de Tienda inválido" }
            val safeRate = exchangeRate.coerceAtLeast(0.01)
            val safeLabel = rateLabel.trim().ifEmpty { "Tasa BCV" }.take(40)
            val safeName = businessName.trim().ifEmpty { DEFAULT_BUSINESS_NAME }.take(120)
            val snap = tiendaConfigDoc.get().await()
            if (snap.exists()) {
                tiendaConfigDoc.update(
                    mapOf(
                        "exchangeRate" to safeRate,
                        "rateLabel" to safeLabel,
                        "rateUpdatedAtMillis" to System.currentTimeMillis(),
                        "businessName" to safeName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                tiendaConfigDoc.set(
                    mapOf(
                        "storeCode" to storeCode,
                        "exchangeRate" to safeRate,
                        "rateLabel" to safeLabel,
                        "rateUpdatedAtMillis" to System.currentTimeMillis(),
                        "businessName" to safeName,
                        "seededDemo" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateBcvRate(
        exchangeRate: Double,
        updatedAtMillis: Long = System.currentTimeMillis()
    ): Result<Unit> {
        val path = tiendaConfigDoc.path
        return try {
            require(isValidStoreCode(storeCode)) { "Código de Tienda inválido" }
            val safeRate = exchangeRate.coerceAtLeast(0.01)
            val snap = tiendaConfigDoc.get().await()
            if (snap.exists()) {
                tiendaConfigDoc.update(
                    mapOf(
                        "exchangeRate" to safeRate,
                        "rateLabel" to "Tasa BCV",
                        "rateUpdatedAtMillis" to updatedAtMillis,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                tiendaConfigDoc.set(
                    mapOf(
                        "storeCode" to storeCode,
                        "exchangeRate" to safeRate,
                        "rateLabel" to "Tasa BCV",
                        "rateUpdatedAtMillis" to updatedAtMillis,
                        "businessName" to DEFAULT_BUSINESS_NAME,
                        "seededDemo" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- CRUD Mutations (All return Result<T>) ---

    suspend fun saveProduct(
        id: String = "",
        name: String,
        priceCents: Long,
        costCents: Long,
        stockQuantity: Int,
        isUnlimitedStock: Boolean,
        category: String,
        unit: String
    ): Result<String> {
        var docPath = inventarioCol.path
        return try {
            require(isValidStoreCode(storeCode)) { "Código de Tienda inválido" }
            val cleanName = name.trim().take(120)
            require(cleanName.isNotEmpty()) { "El nombre del producto es obligatorio" }
            require(priceCents > 0L) { "El precio debe ser mayor a cero" }
            val cleanCategory = category.trim().takeIf { it in PRODUCT_CATEGORIES } ?: PRODUCT_CATEGORIES.first()
            val cleanUnit = unit.trim().ifEmpty { "u" }.take(20)
            val safeStock = if (isUnlimitedStock) 0 else stockQuantity.coerceIn(0, 1_000_000)
            val safeCost = costCents.coerceIn(0L, 1_000_000_000L)

            if (id.isBlank()) {
                val docRef = inventarioCol.document()
                docPath = docRef.path
                val payload = mapOf(
                    "name" to cleanName,
                    "priceCents" to priceCents.coerceAtMost(1_000_000_000L),
                    "costCents" to safeCost,
                    "stockQuantity" to safeStock.toLong(),
                    "unlimitedStock" to isUnlimitedStock,
                    "category" to cleanCategory,
                    "unit" to cleanUnit,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                Result.success(docRef.id)
            } else {
                val docRef = inventarioCol.document(id)
                docPath = docRef.path
                docRef.update(
                    mapOf(
                        "name" to cleanName,
                        "priceCents" to priceCents.coerceAtMost(1_000_000_000L),
                        "costCents" to safeCost,
                        "stockQuantity" to safeStock.toLong(),
                        "unlimitedStock" to isUnlimitedStock,
                        "category" to cleanCategory,
                        "unit" to cleanUnit,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (id.isBlank()) OperationType.CREATE else OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun adjustProductStock(
        productId: String,
        currentStock: Int,
        isUnlimited: Boolean,
        delta: Int
    ): Result<Unit> {
        if (isUnlimited) return Result.success(Unit)
        val docRef = inventarioCol.document(productId)
        val docPath = docRef.path
        return try {
            require(productId.isNotBlank()) { "ID de producto inválido" }
            val nextStock = (currentStock + delta).coerceIn(0, 1_000_000).toLong()
            docRef.update(
                mapOf(
                    "stockQuantity" to nextStock,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun archiveProduct(productId: String): Result<Unit> {
        val docRef = inventarioCol.document(productId)
        val docPath = docRef.path
        return try {
            require(productId.isNotBlank()) { "ID de producto inválido" }
            docRef.update(
                mapOf(
                    "active" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveCustomer(
        id: String = "",
        name: String,
        phone: String,
        notes: String
    ): Result<String> {
        var docPath = clientesCol.path
        return try {
            require(isValidStoreCode(storeCode)) { "Código de Tienda inválido" }
            val cleanName = name.trim().take(120)
            require(cleanName.isNotEmpty()) { "El nombre del cliente es obligatorio" }
            val cleanPhone = phone.trim().take(40)
            val cleanNotes = notes.trim().take(500)

            if (id.isBlank()) {
                val docRef = clientesCol.document()
                docPath = docRef.path
                val payload = mapOf(
                    "name" to cleanName,
                    "phone" to cleanPhone,
                    "notes" to cleanNotes,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                Result.success(docRef.id)
            } else {
                val docRef = clientesCol.document(id)
                docPath = docRef.path
                docRef.update(
                    mapOf(
                        "name" to cleanName,
                        "phone" to cleanPhone,
                        "notes" to cleanNotes,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (id.isBlank()) OperationType.CREATE else OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomer(customerId: String): Result<Unit> {
        val docPath = "${clientesCol.path}/$customerId"
        return try {
            require(customerId.isNotBlank()) { "ID de cliente inválido" }
            val movSnap = movimientosCol
                .whereEqualTo("customerId", customerId)
                .get()
                .await()
            val batch = db.batch()
            movSnap.documents.forEach { batch.delete(it.reference) }
            batch.delete(clientesCol.document(customerId))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordQuickMovement(
        customerId: String?,
        type: String,
        totalCents: Long,
        paymentMethod: String,
        note: String,
        photoPath: String?,
        rateUsed: Double,
        dueDateMillis: Long? = null,
        items: List<CartItemDraft> = emptyList(),
        currentProducts: List<ProductEntity> = emptyList()
    ): Result<String> {
        var docPath = movimientosCol.path
        return try {
            require(isValidStoreCode(storeCode)) { "Código de Tienda inválido" }
            require(totalCents > 0L) { "El monto debe ser mayor a cero" }
            val safeType = type.takeIf {
                it in setOf(MovementType.SALE, MovementType.CREDIT, MovementType.PAYMENT, MovementType.EXPENSE)
            } ?: MovementType.SALE
            val safeItems = items.take(50)
            val serializedItems = safeItems.map { item ->
                mapOf(
                    "productId" to item.productId,
                    "productName" to item.productName.trim().take(120),
                    "unitPriceCents" to item.unitPriceCents,
                    "quantity" to item.quantity.toLong(),
                    "subtotalCents" to item.subtotalCents
                ).filterValues { it != null }
            }

            val docRef = movimientosCol.document()
            docPath = docRef.path
            val payload = mapOf(
                "customerId" to customerId?.takeIf { it.isNotBlank() },
                "type" to safeType,
                "totalCents" to totalCents.coerceAtMost(1_000_000_000L),
                "paymentMethod" to paymentMethod.trim().take(60),
                "note" to note.trim().take(500),
                "photoPath" to photoPath?.takeIf { it.isNotBlank() }?.take(1000),
                "dueDateMillis" to dueDateMillis,
                "rateUsed" to rateUsed.coerceAtLeast(0.01),
                "activityAtMillis" to System.currentTimeMillis(),
                "items" to serializedItems,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            val batch = db.batch()
            batch.set(docRef, payload)

            if (safeType == MovementType.SALE || safeType == MovementType.CREDIT) {
                val productById = currentProducts.associateBy { it.id }
                for (item in safeItems) {
                    val pid = item.productId?.takeIf { it.isNotBlank() } ?: continue
                    val prod = productById[pid]
                    if (prod != null && !prod.isUnlimitedStock) {
                        val nextStock = (prod.stockQuantity - item.quantity).coerceAtLeast(0).toLong()
                        batch.update(
                            inventarioCol.document(pid),
                            mapOf(
                                "stockQuantity" to nextStock,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }

            batch.commit().await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMovement(movementId: String): Result<Unit> {
        val docRef = movimientosCol.document(movementId)
        val docPath = docRef.path
        return try {
            require(movementId.isNotBlank()) { "ID de movimiento inválido" }
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearAllData(): Result<Unit> {
        return try {
            for (col in listOf(movimientosCol, clientesCol, inventarioCol)) {
                val snap = col.get().await()
                if (!snap.isEmpty) {
                    val batch = db.batch()
                    snap.documents.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "${storeRef.path}/all_collections")
            Result.failure(e)
        }
    }

    suspend fun resetAndSeedDemoData(rate: Double): Result<Unit> {
        return try {
            clearAllData().getOrThrow()
            val configSnap = tiendaConfigDoc.get().await()
            if (!configSnap.exists()) {
                tiendaConfigDoc.set(
                    mapOf(
                        "storeCode" to storeCode,
                        "exchangeRate" to rate.coerceAtLeast(0.01),
                        "rateLabel" to "Tasa BCV",
                        "rateUpdatedAtMillis" to System.currentTimeMillis(),
                        "businessName" to DEFAULT_BUSINESS_NAME,
                        "seededDemo" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            seedDeterministicDemoData(rate)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "${storeRef.path}/seed_demo")
            Result.failure(e)
        }
    }

    private suspend fun seedDeterministicDemoData(rate: Double) {
        val now = System.currentTimeMillis()
        val dayMs = TimeUnit.DAYS.toMillis(1)
        val hourMs = TimeUnit.HOURS.toMillis(1)

        // Batch 1: Productos (Comidas con cantidad indefinida, Bebidas y Dulces) + Clientes
        val batch1 = db.batch()

        val productsData = listOf(
            "demo_prod_empanada" to mapOf(
                "name" to "Empanada de Carne / Pollo",
                "priceCents" to 150L,
                "costCents" to 80L,
                "stockQuantity" to 0L,
                "unlimitedStock" to true,
                "category" to "Comidas",
                "unit" to "u",
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "demo_prod_perro" to mapOf(
                "name" to "Perro Caliente Especial",
                "priceCents" to 250L,
                "costCents" to 130L,
                "stockQuantity" to 0L,
                "unlimitedStock" to true,
                "category" to "Comidas",
                "unit" to "u",
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "demo_prod_refresco" to mapOf(
                "name" to "Refresco 2L",
                "priceCents" to 220L,
                "costCents" to 155L,
                "stockQuantity" to 14L,
                "unlimitedStock" to false,
                "category" to "Bebidas",
                "unit" to "u",
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "demo_prod_jugo" to mapOf(
                "name" to "Jugo Enlatado 350ml",
                "priceCents" to 120L,
                "costCents" to 80L,
                "stockQuantity" to 4L,
                "unlimitedStock" to false,
                "category" to "Bebidas",
                "unit" to "u",
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "demo_prod_dulce" to mapOf(
                "name" to "Chocolate Savoy / Galleta",
                "priceCents" to 110L,
                "costCents" to 75L,
                "stockQuantity" to 3L,
                "unlimitedStock" to false,
                "category" to "Dulces",
                "unit" to "paq",
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        for ((docId, payload) in productsData) {
            batch1.set(inventarioCol.document(docId), payload)
        }

        val customersData = listOf(
            "demo_cust_maria" to mapOf(
                "name" to "María Fernanda Rojas",
                "phone" to "0414-3829104",
                "notes" to "Vecina del frente · Al día",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "demo_cust_carlos" to mapOf(
                "name" to "Carlos Mendoza",
                "phone" to "0412-5519082",
                "notes" to "Taller mecánico esquina",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "demo_cust_ana" to mapOf(
                "name" to "Ana Sofía Bolívar",
                "phone" to "0424-7184320",
                "notes" to "Casa portón blanco",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        for ((docId, payload) in customersData) {
            batch1.set(clientesCol.document(docId), payload)
        }
        batch1.commit().await()

        // Batch 2: Movimientos históricos y 4 movimientos de hoy (clientes ya existen en Batch 1)
        val batch2 = db.batch()
        val todayBase = maxOf(startOfTodayMillis() + hourMs, now - 3 * hourMs)

        val movementsData = listOf(
            "demo_mov_hist_1" to mapOf(
                "customerId" to "demo_cust_maria",
                "type" to MovementType.CREDIT,
                "totalCents" to 1000L,
                "paymentMethod" to "",
                "note" to "Almuerzo y bebidas",
                "rateUsed" to rate,
                "activityAtMillis" to (now - 8 * dayMs),
                "items" to emptyList<Map<String, Any>>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "demo_mov_hist_2" to mapOf(
                "customerId" to "demo_cust_maria",
                "type" to MovementType.PAYMENT,
                "totalCents" to 1000L,
                "paymentMethod" to "Pago Móvil",
                "note" to "Canceló saldo completo",
                "rateUsed" to rate,
                "activityAtMillis" to (now - 2 * dayMs),
                "items" to emptyList<Map<String, Any>>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "demo_mov_hist_3" to mapOf(
                "customerId" to "demo_cust_carlos",
                "type" to MovementType.CREDIT,
                "totalCents" to 1450L,
                "paymentMethod" to "",
                "note" to "Desayunos y refrescos",
                "dueDateMillis" to (now + 5 * dayMs),
                "rateUsed" to rate,
                "activityAtMillis" to (now - 5 * dayMs),
                "items" to emptyList<Map<String, Any>>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "demo_mov_hist_4" to mapOf(
                "customerId" to "demo_cust_ana",
                "type" to MovementType.CREDIT,
                "totalCents" to 2650L,
                "paymentMethod" to "",
                "note" to "Combo comidas y dulces",
                "dueDateMillis" to (now - 3 * dayMs),
                "rateUsed" to rate,
                "activityAtMillis" to (now - 18 * dayMs),
                "items" to emptyList<Map<String, Any>>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "demo_mov_today_1" to mapOf(
                "type" to MovementType.SALE,
                "totalCents" to 630L,
                "paymentMethod" to "Pago Móvil",
                "note" to "Ref. 4821",
                "rateUsed" to rate,
                "activityAtMillis" to todayBase,
                "items" to listOf(
                    mapOf("productId" to "demo_prod_empanada", "productName" to "Empanada de Carne / Pollo", "unitPriceCents" to 150L, "quantity" to 2L, "subtotalCents" to 300L),
                    mapOf("productId" to "demo_prod_refresco", "productName" to "Refresco 2L", "unitPriceCents" to 220L, "quantity" to 1L, "subtotalCents" to 220L),
                    mapOf("productId" to "demo_prod_dulce", "productName" to "Chocolate Savoy / Galleta", "unitPriceCents" to 110L, "quantity" to 1L, "subtotalCents" to 110L)
                ),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "demo_mov_today_2" to mapOf(
                "type" to MovementType.SALE,
                "totalCents" to 620L,
                "paymentMethod" to "Efectivo $",
                "note" to "Mostrador",
                "rateUsed" to rate,
                "activityAtMillis" to minOf(now - 90 * 60_000L, todayBase + 35 * 60_000L).coerceAtLeast(startOfTodayMillis()),
                "items" to listOf(
                    mapOf("productId" to "demo_prod_perro", "productName" to "Perro Caliente Especial", "unitPriceCents" to 250L, "quantity" to 2L, "subtotalCents" to 500L),
                    mapOf("productId" to "demo_prod_jugo", "productName" to "Jugo Enlatado 350ml", "unitPriceCents" to 120L, "quantity" to 1L, "subtotalCents" to 120L)
                ),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "demo_mov_today_3" to mapOf(
                "customerId" to "demo_cust_carlos",
                "type" to MovementType.CREDIT,
                "totalCents" to 520L,
                "paymentMethod" to "",
                "note" to "Almuerzo taller",
                "dueDateMillis" to (now + 7 * dayMs),
                "rateUsed" to rate,
                "activityAtMillis" to minOf(now - 45 * 60_000L, todayBase + 70 * 60_000L).coerceAtLeast(startOfTodayMillis()),
                "items" to listOf(
                    mapOf("productId" to "demo_prod_empanada", "productName" to "Empanada de Carne / Pollo", "unitPriceCents" to 150L, "quantity" to 2L, "subtotalCents" to 300L),
                    mapOf("productId" to "demo_prod_refresco", "productName" to "Refresco 2L", "unitPriceCents" to 220L, "quantity" to 1L, "subtotalCents" to 220L)
                ),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "demo_mov_today_4" to mapOf(
                "type" to MovementType.EXPENSE,
                "totalCents" to 550L,
                "paymentMethod" to "Efectivo Bs",
                "note" to "Compra de servilletas, salsa y hielo",
                "rateUsed" to rate,
                "activityAtMillis" to minOf(now - 15 * 60_000L, todayBase + 100 * 60_000L).coerceAtLeast(startOfTodayMillis()),
                "items" to emptyList<Map<String, Any>>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        for ((docId, payload) in movementsData) {
            batch2.set(movimientosCol.document(docId), payload)
        }
        batch2.commit().await()
    }

    private fun normalizePaymentMethod(method: String): String = when (method) {
        "Efectivo" -> "Efectivo Bs"
        "Divisas" -> "Efectivo $"
        else -> method
    }

    private fun computeCustomerSummary(
        customer: CustomerEntity,
        movements: List<LedgerMovementEntity>,
        nowMillis: Long
    ): CustomerSummary {
        val sortedAsc = movements.sortedWith(compareBy<LedgerMovementEntity> { it.createdAt }.thenBy { it.id })
        var totalCredit = 0L
        var totalPayment = 0L
        var runningBalance = 0L
        var activeDebtStart: Long? = null
        var lastMove: Long? = null

        for (m in sortedAsc) {
            lastMove = maxOf(lastMove ?: 0L, m.createdAt)
            when (m.type) {
                MovementType.CREDIT -> {
                    if (runningBalance <= 0L) {
                        activeDebtStart = m.createdAt
                    }
                    totalCredit += m.totalCents
                    runningBalance += m.totalCents
                }
                MovementType.PAYMENT -> {
                    totalPayment += m.totalCents
                    runningBalance -= m.totalCents
                    if (runningBalance <= 0L) {
                        activeDebtStart = null
                    }
                }
            }
        }

        val balance = totalCredit - totalPayment
        val debtAgeDays = if (balance > 0L && activeDebtStart != null) {
            TimeUnit.MILLISECONDS.toDays((nowMillis - activeDebtStart).coerceAtLeast(0L)).toInt()
        } else {
            0
        }

        val nearestDue = if (balance > 0L) {
            sortedAsc
                .filter { it.type == MovementType.CREDIT && it.dueDateMillis != null && (activeDebtStart == null || it.createdAt >= activeDebtStart) }
                .mapNotNull { it.dueDateMillis }
                .minOrNull()
        } else null

        val semaphore = when {
            balance <= 0L -> DebtSemaphore.GREEN
            debtAgeDays >= 15 -> DebtSemaphore.RED
            else -> DebtSemaphore.YELLOW
        }

        return CustomerSummary(
            id = customer.id,
            name = customer.name,
            phone = customer.phone,
            notes = customer.notes,
            createdAt = customer.createdAt,
            totalCreditCents = totalCredit,
            totalPaymentCents = totalPayment,
            lastMovementAt = lastMove,
            debtSinceAt = activeDebtStart,
            nearestDueDateMillis = nearestDue,
            debtAgeDays = debtAgeDays,
            semaphore = semaphore
        )
    }

    private fun startOfTodayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
