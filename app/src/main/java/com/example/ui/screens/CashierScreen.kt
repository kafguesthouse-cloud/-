package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.CafeConfig
import com.example.data.CafeOrder
import com.example.data.DraftTicketLine
import com.example.data.MenuItem
import com.example.data.OrderLineItem
import com.example.data.OrderStatus
import com.example.ui.CafeViewModel
import com.example.ui.KeypadState
import com.example.ui.components.SyncStatusPill
import com.example.ui.components.formatBirr
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalPosColors
import com.google.firebase.Timestamp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CashierScreen(
    keypadState: KeypadState,
    config: CafeConfig,
    menuItems: List<MenuItem>,
    todayOrders: List<CafeOrder>,
    unpaidOrders: List<CafeOrder>,
    isOnline: Boolean,
    hasPendingWrites: Boolean,
    onDigit: (String) -> Unit,
    onMultiply: () -> Unit,
    onBackspace: () -> Unit,
    onAddNext: () -> Unit,
    onClearTicket: () -> Unit,
    onRemoveLine: (Int) -> Unit,
    onToggleDraftUnpaid: () -> Unit,
    onToggleDraftUrgent: () -> Unit,
    onUpdateDraftNote: (String) -> Unit,
    onSendClicked: () -> Unit,
    onDismissStaffPicker: () -> Unit,
    onSelectStaffAndSend: (String) -> Unit,
    onUpdatePendingOrder: (orderId: String, items: List<OrderLineItem>, urgent: Boolean, note: String?) -> Unit,
    onCancelPendingOrder: (String) -> Unit,
    onMarkOrderPaid: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReport: () -> Unit,
    onExitRole: () -> Unit
) {
    BackHandler { onExitRole() }

    val posColors = LocalPosColors.current
    var showReturnedSheet by remember { mutableStateOf(false) }
    var showPendingSheet by remember { mutableStateOf(false) }
    var showUnpaidSheet by remember { mutableStateOf(false) }
    var editingPendingOrderId by remember { mutableStateOf<String?>(null) }

    val seqMap = remember(todayOrders) {
        CafeViewModel.computeChronologicalSequenceMap(todayOrders)
    }

    val todayPendingOrders = remember(todayOrders) {
        todayOrders.filter { it.orderStatus() == OrderStatus.PENDING }
            .sortedWith(
                compareBy<CafeOrder> { it.createdAt ?: Timestamp(Long.MAX_VALUE, 0) }
                    .thenBy { it.ticketNumber }
                    .thenBy { it.id }
            )
    }

    val todayReturnedOrders = remember(todayOrders) {
        todayOrders.filter { it.orderStatus() == OrderStatus.RETURNED }
            .sortedByDescending { it.completedAt ?: it.createdAt ?: Timestamp(0, 0) }
    }

    val unpaidTotal = remember(unpaidOrders) { unpaidOrders.sumOf { it.totalAmount } }

    val enteredCode = keypadState.codeBuffer.toIntOrNull()
    val effectiveCode = enteredCode?.let {
        if (config.alias78To79 && it == 78) 79 else it
    }
    val matchedMenuItem = effectiveCode?.let { code -> menuItems.firstOrNull { it.code == code } }
    val pendingQty = if (keypadState.hasMultiply) (keypadState.qtyBuffer.toIntOrNull() ?: 1) else 1

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    // Main Top Bar: Lock, Clear ("ክሊር"), Settings ("ሴቲንግ"), Report ("ሪፖርት"), Sync
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = onExitRole,
                            modifier = Modifier.testTag("cashier_lock_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Switch Role",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        TopActionPill(
                            label = "ክሊር",
                            icon = Icons.Filled.DeleteSweep,
                            containerColor = posColors.returnRedContainer,
                            contentColor = posColors.returnRedOnContainer,
                            onClick = onClearTicket,
                            modifier = Modifier.testTag("cashier_clear_button")
                        )

                        TopActionPill(
                            label = "ሴቲንግ",
                            icon = Icons.Filled.Settings,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("cashier_settings_button")
                        )

                        TopActionPill(
                            label = "ሪፖርት",
                            icon = Icons.Filled.Assessment,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = onOpenReport,
                            modifier = Modifier.testTag("cashier_report_button")
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        SyncStatusPill(isOnline = isOnline, hasPendingWrites = hasPendingWrites)
                    }

                    HorizontalDivider(color = posColors.borderSubtle)

                    // Status & Action Strip near Clear/Delete:
                    // 1) "ያልተከፈለ" toggle (tap to toggle unpaid for next send, long-press to view list)
                    // 2) Live Unpaid Notification Badge ("ያልተከፈሉ X • Y ብር") -> opens unpaid list
                    // 3) Unserved Sent Orders ("ያልወጡ X") -> opens cancelable pending orders
                    // 4) Returned alert ("የተመለሰ X") when any exist
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // "ያልተከፈለ" Toggle (tap to mark next order unpaid; long press to open list)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (keypadState.draftUnpaid) posColors.pendingAmber else posColors.pendingAmberContainer,
                            border = BorderStroke(1.dp, posColors.pendingAmber),
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = onToggleDraftUnpaid,
                                    onLongClick = { showUnpaidSheet = true }
                                )
                                .testTag("cashier_unpaid_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Receipt,
                                    contentDescription = "ያልተከፈለ",
                                    tint = if (keypadState.draftUnpaid) Color.White else posColors.pendingAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (keypadState.draftUnpaid) "ያልተከፈለ ✓" else "ያልተከፈለ",
                                    color = if (keypadState.draftUnpaid) Color.White else posColors.pendingAmber,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        // Unpaid Orders Notification Badge (always shows count & total when unpaid > 0, even after served!)
                        if (unpaidOrders.isNotEmpty()) {
                            Surface(
                                onClick = { showUnpaidSheet = true },
                                shape = RoundedCornerShape(12.dp),
                                color = posColors.returnRed,
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("cashier_unpaid_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.NotificationsActive,
                                        contentDescription = "Unpaid orders",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "${unpaidOrders.size} • ${formatBirr(unpaidTotal)}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        // Urgent / ቅድሚያ priority toggle
                        Surface(
                            onClick = onToggleDraftUrgent,
                            shape = RoundedCornerShape(12.dp),
                            color = if (keypadState.draftUrgent) posColors.returnRed else posColors.returnRedContainer.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, posColors.returnRed.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("cashier_urgent_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bolt,
                                    contentDescription = "ቅድሚያ",
                                    tint = if (keypadState.draftUrgent) Color.White else posColors.returnRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (keypadState.draftUrgent) "ቅድሚያ ✓" else "ቅድሚያ",
                                    color = if (keypadState.draftUrgent) Color.White else posColors.returnRed,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        // Unserved Sent Tickets ("ያልወጡ") -> counts TICKETS (not items), tap to inspect/edit/cancel
                        Surface(
                            onClick = { showPendingSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, posColors.borderSubtle),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("cashier_pending_orders_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.HourglassTop,
                                    contentDescription = "Unserved tickets",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "ያልወጡ (${todayPendingOrders.size})",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        // Returned ("የተመለሰ") indicator visible on Cashier side
                        if (todayReturnedOrders.isNotEmpty()) {
                            Surface(
                                onClick = { showReturnedSheet = true },
                                color = posColors.returnRedContainer,
                                border = BorderStroke(1.dp, posColors.returnRed.copy(alpha = 0.45f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("cashier_returned_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.WarningAmber,
                                        contentDescription = "Returned",
                                        tint = posColors.returnRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "የተመለሰ (${todayReturnedOrders.size})",
                                        color = posColors.returnRedOnContainer,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(10.dp)
        ) {
            val isWide = maxWidth >= 680.dp
            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ReceiptTicketPanel(
                        keypadState = keypadState,
                        enteredCode = enteredCode,
                        effectiveCode = effectiveCode,
                        pendingQty = pendingQty,
                        matchedMenuItem = matchedMenuItem,
                        alias78To79 = config.alias78To79,
                        onRemoveLine = onRemoveLine,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    CashierKeypadPanel(
                        isSending = keypadState.isSending,
                        canSend = keypadState.draftLines.isNotEmpty() || keypadState.codeBuffer.isNotEmpty(),
                        onDigit = onDigit,
                        onMultiply = onMultiply,
                        onBackspace = onBackspace,
                        onAddNext = onAddNext,
                        onSendClicked = onSendClicked,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ReceiptTicketPanel(
                        keypadState = keypadState,
                        enteredCode = enteredCode,
                        effectiveCode = effectiveCode,
                        pendingQty = pendingQty,
                        matchedMenuItem = matchedMenuItem,
                        alias78To79 = config.alias78To79,
                        onRemoveLine = onRemoveLine,
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxWidth()
                    )
                    CashierKeypadPanel(
                        isSending = keypadState.isSending,
                        canSend = keypadState.draftLines.isNotEmpty() || keypadState.codeBuffer.isNotEmpty(),
                        onDigit = onDigit,
                        onMultiply = onMultiply,
                        onBackspace = onBackspace,
                        onAddNext = onAddNext,
                        onSendClicked = onSendClicked,
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }

    // Staff / Waiter Picker Modal Sheet on Send ("ሴንድ")
    if (keypadState.showStaffPicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissStaffPicker,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ሰራተኛ ይምረጡ • Select Staff",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "${keypadState.draftItemCount} ትዕዛዝ • ጠቅላላ ${formatBirr(keypadState.draftTotal)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismissStaffPicker) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                // Quick Urgent & Unpaid flags + Short 15-char note
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = onToggleDraftUrgent,
                        color = if (keypadState.draftUrgent) posColors.returnRed else posColors.returnRedContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("staff_picker_urgent_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = if (keypadState.draftUrgent) Color.White else posColors.returnRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (keypadState.draftUrgent) "ቅድሚያ ✓" else "ቅድሚያ / Urgent",
                                color = if (keypadState.draftUrgent) Color.White else posColors.returnRed,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    Surface(
                        onClick = onToggleDraftUnpaid,
                        color = if (keypadState.draftUnpaid) posColors.pendingAmber else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (keypadState.draftUnpaid) "ያልተከፈለ ✓" else "ያልተከፈለ?",
                            color = if (keypadState.draftUnpaid) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = keypadState.draftNote,
                    onValueChange = onUpdateDraftNote,
                    label = { Text("አጭር ማስታወሻ • Note (${keypadState.draftNote.length}/15)") },
                    placeholder = { Text("ምሳሌ: ስኳር የለሽ / Urgent") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cashier_short_note_input")
                )

                HorizontalDivider(color = posColors.borderSubtle)

                val staffList = config.staff.ifEmpty { listOf("ሰራተኛ 1", "ሰራተኛ 2") }
                staffList.forEachIndexed { idx, staffName ->
                    Button(
                        onClick = { onSelectStaffAndSend(staffName) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = posColors.dispatchGreen,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("staff_pick_$idx")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = staffName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }

    // Unserved Sent Tickets Sheet ("ያልወጡ") - tap a ticket to inspect & edit items/quantities, or cancel
    if (showPendingSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPendingSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "ያልወጡ ትኬቶች • Unserved (${todayPendingOrders.size} ትኬት)",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "ዝርዝር ለማየት ወይም ለማስተካከል ትኬቱን ይንኩ • Tap a ticket to inspect or edit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (todayPendingOrders.isEmpty()) {
                    Text(
                        text = "በመጠባበቅ ላይ ያለ ትኬት የለም",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(todayPendingOrders, key = { it.id }) { order ->
                            val seqNo = seqMap[order.id] ?: order.ticketNumber
                            Surface(
                                onClick = { editingPendingOrderId = order.id },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.background,
                                border = BorderStroke(
                                    1.dp,
                                    if (order.urgent) posColors.returnRed.copy(alpha = 0.7f) else posColors.borderSubtle
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pending_ticket_row_${order.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "#$seqNo • ${order.staffName}",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            if (order.urgent) {
                                                Surface(
                                                    color = posColors.returnRed,
                                                    shape = RoundedCornerShape(999.dp)
                                                ) {
                                                    Text(
                                                        text = "ቅድሚያ",
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            if (order.unpaid) {
                                                Surface(
                                                    color = posColors.pendingAmberContainer,
                                                    shape = RoundedCornerShape(999.dp)
                                                ) {
                                                    Text(
                                                        text = "ያልተከፈለ",
                                                        color = posColors.pendingAmber,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = order.items.joinToString(" • ") { "${it.n}×${it.q}" },
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (!order.note.isNullOrBlank()) {
                                            Text(
                                                text = "ማስታወሻ: ${order.note}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            text = "${CafeViewModel.formatTimeShort(order.createdAt)} • ${order.items.sumOf { it.q }} ትዕዛዝ • ${formatBirr(order.totalAmount)}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Surface(
                                            onClick = { editingPendingOrderId = order.id },
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.height(38.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Edit,
                                                    contentDescription = "Edit ticket",
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "አስተካክል",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = { onCancelPendingOrder(order.id) },
                                            border = BorderStroke(1.dp, posColors.returnRed),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = posColors.returnRed),
                                            contentPadding = PaddingValues(horizontal = 10.dp),
                                            modifier = Modifier
                                                .height(38.dp)
                                                .testTag("cancel_pending_order_${order.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "ሰርዝ",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("ሰርዝ", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val editingOrder = editingPendingOrderId?.let { targetId ->
        todayPendingOrders.firstOrNull { it.id == targetId }
    }
    if (editingOrder != null) {
        PendingTicketEditDialog(
            order = editingOrder,
            displayTicketNumber = seqMap[editingOrder.id] ?: editingOrder.ticketNumber,
            menuItems = menuItems,
            onDismiss = { editingPendingOrderId = null },
            onSave = { updatedItems, updatedUrgent, updatedNote ->
                onUpdatePendingOrder(editingOrder.id, updatedItems, updatedUrgent, updatedNote)
                editingPendingOrderId = null
            },
            onCancelOrder = {
                onCancelPendingOrder(editingOrder.id)
                editingPendingOrderId = null
            }
        )
    }

    // Unpaid Orders Sheet ("ያልተከፈሉ ትኬቶች") - mark "ተከፍሏል" when paid
    if (showUnpaidSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showUnpaidSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ያልተከፈሉ ትኬቶች (${unpaidOrders.size})",
                        style = MaterialTheme.typography.headlineSmall,
                        color = posColors.pendingAmber
                    )
                    Text(
                        text = formatBirr(unpaidTotal),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = posColors.returnRed
                    )
                }

                if (unpaidOrders.isEmpty()) {
                    Text(
                        text = "ያልተከፈለ ትኬት የለም",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(unpaidOrders, key = { it.id }) { order ->
                            val seqNo = seqMap[order.id] ?: order.ticketNumber
                            val statusLabel = if (order.orderStatus() == OrderStatus.COMPLETED) "ወጥቷል" else "በመጠባበቅ"
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = posColors.pendingAmberContainer.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, posColors.pendingAmber.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "#$seqNo • ${order.staffName}",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(999.dp)
                                            ) {
                                                Text(
                                                    text = statusLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = order.items.joinToString(" • ") { "${it.n}×${it.q}" },
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "${CafeViewModel.formatTimeShort(order.createdAt)} • ${formatBirr(order.totalAmount)}",
                                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                                            color = posColors.pendingAmber
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { onMarkOrderPaid(order.id) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = posColors.dispatchGreen,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("mark_paid_${order.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = "ተከፍሏል",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("ተከፍሏል")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Returned Orders Sheet on Cashier Side ("የተመለሰ")
    if (showReturnedSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showReturnedSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "የተመለሱ ትኬቶች • Returned (${todayReturnedOrders.size})",
                    style = MaterialTheme.typography.headlineSmall,
                    color = posColors.returnRed
                )
                todayReturnedOrders.forEach { order ->
                    val seqNo = seqMap[order.id] ?: order.ticketNumber
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = posColors.returnRedContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, posColors.returnRed.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#$seqNo • ${order.staffName}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Surface(
                                    color = posColors.returnRed,
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = "የተመለሰ",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = order.items.joinToString(" • ") { "${it.n}×${it.q}" },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${CafeViewModel.formatTimeShort(order.createdAt)}  |  ${formatBirr(order.totalAmount)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = posColors.returnRedOnContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(40.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ReceiptTicketPanel(
    keypadState: KeypadState,
    enteredCode: Int?,
    effectiveCode: Int?,
    pendingQty: Int,
    matchedMenuItem: MenuItem?,
    alias78To79: Boolean,
    onRemoveLine: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val posColors = LocalPosColors.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, posColors.borderSubtle),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Live Entry Strip
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val expressionText = when {
                        keypadState.hasMultiply -> "${keypadState.qtyBuffer} × ${keypadState.codeBuffer.ifEmpty { "_" }}"
                        keypadState.codeBuffer.isNotEmpty() -> "1 × ${keypadState.codeBuffer}"
                        else -> "ቁጥር ይንኩ • Tap #"
                    }

                    Text(
                        text = expressionText,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("cashier_expression_display")
                    )

                    if (enteredCode != null) {
                        val previewText = if (matchedMenuItem != null) {
                            val aliasTag = if (alias78To79 && enteredCode == 78) "78→79 " else ""
                            "$aliasTag${matchedMenuItem.name} • ${formatBirr(matchedMenuItem.price * pendingQty)}"
                        } else {
                            "#$effectiveCode የለም"
                        }
                        Surface(
                            color = if (matchedMenuItem != null) posColors.dispatchGreenContainer else posColors.returnRedContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = previewText,
                                color = if (matchedMenuItem != null) posColors.dispatchGreenOnContainer else posColors.returnRedOnContainer,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Receipt Line Items: Tap any row to remove it; formatted as "ማኪያቶ×2" and "100 ብር" on right
            if (keypadState.draftLines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "ትኬት ባዶ ነው • Ticket Empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ምሳሌ: 2 → × → 1 → +  (ማኪያቶ×2)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(keypadState.draftLines, key = { it.code }) { line ->
                        DraftTicketRow(
                            line = line,
                            onRemove = { onRemoveLine(line.code) }
                        )
                    }
                }
            }

            // Receipt Footer Total ("ትዕዛዝ" instead of "ዕቃ")
            HorizontalDivider(color = posColors.borderSubtle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "ጠቅላላ (${keypadState.draftItemCount} ትዕዛዝ)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    if (keypadState.draftUrgent) {
                        Surface(
                            color = posColors.returnRed,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "ቅድሚያ",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (keypadState.draftUnpaid) {
                        Surface(
                            color = posColors.pendingAmberContainer,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "ያልተከፈለ",
                                color = posColors.pendingAmber,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Text(
                    text = formatBirr(keypadState.draftTotal),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("cashier_draft_total")
                )
            }
        }
    }
}

@Composable
private fun DraftTicketRow(
    line: DraftTicketLine,
    onRemove: () -> Unit
) {
    val posColors = LocalPosColors.current
    Surface(
        onClick = onRemove,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, posColors.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("draft_line_${line.code}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${line.name}×${line.qty}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatBirr(line.lineTotal),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    )
                )
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove ${line.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun CashierKeypadPanel(
    isSending: Boolean,
    canSend: Boolean,
    onDigit: (String) -> Unit,
    onMultiply: () -> Unit,
    onBackspace: () -> Unit,
    onAddNext: () -> Unit,
    onSendClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val posColors = LocalPosColors.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val digitRows = listOf(
            listOf("7", "8", "9"),
            listOf("4", "5", "6"),
            listOf("1", "2", "3")
        )

        digitRows.forEach { row ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { digit ->
                    KeypadButton(
                        label = digit,
                        onClick = { onDigit(digit) },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("keypad_$digit")
                    )
                }
                val opLabel = when (row.first()) {
                    "7" -> "⌫"
                    "4" -> "×"
                    else -> "+"
                }
                when (opLabel) {
                    "⌫" -> Surface(
                        onClick = onBackspace,
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, posColors.borderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("keypad_backspace")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Backspace or remove last item",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    "×" -> KeypadButton(
                        label = "×",
                        onClick = onMultiply,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("keypad_multiply")
                    )
                    else -> KeypadButton(
                        label = "+",
                        onClick = onAddNext,
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("keypad_add")
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeypadButton(
                label = "0",
                onClick = { onDigit("0") },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("keypad_0")
            )

            Button(
                onClick = onSendClicked,
                enabled = !isSending && canSend,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = posColors.dispatchGreen,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(3f)
                    .fillMaxHeight()
                    .testTag("cashier_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "ሴንድ • SEND",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val posColors = LocalPosColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, posColors.borderSubtle),
        shadowElevation = 1.dp,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp
                )
            )
        }
    }
}

@Composable
private fun PendingTicketEditDialog(
    order: CafeOrder,
    displayTicketNumber: Int,
    menuItems: List<MenuItem>,
    onDismiss: () -> Unit,
    onSave: (items: List<OrderLineItem>, urgent: Boolean, note: String?) -> Unit,
    onCancelOrder: () -> Unit
) {
    val posColors = LocalPosColors.current
    var editedItems by remember(order.id) { mutableStateOf(order.items) }
    var editedUrgent by remember(order.id) { mutableStateOf(order.urgent) }
    var editedNote by remember(order.id) { mutableStateOf(order.note.orEmpty().take(15)) }
    var showAddMenu by remember { mutableStateOf(false) }

    val updatedTotal = remember(editedItems) { editedItems.sumOf { it.lineTotal() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ትኬት #$displayTicketNumber • ${order.staffName}",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${CafeViewModel.formatTimeShort(order.createdAt)} • ${editedItems.sumOf { it.q }} ትዕዛዝ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                // Urgent flag + short note
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { editedUrgent = !editedUrgent },
                        color = if (editedUrgent) posColors.returnRed else posColors.returnRedContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("edit_pending_urgent_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = "ቅድሚያ",
                                tint = if (editedUrgent) Color.White else posColors.returnRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (editedUrgent) "ቅድሚያ ✓" else "ቅድሚያ",
                                color = if (editedUrgent) Color.White else posColors.returnRed,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    OutlinedTextField(
                        value = editedNote,
                        onValueChange = { editedNote = it.replace('\n', ' ').take(15) },
                        label = { Text("ማስታወሻ (${editedNote.length}/15)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(color = posColors.borderSubtle)

                // Items list inside this ticket (existing items retain snapshotted unit prices)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (showAddMenu) 170.dp else 230.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(editedItems, key = { "${it.c}_${it.p}" }) { line ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, posColors.borderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${line.n}×${line.q}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatBirr(line.lineTotal()),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (line.q > 1) {
                                            editedItems = editedItems.map {
                                                if (it.c == line.c && it.p == line.p) it.copy(q = it.q - 1) else it
                                            }
                                        } else if (editedItems.size > 1) {
                                            editedItems = editedItems.filterNot { it.c == line.c && it.p == line.p }
                                        }
                                    },
                                    enabled = line.q > 1 || editedItems.size > 1
                                ) {
                                    Icon(Icons.Filled.Remove, contentDescription = "Decrease ${line.n}")
                                }

                                IconButton(
                                    onClick = {
                                        editedItems = editedItems.map {
                                            if (it.c == line.c && it.p == line.p) it.copy(q = (it.q + 1).coerceAtMost(999)) else it
                                        }
                                    }
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Increase ${line.n}")
                                }

                                if (editedItems.size > 1) {
                                    IconButton(
                                        onClick = {
                                            editedItems = editedItems.filterNot { it.c == line.c && it.p == line.p }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Remove ${line.n}",
                                            tint = posColors.returnRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Add extra item from menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showAddMenu = !showAddMenu }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showAddMenu) "ምናሌ ዝጋ" else "ዕቃ ጨምር • Add Item")
                    }
                    Text(
                        text = formatBirr(updatedTotal),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (showAddMenu) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(menuItems, key = { it.code }) { menuItem ->
                            Surface(
                                onClick = {
                                    val idx = editedItems.indexOfFirst { it.c == menuItem.code }
                                    editedItems = if (idx >= 0) {
                                        editedItems.mapIndexed { i, item ->
                                            if (i == idx) item.copy(q = (item.q + 1).coerceAtMost(999)) else item
                                        }
                                    } else {
                                        editedItems + OrderLineItem(
                                            c = menuItem.code,
                                            n = menuItem.name,
                                            q = 1,
                                            p = menuItem.price
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#${menuItem.code} ${menuItem.name}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = formatBirr(menuItem.price),
                                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = posColors.borderSubtle)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancelOrder,
                        border = BorderStroke(1.dp, posColors.returnRed),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = posColors.returnRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("edit_pending_cancel_ticket")
                    ) {
                        Text("ትኬት ሰርዝ")
                    }
                    Button(
                        onClick = { onSave(editedItems, editedUrgent, editedNote.trim().ifEmpty { null }) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = posColors.dispatchGreen,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("edit_pending_save_ticket")
                    ) {
                        Text("አስቀምጥ • Save")
                    }
                }
            }
        }
    }
}
