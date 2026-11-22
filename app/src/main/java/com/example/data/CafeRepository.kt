package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

data class OrderSyncSnapshot(
    val orders: List<CafeOrder>,
    val hasPendingWrites: Boolean,
    val isFromCache: Boolean
)

class CafeRepository(
    private val db: FirebaseFirestore,
    private val localCache: LocalCafeCache,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    constructor(context: Context) : this(
        db = createConfiguredFirestore(context.applicationContext),
        localCache = LocalCafeCache(context.applicationContext)
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeCachedConfig(userId: String): Flow<CafeConfig?> =
        localCache.observeCachedConfig(userId)

    fun observeCachedMenu(userId: String): Flow<List<MenuItem>> =
        localCache.observeCachedMenu(userId)

    fun observeTicketerKeepAwake(): Flow<Boolean> =
        localCache.observeTicketerKeepAwake()

    suspend fun setTicketerKeepAwake(enabled: Boolean) =
        localCache.setTicketerKeepAwake(enabled)

    fun observeTicketerSoundEnabled(): Flow<Boolean> =
        localCache.observeTicketerSoundEnabled()

    suspend fun setTicketerSoundEnabled(enabled: Boolean) =
        localCache.setTicketerSoundEnabled(enabled)

    fun observeConfig(userId: String): Flow<CafeConfig?> = flow {
        val uid = requireUserId()
        require(uid == userId) { "Signed-in user mismatch" }
        val docRef = db.collection("users").document(userId).collection("config").document("settings")
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    val cfg = snapshot.toObject(
                        CafeConfig::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    if (cfg != null) {
                        bgScope.launch { localCache.saveConfig(userId, cfg) }
                    }
                    cfg
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeMenuItems(userId: String): Flow<List<MenuItem>> = flow {
        val uid = requireUserId()
        require(uid == userId) { "Signed-in user mismatch" }
        val colRef = db.collection("users").document(userId).collection("menuItems")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    val list = snapshot.toObjects(
                        MenuItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.code }
                    if (list.isNotEmpty()) {
                        bgScope.launch { localCache.saveMenu(userId, list) }
                    }
                    list
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeOrdersForDay(userId: String, dayKey: String): Flow<OrderSyncSnapshot> = flow {
        val uid = requireUserId()
        require(uid == userId) { "Signed-in user mismatch" }
        val colRef = db.collection("users").document(userId).collection("orders")
        emitAll(
            colRef.whereEqualTo("dayKey", dayKey)
                .snapshots()
                .map { snapshot ->
                    val orders = snapshot.toObjects(
                        CafeOrder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    OrderSyncSnapshot(
                        orders = orders,
                        hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                        isFromCache = snapshot.metadata.isFromCache
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeUnpaidOrders(userId: String): Flow<List<CafeOrder>> = flow {
        val uid = requireUserId()
        require(uid == userId) { "Signed-in user mismatch" }
        val colRef = db.collection("users").document(userId).collection("orders")
        emitAll(
            colRef.whereEqualTo("unpaid", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CafeOrder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureInitialWorkspace(userId: String, todayKey: String, userEmail: String? = null): Result<Unit> {
        val configPath = "users/$userId/config/settings"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val configRef = db.collection("users").document(userId).collection("config").document("settings")
            val existingConfig = configRef.get().await()
            val menuCol = db.collection("users").document(userId).collection("menuItems")
            val existingMenu = menuCol.limit(1).get().await()

            val normalizedEmail = (userEmail ?: auth.currentUser?.email ?: "")
                .trim()
                .lowercase()
                .take(120)

            if (!existingConfig.exists() || existingMenu.isEmpty) {
                val batch = db.batch()
                if (!existingConfig.exists()) {
                    val defaultConfig = mapOf(
                        "userId" to uid,
                        "cashierPin" to "1111",
                        "ticketerPin" to "2222",
                        "pinsCustomized" to false,
                        "alias78To79" to true,
                        "staff" to listOf("ሰራተኛ 1", "ሰራተኛ 2", "ሰራተኛ 3"),
                        "nextTicketSeq" to 1L,
                        "lastTicketDay" to todayKey,
                        "dayEpoch" to 1L,
                        "linkedEmail" to normalizedEmail,
                        "appPasswordHash" to "",
                        "appPasswordSalt" to "",
                        "appPasswordSet" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    batch.set(configRef, defaultConfig)
                }
                if (existingMenu.isEmpty) {
                    val starterMenu = listOf(
                        Triple(1, "ማኪያቶ", 50.0),
                        Triple(2, "ቡና", 40.0),
                        Triple(3, "ሻይ", 25.0),
                        Triple(4, "ስፕሪስ", 55.0),
                        Triple(5, "ወተት", 45.0),
                        Triple(6, "ውሃ", 30.0),
                        Triple(79, "ልዩ ማኪያቶ", 65.0)
                    )
                    starterMenu.forEach { (code, name, price) ->
                        val docRef = menuCol.document(code.toString())
                        batch.set(
                            docRef,
                            mapOf(
                                "code" to code,
                                "name" to name,
                                "price" to price,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
                withTimeoutOrNull(3000L) {
                    batch.commit().await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, configPath)
            Result.failure(e)
        }
    }

    suspend fun saveMenuItem(
        userId: String,
        code: Int,
        name: String,
        price: Double,
        isExisting: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/menuItems/$code"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            require(code in 1..1000) { "Menu number must be between 1 and 1000" }
            val cleanName = name.trim()
            require(cleanName.length in 1..60) { "Item name must be 1..60 characters" }
            require(price in 0.0..1000000.0) { "Price must be between 0 and 1,000,000" }

            val docRef = db.collection("users").document(userId)
                .collection("menuItems").document(code.toString())

            val task = if (isExisting) {
                docRef.update(
                    mapOf(
                        "name" to cleanName,
                        "price" to price,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                docRef.set(
                    mapOf(
                        "code" to code,
                        "name" to cleanName,
                        "price" to price,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            task.addOnFailureListener { err ->
                handleFirestoreError(err, if (isExisting) OperationType.UPDATE else OperationType.CREATE, docPath)
            }
            withTimeoutOrNull(900L) { task.await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isExisting && (e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                return saveMenuItem(userId, code, name, price, isExisting = false)
            }
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMenuItem(userId: String, code: Int): Result<Unit> {
        val docPath = "users/$userId/menuItems/$code"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            require(code in 1..1000) { "Menu number must be between 1 and 1000" }
            val docRef = db.collection("users").document(userId)
                .collection("menuItems").document(code.toString())
            val task = docRef.delete()
            task.addOnFailureListener { err ->
                handleFirestoreError(err, OperationType.DELETE, docPath)
            }
            withTimeoutOrNull(900L) { task.await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateSettings(
        userId: String,
        cashierPin: String,
        ticketerPin: String,
        pinsCustomized: Boolean,
        alias78To79: Boolean,
        staff: List<String>
    ): Result<Unit> {
        val docPath = "users/$userId/config/settings"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            require(cashierPin.matches(Regex("^[0-9]{4,8}$"))) { "Cashier PIN must be 4-8 digits" }
            require(ticketerPin.matches(Regex("^[0-9]{4,8}$"))) { "Ticketer PIN must be 4-8 digits" }
            val cleanStaff = staff.map { it.trim() }.filter { it.isNotEmpty() }.take(50)

            val docRef = db.collection("users").document(userId)
                .collection("config").document("settings")
            val payload = mapOf(
                "cashierPin" to cashierPin,
                "ticketerPin" to ticketerPin,
                "pinsCustomized" to pinsCustomized,
                "alias78To79" to alias78To79,
                "staff" to cleanStaff,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val task = docRef.update(payload)
            task.addOnFailureListener { err ->
                handleFirestoreError(err, OperationType.UPDATE, docPath)
            }
            withTimeoutOrNull(900L) { task.await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveAppPassword(
        userId: String,
        linkedEmail: String,
        passwordHash: String,
        passwordSalt: String,
        appPasswordSet: Boolean = true
    ): Result<Unit> {
        val docPath = "users/$userId/config/settings"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val cleanEmail = linkedEmail.trim().lowercase().take(120)
            require(cleanEmail.isNotEmpty()) { "Linked Gmail address required" }
            require(passwordHash.length <= 128 && passwordSalt.length <= 64) { "Invalid password hash payload" }

            localCache.saveAppPasswordCache(
                uid = userId,
                linkedEmail = cleanEmail,
                hash = passwordHash,
                salt = passwordSalt,
                appPasswordSet = appPasswordSet
            )

            val docRef = db.collection("users").document(userId)
                .collection("config").document("settings")
            val payload = mapOf(
                "linkedEmail" to cleanEmail,
                "appPasswordHash" to passwordHash,
                "appPasswordSalt" to passwordSalt,
                "appPasswordSet" to appPasswordSet,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val task = docRef.update(payload)
            task.addOnFailureListener { err ->
                handleFirestoreError(err, OperationType.UPDATE, docPath)
            }
            withTimeoutOrNull(900L) { task.await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun sendOrder(
        userId: String,
        staffName: String,
        items: List<DraftTicketLine>,
        unpaid: Boolean,
        urgent: Boolean = false,
        note: String? = null,
        todayKey: String,
        currentConfig: CafeConfig?
    ): Result<Int> {
        val ordersPath = "users/$userId/orders"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val cleanStaff = staffName.trim()
            require(cleanStaff.length in 1..50) { "Staff name required" }
            require(items.isNotEmpty() && items.size <= 50) { "Ticket must contain 1..50 items" }

            val activeEpoch = (currentConfig?.dayEpoch ?: 1L).coerceIn(1L, 1_000_000L)
            val issuedSeq = if (currentConfig != null && currentConfig.lastTicketDay == todayKey) {
                currentConfig.nextTicketSeq.toInt().coerceIn(1, 999999)
            } else {
                1
            }
            val nextSeqAfter = (issuedSeq + 1).toLong().coerceIn(1L, 1000000L)
            val totalAmount = items.sumOf { it.lineTotal }
            val cleanNote = note?.trim()?.take(15)?.takeIf { it.isNotEmpty() }

            val orderRef = db.collection("users").document(userId).collection("orders").document()
            val configRef = db.collection("users").document(userId).collection("config").document("settings")

            val compactItems = items.map { line ->
                mapOf(
                    "c" to line.code,
                    "n" to line.name,
                    "q" to line.qty,
                    "p" to line.unitPrice
                )
            }
            val orderPayload = mutableMapOf<String, Any>(
                "ticketNumber" to issuedSeq,
                "dayKey" to todayKey,
                "dayEpoch" to activeEpoch,
                "staffName" to cleanStaff,
                "status" to OrderStatus.PENDING.name,
                "items" to compactItems,
                "totalAmount" to totalAmount,
                "unpaid" to unpaid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).apply {
                if (urgent) put("urgent", true)
                if (cleanNote != null) put("note", cleanNote)
            }

            val batch = db.batch()
            batch.set(orderRef, orderPayload)
            if (currentConfig != null) {
                batch.update(
                    configRef,
                    mapOf(
                        "nextTicketSeq" to nextSeqAfter,
                        "lastTicketDay" to todayKey,
                        "dayEpoch" to activeEpoch,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val task = batch.commit()
            task.addOnFailureListener { err ->
                handleFirestoreError(err, OperationType.CREATE, orderRef.path)
            }
            withTimeoutOrNull(850L) { task.await() }
            Result.success(issuedSeq)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ordersPath)
            Result.failure(e)
        }
    }

    suspend fun updatePendingOrder(
        userId: String,
        orderId: String,
        items: List<OrderLineItem>,
        urgent: Boolean,
        note: String?
    ): Result<Unit> {
        val docPath = "users/$userId/orders/$orderId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            require(orderId.isNotBlank()) { "Order ID required" }
            require(items.isNotEmpty() && items.size <= 50) { "Ticket must contain 1..50 items" }

            val compactItems = items.map { line ->
                require(line.c in 1..1000) { "Invalid item code" }
                require(line.q in 1..999) { "Invalid quantity" }
                mapOf(
                    "c" to line.c,
                    "n" to line.n.trim().take(60),
                    "q" to line.q,
                    "p" to line.p.coerceIn(0.0, 1_000_000.0)
                )
            }
            val totalAmount = items.sumOf { it.lineTotal() }
            val cleanNote = note?.trim()?.take(15)?.takeIf { it.isNotEmpty() }

            val docRef = db.collection("users").document(userId).collection("orders").document(orderId)
            val payload = mapOf<String, Any>(
                "items" to compactItems,
                "totalAmount" to totalAmount,
                "urgent" to urgent,
                "note" to (cleanNote ?: FieldValue.delete()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val task = docRef.update(payload)
            task.addOnFailureListener { err ->
                handleFirestoreError(err, OperationType.UPDATE, docPath)
            }
            withTimeoutOrNull(850L) { task.await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun startNewDay(
        userId: String,
        todayKey: String,
        currentConfig: CafeConfig
    ): Result<Long> {
        val docPath = "users/$userId/config/settings"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val nextEpoch = (currentConfig.dayEpoch + 1L).coerceIn(1L, 1_000_000L)
            val configRef = db.collection("users").document(userId).collection("config").document("settings")
            val task = configRef.update(
                mapOf(
                    "nextTicketSeq" to 1L,
                    "lastTicketDay" to todayKey,
                    "dayEpoch" to nextEpoch,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            task.addOnFailureListener { err ->
                handleFirestoreError(err, OperationType.UPDATE, docPath)
            }
            withTimeoutOrNull(900L) { task.await() }
            Result.success(nextEpoch)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun restoreBackup(
        userId: String,
        backup: CafeBackupData,
        existingMenuCodes: Set<Int>
    ): Result<Unit> {
        val configPath = "users/$userId/config/settings"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }

            val menuCol = db.collection("users").document(userId).collection("menuItems")
            val configRef = db.collection("users").document(userId).collection("config").document("settings")

            val remoteCodes = withTimeoutOrNull(2500L) {
                menuCol.get().await().documents.mapNotNull { it.id.toIntOrNull() }.toSet()
            } ?: existingMenuCodes
            val knownCodes = remoteCodes + existingMenuCodes

            val cleanStaff = backup.staff.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(50)
            val validItems = backup.menuItems
                .filter { it.code in 1..1000 && it.name.trim().isNotEmpty() && it.price in 0.0..1_000_000.0 }
                .distinctBy { it.code }

            if (cleanStaff.isNotEmpty() || validItems.isNotEmpty()) {
                val cfgTask = configRef.update(
                    buildMap<String, Any> {
                        put("alias78To79", backup.alias78To79)
                        if (cleanStaff.isNotEmpty()) put("staff", cleanStaff)
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                )
                cfgTask.addOnFailureListener { err ->
                    handleFirestoreError(err, OperationType.UPDATE, configPath)
                }
                withTimeoutOrNull(900L) { cfgTask.await() }
            }

            validItems.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { item ->
                    val cleanName = item.name.trim().take(60)
                    val docRef = menuCol.document(item.code.toString())
                    if (item.code in knownCodes) {
                        batch.update(
                            docRef,
                            mapOf(
                                "name" to cleanName,
                                "price" to item.price,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    } else {
                        batch.set(
                            docRef,
                            mapOf(
                                "code" to item.code,
                                "name" to cleanName,
                                "price" to item.price,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
                val task = batch.commit()
                task.addOnFailureListener { err ->
                    handleFirestoreError(err, OperationType.WRITE, "users/$userId/menuItems")
                }
                withTimeoutOrNull(2500L) { task.await() }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, configPath)
            Result.failure(e)
        }
    }

    suspend fun cancelPendingOrder(userId: String, orderId: String): Result<Unit> {
        val docPath = "users/$userId/orders/$orderId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            require(orderId.isNotBlank()) { "Order ID required" }
            val docRef = db.collection("users").document(userId).collection("orders").document(orderId)
            val task = docRef.delete()
            task.addOnFailureListener { err ->
                handleFirestoreError(err, OperationType.DELETE, docPath)
            }
            withTimeoutOrNull(850L) { task.await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markOrderPaid(userId: String, orderId: String): Result<Unit> {
        val docPath = "users/$userId/orders/$orderId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            require(orderId.isNotBlank()) { "Order ID required" }
            val docRef = db.collection("users").document(userId).collection("orders").document(orderId)
            val task = docRef.update(
                mapOf(
                    "unpaid" to false,
                    "paidAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            task.addOnFailureListener { err ->
                handleFirestoreError(err, OperationType.UPDATE, docPath)
            }
            withTimeoutOrNull(850L) { task.await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markOrderCompleted(userId: String, orderId: String): Result<Unit> {
        val docPath = "users/$userId/orders/$orderId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            require(orderId.isNotBlank()) { "Order ID required" }
            val docRef = db.collection("users").document(userId).collection("orders").document(orderId)
            val task = docRef.update(
                mapOf(
                    "status" to OrderStatus.COMPLETED.name,
                    "completedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            task.addOnFailureListener { err ->
                handleFirestoreError(err, OperationType.UPDATE, docPath)
            }
            withTimeoutOrNull(850L) { task.await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markOrderReturned(userId: String, orderId: String, note: String? = null): Result<Unit> {
        val docPath = "users/$userId/orders/$orderId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            require(orderId.isNotBlank()) { "Order ID required" }
            val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }?.take(15)
            val docRef = db.collection("users").document(userId).collection("orders").document(orderId)
            val payload = mapOf(
                "status" to OrderStatus.RETURNED.name,
                "completedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "note" to cleanNote
            ).filterValues { it != null } as Map<String, Any>
            val task = docRef.update(payload)
            task.addOnFailureListener { err ->
                handleFirestoreError(err, OperationType.UPDATE, docPath)
            }
            withTimeoutOrNull(850L) { task.await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    companion object {
        @Volatile
        private var configuredDb: FirebaseFirestore? = null

        fun createConfiguredFirestore(context: Context): FirebaseFirestore {
            return configuredDb ?: synchronized(this) {
                configuredDb ?: FirebaseFirestore.getInstance(
                    context.applicationContext.getString(R.string.firestore_database_id)
                ).apply {
                    try {
                        firestoreSettings = firestoreSettings {
                            setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                        }
                    } catch (_: IllegalStateException) {
                        // Settings already initialized on existing instance
                    }
                    configuredDb = this
                }
            }
        }
    }
}
