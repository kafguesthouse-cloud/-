package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppRole
import com.example.data.AppRoute
import com.example.data.CafeBackupData
import com.example.data.CafeConfig
import com.example.data.CafeOrder
import com.example.data.CafeRepository
import com.example.data.DraftTicketLine
import com.example.data.MenuItem
import com.example.data.NetworkMonitor
import com.example.data.OrderLineItem
import com.example.data.OrderStatus
import com.example.data.OrderSyncSnapshot
import com.example.data.StaffDailySummary
import com.example.data.UiState
import org.json.JSONArray
import org.json.JSONObject
import com.example.ui.auth.AppPasswordCrypto
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class KeypadState(
    val qtyBuffer: String = "",
    val hasMultiply: Boolean = false,
    val codeBuffer: String = "",
    val draftLines: List<DraftTicketLine> = emptyList(),
    val draftUnpaid: Boolean = false,
    val draftUrgent: Boolean = false,
    val draftNote: String = "",
    val showStaffPicker: Boolean = false,
    val isSending: Boolean = false
) {
    val draftTotal: Double get() = draftLines.sumOf { it.lineTotal }
    val draftItemCount: Int get() = draftLines.sumOf { it.qty }
}

data class ItemSalesSummary(
    val code: Int,
    val name: String,
    val qtySold: Int,
    val totalRevenue: Double
)

data class DailyReportSummary(
    val dayKey: String,
    val completedOrders: List<CafeOrder>,
    val returnedOrders: List<CafeOrder>,
    val pendingCount: Int,
    val completedTotalRevenue: Double,
    val returnedTotalAmount: Double,
    val itemSales: List<ItemSalesSummary>,
    val staffSummaries: List<StaffDailySummary> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class CafeViewModel(
    private val repository: CafeRepository,
    networkMonitor: NetworkMonitor,
    private val currentUserId: String
) : ViewModel() {

    private val _route = MutableStateFlow<AppRoute>(AppRoute.RoleSelect)
    val route: StateFlow<AppRoute> = _route.asStateFlow()

    private val _pinPromptRole = MutableStateFlow<AppRole?>(null)
    val pinPromptRole: StateFlow<AppRole?> = _pinPromptRole.asStateFlow()

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    private val _selectedDayKey = MutableStateFlow(todayDayKey())
    val selectedDayKey: StateFlow<String> = _selectedDayKey.asStateFlow()

    private val _keypadState = MutableStateFlow(KeypadState())
    val keypadState: StateFlow<KeypadState> = _keypadState.asStateFlow()

    private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastMessages: SharedFlow<String> = _toastMessages.asSharedFlow()

    private val _hasLoadedConfig = MutableStateFlow(false)
    val hasLoadedConfig: StateFlow<Boolean> = _hasLoadedConfig.asStateFlow()

    val isOnline: StateFlow<Boolean> = networkMonitor.observeOnlineStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    val ticketerKeepAwake: StateFlow<Boolean> = repository.observeTicketerKeepAwake()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    val ticketerSoundEnabled: StateFlow<Boolean> = repository.observeTicketerSoundEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    private val remoteConfigFlow = repository.observeConfig(currentUserId)
        .map<CafeConfig?, UiState<CafeConfig?>> {
            _hasLoadedConfig.value = true
            UiState.Success(it)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing config", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load settings"))
        }

    val configState: StateFlow<CafeConfig> = combine(
        remoteConfigFlow,
        repository.observeCachedConfig(currentUserId)
    ) { remote, cached ->
        when (remote) {
            is UiState.Success -> remote.data ?: cached ?: CafeConfig(userId = currentUserId, lastTicketDay = todayDayKey())
            else -> cached ?: CafeConfig(userId = currentUserId, lastTicketDay = todayDayKey())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = CafeConfig(userId = currentUserId, lastTicketDay = todayDayKey())
    )

    private val remoteMenuFlow = repository.observeMenuItems(currentUserId)
        .map<List<MenuItem>, UiState<List<MenuItem>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing menu", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load menu"))
        }

    val menuState: StateFlow<List<MenuItem>> = combine(
        remoteMenuFlow,
        repository.observeCachedMenu(currentUserId)
    ) { remote, cached ->
        when (remote) {
            is UiState.Success -> remote.data.ifEmpty { cached }
            else -> cached
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val ordersState: StateFlow<UiState<OrderSyncSnapshot>> = _selectedDayKey
        .flatMapLatest { dayKey ->
            repository.observeOrdersForDay(currentUserId, dayKey)
                .map<OrderSyncSnapshot, UiState<OrderSyncSnapshot>> { UiState.Success(it) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing orders", rootError)
                    emit(UiState.Error(rootError.message ?: "Failed to load orders"))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val todayOrdersState: StateFlow<UiState<OrderSyncSnapshot>> = repository.observeOrdersForDay(currentUserId, todayDayKey())
        .map<OrderSyncSnapshot, UiState<OrderSyncSnapshot>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing today's orders", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load today's orders"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val unpaidOrdersState: StateFlow<List<CafeOrder>> = repository.observeUnpaidOrders(currentUserId)
        .map { list ->
            list.filter { it.unpaid && it.orderStatus() != OrderStatus.RETURNED }
                .sortedWith(
                    compareBy<CafeOrder> { it.createdAt ?: Timestamp(Long.MAX_VALUE, 0) }
                        .thenBy { it.ticketNumber }
                )
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing unpaid orders", firestoreError ?: error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            repository.ensureInitialWorkspace(currentUserId, todayDayKey())
        }
    }

    // --- Role & PIN Navigation (PIN ONLY for Cashier; Ticketer opens directly) ---

    fun requestRoleAccess(role: AppRole) {
        when (role) {
            AppRole.TICKETER -> {
                _pinPromptRole.value = null
                _pinError.value = null
                _route.value = AppRoute.Ticketer
            }
            AppRole.CASHIER -> {
                _pinError.value = null
                _pinPromptRole.value = AppRole.CASHIER
            }
        }
    }

    fun dismissPinPrompt() {
        _pinPromptRole.value = null
        _pinError.value = null
    }

    fun submitRolePin(enteredPin: String) {
        val targetRole = _pinPromptRole.value ?: return
        val cfg = configState.value
        val expectedPin = when (targetRole) {
            AppRole.CASHIER -> cfg.cashierPin
            AppRole.TICKETER -> cfg.ticketerPin
        }
        if (enteredPin == expectedPin) {
            _pinPromptRole.value = null
            _pinError.value = null
            _route.value = when (targetRole) {
                AppRole.CASHIER -> AppRoute.Cashier
                AppRole.TICKETER -> AppRoute.Ticketer
            }
        } else {
            _pinError.value = "የተሳሳተ ፒን (Incorrect PIN)"
        }
    }

    fun navigateTo(target: AppRoute) {
        _route.value = target
    }

    fun setReportDayKey(dayKey: String) {
        _selectedDayKey.value = dayKey
    }

    // --- Menu Resolution (with explicit 78 -> 79 mapping rule) ---

    fun resolveEffectiveCode(enteredCode: Int): Int {
        return if (configState.value.alias78To79 && enteredCode == 78) 79 else enteredCode
    }

    // --- Cashier Keypad & Ticket Builder ---

    fun onDigitPressed(digit: String) {
        val current = _keypadState.value
        if (current.codeBuffer.isEmpty() && digit == "0") return
        val candidate = current.codeBuffer + digit
        val parsed = candidate.toIntOrNull() ?: return
        if (parsed > 1000) {
            _toastMessages.tryEmit("የምናሌ ቁጥር ከ1–1000 መሆን አለበት (1–1000 only)")
            return
        }
        _keypadState.value = current.copy(codeBuffer = candidate)
    }

    fun onMultiplyPressed() {
        val current = _keypadState.value
        if (current.hasMultiply) return
        val qty = current.codeBuffer.ifEmpty { "1" }.toIntOrNull()?.coerceIn(1, 999) ?: 1
        _keypadState.value = current.copy(
            qtyBuffer = qty.toString(),
            hasMultiply = true,
            codeBuffer = ""
        )
    }

    fun onBackspacePressed() {
        val current = _keypadState.value
        when {
            current.codeBuffer.isNotEmpty() -> {
                _keypadState.value = current.copy(codeBuffer = current.codeBuffer.dropLast(1))
            }
            current.hasMultiply -> {
                _keypadState.value = current.copy(
                    codeBuffer = current.qtyBuffer,
                    qtyBuffer = "",
                    hasMultiply = false
                )
            }
            current.draftLines.isNotEmpty() -> {
                val removed = current.draftLines.last()
                _keypadState.value = current.copy(draftLines = current.draftLines.dropLast(1))
                _toastMessages.tryEmit("${removed.name}×${removed.qty} ተሰርዟል")
            }
        }
    }

    fun onAddNextItem(): Boolean {
        val current = _keypadState.value
        val enteredCode = current.codeBuffer.toIntOrNull()
        if (enteredCode == null || enteredCode !in 1..1000) {
            _toastMessages.tryEmit("የምግብ/መጠጥ ቁጥር ያስገቡ (Enter item code 1–1000)")
            return false
        }
        val effectiveCode = resolveEffectiveCode(enteredCode)
        val item = menuState.value.firstOrNull { it.code == effectiveCode }
        if (item == null) {
            val aliasNote = if (enteredCode == 78 && configState.value.alias78To79) " (78→79)" else ""
            _toastMessages.tryEmit("ኮድ #$effectiveCode$aliasNote በሴቲንግ አልተመዘገበም")
            return false
        }
        val qty = if (current.hasMultiply) {
            (current.qtyBuffer.toIntOrNull() ?: 1).coerceIn(1, 999)
        } else {
            1
        }
        val updatedLines = current.draftLines.toMutableList()
        val existingIdx = updatedLines.indexOfFirst { it.code == item.code }
        if (existingIdx >= 0) {
            val prev = updatedLines[existingIdx]
            updatedLines[existingIdx] = prev.copy(qty = (prev.qty + qty).coerceAtMost(999))
        } else {
            updatedLines.add(
                DraftTicketLine(
                    code = item.code,
                    enteredCode = enteredCode,
                    name = item.name,
                    qty = qty,
                    unitPrice = item.price
                )
            )
        }
        _keypadState.value = current.copy(
            qtyBuffer = "",
            hasMultiply = false,
            codeBuffer = "",
            draftLines = updatedLines
        )
        return true
    }

    fun onRemoveDraftLine(code: Int) {
        val current = _keypadState.value
        val target = current.draftLines.firstOrNull { it.code == code }
        _keypadState.value = current.copy(
            draftLines = current.draftLines.filterNot { it.code == code }
        )
        if (target != null) {
            _toastMessages.tryEmit("${target.name}×${target.qty} ተሰርዟል")
        }
    }

    fun toggleDraftUnpaid() {
        val current = _keypadState.value
        val next = !current.draftUnpaid
        _keypadState.value = current.copy(draftUnpaid = next)
        _toastMessages.tryEmit(
            if (next) "ያልተከፈለ ተመርጧል (Order will be sent as Unpaid)"
            else "ያልተከፈለ ተነስቷል (Normal paid order)"
        )
    }

    fun toggleDraftUrgent() {
        val current = _keypadState.value
        val next = !current.draftUrgent
        _keypadState.value = current.copy(draftUrgent = next)
        _toastMessages.tryEmit(
            if (next) "ቅድሚያ ተመርጧል (Urgent priority)"
            else "ቅድሚያ ተነስቷል"
        )
    }

    fun updateDraftNote(raw: String) {
        val clean = raw.replace('\n', ' ').take(15)
        _keypadState.value = _keypadState.value.copy(draftNote = clean)
    }

    fun onClearTicket() {
        _keypadState.value = KeypadState()
        _toastMessages.tryEmit("ትኬቱ ጸድቷል (Ticket cleared)")
    }

    fun onSendClicked() {
        val current = _keypadState.value
        if (current.codeBuffer.isNotEmpty()) {
            val added = onAddNextItem()
            if (!added) return
        }
        if (_keypadState.value.draftLines.isEmpty()) {
            _toastMessages.tryEmit("መጀመሪያ ትዕዛዝ ያስገቡ (Add an item first)")
            return
        }
        _keypadState.value = _keypadState.value.copy(showStaffPicker = true)
    }

    fun dismissStaffPicker() {
        _keypadState.value = _keypadState.value.copy(showStaffPicker = false)
    }

    fun onConfirmSendOrder(staffName: String) {
        val state = _keypadState.value
        val lines = state.draftLines
        if (lines.isEmpty()) return
        val isUnpaidOrder = state.draftUnpaid
        val isUrgentOrder = state.draftUrgent
        val orderNote = state.draftNote.trim().take(15).ifEmpty { null }
        _keypadState.value = state.copy(showStaffPicker = false, isSending = true)
        viewModelScope.launch {
            val result = repository.sendOrder(
                userId = currentUserId,
                staffName = staffName,
                items = lines,
                unpaid = isUnpaidOrder,
                urgent = isUrgentOrder,
                note = orderNote,
                todayKey = todayDayKey(),
                currentConfig = configState.value
            )
            result.fold(
                onSuccess = {
                    _keypadState.value = KeypadState()
                    val unpaidSuffix = if (isUnpaidOrder) " • ያልተከፈለ" else ""
                    val urgentSuffix = if (isUrgentOrder) " • ቅድሚያ" else ""
                    val syncSuffix = if (isOnline.value) "" else " • በኦፍላይን"
                    _toastMessages.tryEmit("ትኬት ወደ ትኬተር ተልኳል ($staffName)$urgentSuffix$unpaidSuffix$syncSuffix")
                },
                onFailure = { err ->
                    _keypadState.value = _keypadState.value.copy(isSending = false)
                    _toastMessages.tryEmit("ስህተት: ${err.localizedMessage ?: "Failed to send"}")
                }
            )
        }
    }

    // --- Edit / Cancel Unserved Order / Mark Unpaid Order Paid ---

    fun updatePendingOrder(
        orderId: String,
        items: List<OrderLineItem>,
        urgent: Boolean,
        note: String?
    ) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            repository.updatePendingOrder(currentUserId, orderId, items, urgent, note).fold(
                onSuccess = {
                    _toastMessages.tryEmit("ትኬቱ ተስተካክሏል ✓ (Updated)")
                },
                onFailure = { err ->
                    _toastMessages.tryEmit("ማስተካከል አልተቻለም: ${err.localizedMessage ?: "Update failed"}")
                }
            )
        }
    }

    fun cancelPendingOrder(orderId: String) {
        viewModelScope.launch {
            repository.cancelPendingOrder(currentUserId, orderId).fold(
                onSuccess = {
                    _toastMessages.tryEmit("ትዕዛዙ ተሰርዟል (Cancelled)")
                },
                onFailure = { err ->
                    _toastMessages.tryEmit("መሰረዝ አልተቻለም: ${err.localizedMessage ?: "Failed"}")
                }
            )
        }
    }

    fun markOrderPaid(orderId: String) {
        viewModelScope.launch {
            repository.markOrderPaid(currentUserId, orderId).fold(
                onSuccess = {
                    _toastMessages.tryEmit("ተከፍሏል ✓ (Removed from unpaid)")
                },
                onFailure = { err ->
                    _toastMessages.tryEmit("ስህተት: ${err.localizedMessage ?: "Failed"}")
                }
            )
        }
    }

    // --- Ticketer Actions ---

    fun markOrderCompleted(orderId: String) {
        viewModelScope.launch {
            repository.markOrderCompleted(currentUserId, orderId).fold(
                onSuccess = {
                    _toastMessages.tryEmit("ወጥቷል ✓")
                },
                onFailure = { err ->
                    _toastMessages.tryEmit("ስህተት: ${err.localizedMessage ?: "Update failed"}")
                }
            )
        }
    }

    fun markOrderReturned(orderId: String, note: String? = null) {
        viewModelScope.launch {
            repository.markOrderReturned(currentUserId, orderId, note).fold(
                onSuccess = {
                    _toastMessages.tryEmit("ተመልሷል (Returned)")
                },
                onFailure = { err ->
                    _toastMessages.tryEmit("ስህተት: ${err.localizedMessage ?: "Return failed"}")
                }
            )
        }
    }

    fun setTicketerKeepAwake(enabled: Boolean) {
        viewModelScope.launch {
            repository.setTicketerKeepAwake(enabled)
        }
    }

    fun setTicketerSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setTicketerSoundEnabled(enabled)
        }
    }

    fun startNewDay() {
        viewModelScope.launch {
            repository.startNewDay(currentUserId, todayDayKey(), configState.value).fold(
                onSuccess = { newEpoch ->
                    _toastMessages.tryEmit("አዲስ ቀን ተጀምሯል (#1 reset • ዙር #$newEpoch)")
                },
                onFailure = { err ->
                    _toastMessages.tryEmit("ስህተት: ${err.localizedMessage ?: "Reset failed"}")
                }
            )
        }
    }

    fun restoreBackup(backup: CafeBackupData, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val existingCodes = menuState.value.map { it.code }.toSet()
            repository.restoreBackup(currentUserId, backup, existingCodes).fold(
                onSuccess = {
                    _toastMessages.tryEmit("${backup.menuItems.size} ምናሌ እና ${backup.staff.size} ሰራተኞች ተመልሰዋል ✓")
                    onComplete(true)
                },
                onFailure = { err ->
                    _toastMessages.tryEmit("Restore failed: ${err.localizedMessage ?: "Error"}")
                    onComplete(false)
                }
            )
        }
    }

    // --- Settings Actions ---

    fun saveMenuItem(code: Int, name: String, price: Double) {
        viewModelScope.launch {
            val existing = menuState.value.any { it.code == code }
            repository.saveMenuItem(currentUserId, code, name, price, existing).fold(
                onSuccess = {
                    _toastMessages.tryEmit("#$code $name ተቀምጧል")
                },
                onFailure = { err ->
                    _toastMessages.tryEmit("ስህተት: ${err.localizedMessage ?: "Save failed"}")
                }
            )
        }
    }

    fun deleteMenuItem(code: Int) {
        viewModelScope.launch {
            repository.deleteMenuItem(currentUserId, code).fold(
                onSuccess = {
                    _toastMessages.tryEmit("#$code ተሰርዟል")
                },
                onFailure = { err ->
                    _toastMessages.tryEmit("ስህተት: ${err.localizedMessage ?: "Delete failed"}")
                }
            )
        }
    }

    fun saveAppPassword(
        linkedEmail: String,
        newPassword: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val cleanEmail = linkedEmail.trim().lowercase()
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
            _toastMessages.tryEmit("ትክክለኛ Gmail ያስገቡ (Valid Gmail required)")
            onComplete(false)
            return
        }
        if (newPassword.length < 6) {
            _toastMessages.tryEmit("App Password ቢያንስ 6 ፊደል/ቁጥር መሆን አለበት (Min 6 chars)")
            onComplete(false)
            return
        }
        viewModelScope.launch {
            val salt = AppPasswordCrypto.generateSaltHex()
            val hash = AppPasswordCrypto.hashAppPassword(newPassword, salt)
            repository.saveAppPassword(
                userId = currentUserId,
                linkedEmail = cleanEmail,
                passwordHash = hash,
                passwordSalt = salt,
                appPasswordSet = true
            ).fold(
                onSuccess = {
                    _toastMessages.tryEmit("App Password ተቀምጧል ✓")
                    onComplete(true)
                },
                onFailure = { err ->
                    _toastMessages.tryEmit("ስህተት: ${err.localizedMessage ?: "Save failed"}")
                    onComplete(false)
                }
            )
        }
    }

    fun updateSettings(
        cashierPin: String,
        ticketerPin: String,
        alias78To79: Boolean,
        staff: List<String>
    ) {
        viewModelScope.launch {
            repository.updateSettings(
                userId = currentUserId,
                cashierPin = cashierPin,
                ticketerPin = ticketerPin,
                pinsCustomized = true,
                alias78To79 = alias78To79,
                staff = staff
            ).fold(
                onSuccess = {
                    _toastMessages.tryEmit("ቅንብሮች ተቀምጠዋል (Settings saved)")
                },
                onFailure = { err ->
                    _toastMessages.tryEmit("ስህተት: ${err.localizedMessage ?: "Save failed"}")
                }
            )
        }
    }

    companion object {
        private const val TAG = "CafeVM"

        fun todayDayKey(): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        }

        fun formatTimeShort(ts: Timestamp?): String {
            if (ts == null) return "--:--"
            return SimpleDateFormat("HH:mm", Locale.US).format(ts.toDate())
        }

        /**
         * Assigns #1, #2, #3... strictly by chronological order time (earliest ordered = #1).
         */
        fun computeChronologicalSequenceMap(orders: List<CafeOrder>): Map<String, Int> {
            val sorted = orders.sortedWith(
                compareBy<CafeOrder> { it.createdAt ?: Timestamp(Long.MAX_VALUE, 0) }
                    .thenBy { it.ticketNumber }
                    .thenBy { it.id }
            )
            return sorted.mapIndexed { index, order -> order.id to (index + 1) }.toMap()
        }

        fun buildReportSummary(dayKey: String, orders: List<CafeOrder>): DailyReportSummary {
            val completed = orders
                .filter { it.orderStatus() == OrderStatus.COMPLETED }
                .sortedByDescending { it.completedAt ?: it.createdAt ?: Timestamp(0, 0) }
            val returned = orders
                .filter { it.orderStatus() == OrderStatus.RETURNED }
                .sortedByDescending { it.completedAt ?: it.createdAt ?: Timestamp(0, 0) }
            val pendingCount = orders.count { it.orderStatus() == OrderStatus.PENDING }

            val itemMap = linkedMapOf<Int, ItemSalesSummary>()
            completed.forEach { order ->
                order.items.forEach { line ->
                    val prev = itemMap[line.c]
                    itemMap[line.c] = if (prev == null) {
                        ItemSalesSummary(
                            code = line.c,
                            name = line.n,
                            qtySold = line.q,
                            totalRevenue = line.lineTotal()
                        )
                    } else {
                        prev.copy(
                            qtySold = prev.qtySold + line.q,
                            totalRevenue = prev.totalRevenue + line.lineTotal()
                        )
                    }
                }
            }

            val staffMap = linkedMapOf<String, StaffDailySummary>()
            orders.forEach { order ->
                val name = order.staffName.ifBlank { "ያልታወቀ" }
                val prev = staffMap[name] ?: StaffDailySummary(
                    staffName = name,
                    completedOrderCount = 0,
                    completedSalesTotal = 0.0,
                    unpaidCount = 0,
                    unpaidTotal = 0.0,
                    returnedCount = 0
                )
                val status = order.orderStatus()
                val updated = when (status) {
                    OrderStatus.COMPLETED -> prev.copy(
                        completedOrderCount = prev.completedOrderCount + 1,
                        completedSalesTotal = prev.completedSalesTotal + order.totalAmount,
                        unpaidCount = if (order.unpaid) prev.unpaidCount + 1 else prev.unpaidCount,
                        unpaidTotal = if (order.unpaid) prev.unpaidTotal + order.totalAmount else prev.unpaidTotal
                    )
                    OrderStatus.PENDING -> prev.copy(
                        unpaidCount = if (order.unpaid) prev.unpaidCount + 1 else prev.unpaidCount,
                        unpaidTotal = if (order.unpaid) prev.unpaidTotal + order.totalAmount else prev.unpaidTotal
                    )
                    OrderStatus.RETURNED -> prev.copy(
                        returnedCount = prev.returnedCount + 1
                    )
                }
                staffMap[name] = updated
            }

            return DailyReportSummary(
                dayKey = dayKey,
                completedOrders = completed,
                returnedOrders = returned,
                pendingCount = pendingCount,
                completedTotalRevenue = completed.sumOf { it.totalAmount },
                returnedTotalAmount = returned.sumOf { it.totalAmount },
                itemSales = itemMap.values.sortedByDescending { it.qtySold },
                staffSummaries = staffMap.values.sortedWith(
                    compareByDescending<StaffDailySummary> { it.completedSalesTotal }
                        .thenByDescending { it.unpaidTotal }
                        .thenBy { it.staffName }
                )
            )
        }

        fun buildBackupJson(config: CafeConfig, menuItems: List<MenuItem>): String {
            val root = JSONObject()
            root.put("version", 1)
            root.put("app", "BunaPOS")
            root.put("alias78To79", config.alias78To79)
            root.put("staff", JSONArray(config.staff))
            val menuArr = JSONArray()
            menuItems.sortedBy { it.code }.forEach { item ->
                menuArr.put(
                    JSONObject().apply {
                        put("c", item.code)
                        put("n", item.name)
                        put("p", item.price)
                    }
                )
            }
            root.put("menuItems", menuArr)
            return root.toString(2)
        }

        fun parseBackupJson(raw: String): Result<CafeBackupData> {
            return try {
                val root = JSONObject(raw.trim())
                val alias = root.optBoolean("alias78To79", true)
                val staffArr = root.optJSONArray("staff") ?: JSONArray()
                val staffList = buildList {
                    for (i in 0 until staffArr.length()) {
                        val s = staffArr.optString(i).trim().take(50)
                        if (s.isNotEmpty() && !contains(s)) add(s)
                    }
                }.take(50)
                val menuArr = root.optJSONArray("menuItems") ?: JSONArray()
                val menuList = buildList {
                    for (i in 0 until menuArr.length()) {
                        val obj = menuArr.optJSONObject(i) ?: continue
                        val code = if (obj.has("c")) obj.optInt("c", 0) else obj.optInt("code", 0)
                        val name = (if (obj.has("n")) obj.optString("n") else obj.optString("name")).trim().take(60)
                        val price = if (obj.has("p")) obj.optDouble("p", -1.0) else obj.optDouble("price", -1.0)
                        if (code in 1..1000 && name.isNotEmpty() && price in 0.0..1_000_000.0) {
                            add(MenuItem(id = code.toString(), code = code, name = name, price = price))
                        }
                    }
                }.distinctBy { it.code }.sortedBy { it.code }
                if (staffList.isEmpty() && menuList.isEmpty()) {
                    Result.failure(IllegalArgumentException("ባክአፕ ፋይሉ ባዶ ወይም የተሳሳተ ነው (Empty backup)"))
                } else {
                    Result.success(
                        CafeBackupData(
                            alias78To79 = alias,
                            staff = staffList,
                            menuItems = menuList
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(IllegalArgumentException("ትክክለኛ የBunaPOS ባክአፕ JSON አይደለም: ${e.localizedMessage}"))
            }
        }
    }
}
