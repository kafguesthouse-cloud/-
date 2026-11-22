package com.example.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.CafeOrder
import com.example.data.OrderStatus
import com.example.ui.CafeViewModel
import com.example.ui.components.SyncStatusPill
import com.example.ui.components.formatBirr
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalPosColors
import com.google.firebase.Timestamp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketerScreen(
    orders: List<CafeOrder>,
    isOrdersLoaded: Boolean,
    isOnline: Boolean,
    hasPendingWrites: Boolean,
    keepScreenAwake: Boolean,
    soundEnabled: Boolean,
    onToggleKeepScreenAwake: (Boolean) -> Unit,
    onToggleSoundEnabled: (Boolean) -> Unit,
    onMarkCompleted: (String) -> Unit,
    onMarkReturned: (String) -> Unit,
    onExitRole: () -> Unit
) {
    BackHandler { onExitRole() }

    val posColors = LocalPosColors.current
    val view = LocalView.current
    var expandedOrderId by remember { mutableStateOf<String?>(null) }
    var showRecentCompletedSheet by remember { mutableStateOf(false) }

    // Keep screen awake while on TicketerScreen if enabled
    DisposableEffect(view, keepScreenAwake) {
        val prev = view.keepScreenOn
        view.keepScreenOn = keepScreenAwake
        onDispose {
            view.keepScreenOn = prev
        }
    }

    val toneGenerator = remember {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        } catch (_: Exception) {
            null
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            try {
                toneGenerator?.release()
            } catch (_: Exception) {
            }
        }
    }

    // Chronological sequence: earliest ordered = #1, next = #2, etc.
    val seqMap = remember(orders) {
        CafeViewModel.computeChronologicalSequenceMap(orders)
    }

    // Pending incoming orders appear from top (urgent/priority or chronological order)
    val pendingOrders = remember(orders) {
        orders.filter { it.orderStatus() == OrderStatus.PENDING }
            .sortedWith(
                compareBy<CafeOrder> { it.createdAt ?: Timestamp(Long.MAX_VALUE, 0) }
                    .thenBy { it.ticketNumber }
                    .thenBy { it.id }
            )
    }

    // Play alert sound on new pending ticket arrival or external pending cancellation
    var hasAudioBaseline by remember { mutableStateOf(false) }
    var previousPendingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(isOrdersLoaded, pendingOrders, orders, soundEnabled) {
        if (!isOrdersLoaded) return@LaunchedEffect
        val currentPendingIds = pendingOrders.map { it.id }.toSet()
        val allOrderIds = orders.map { it.id }.toSet()
        if (!hasAudioBaseline) {
            previousPendingIds = currentPendingIds
            hasAudioBaseline = true
            return@LaunchedEffect
        }
        val arrivedIds = currentPendingIds - previousPendingIds
        val cancelledIds = (previousPendingIds - currentPendingIds).filter { it !in allOrderIds }
        previousPendingIds = currentPendingIds

        if (soundEnabled && toneGenerator != null) {
            if (arrivedIds.isNotEmpty()) {
                try {
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
                } catch (_: Exception) {
                }
            } else if (cancelledIds.isNotEmpty()) {
                try {
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 200)
                } catch (_: Exception) {
                }
            }
        }
    }

    val recentCompleted = remember(orders) {
        orders.filter { it.orderStatus() == OrderStatus.COMPLETED }
            .sortedByDescending { it.completedAt ?: it.createdAt ?: Timestamp(0, 0) }
    }

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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = onExitRole,
                            modifier = Modifier.testTag("ticketer_lock_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Switch Role",
                                tint = posColors.dispatchGreen
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ትኬተር • Order Queue",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${pendingOrders.size} ትኬት በመጠባበቅ ላይ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (recentCompleted.isNotEmpty()) {
                            Surface(
                                onClick = { showRecentCompletedSheet = true },
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("ticketer_recent_completed_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.History,
                                        contentDescription = "Recent completed orders",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "ተመላሽ (${recentCompleted.size})",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }

                        SyncStatusPill(isOnline = isOnline, hasPendingWrites = hasPendingWrites)
                    }

                    HorizontalDivider(color = posColors.borderSubtle)

                    // Sound alert & Keep Screen Awake bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = { onToggleSoundEnabled(!soundEnabled) },
                            color = if (soundEnabled) posColors.dispatchGreenContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (soundEnabled) posColors.dispatchGreen.copy(alpha = 0.5f) else posColors.borderSubtle
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("ticketer_sound_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = "Sound alert",
                                    tint = if (soundEnabled) posColors.dispatchGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (soundEnabled) "ድምፅ ክፍት" else "ድምፅ ዝግ",
                                    color = if (soundEnabled) posColors.dispatchGreenOnContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        Surface(
                            onClick = { onToggleKeepScreenAwake(!keepScreenAwake) },
                            color = if (keepScreenAwake) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (keepScreenAwake) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else posColors.borderSubtle
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("ticketer_keep_awake_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (keepScreenAwake) Icons.Filled.WbSunny else Icons.Filled.ScreenLockPortrait,
                                    contentDescription = "Keep Screen Awake",
                                    tint = if (keepScreenAwake) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (keepScreenAwake) "ስክሪን ክፍት ✓" else "ስክሪን ክፍት",
                                    color = if (keepScreenAwake) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (pendingOrders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "አዲስ ትዕዛዝ የለም",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ካሸር ትኬት ሲልክ እዚህ ወዲያውኑ ይመጣል",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 760.dp),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(pendingOrders, key = { it.id }) { order ->
                        val seqNo = seqMap[order.id] ?: order.ticketNumber
                        TicketerOrderCard(
                            order = order,
                            displayTicketNumber = seqNo,
                            isExpanded = expandedOrderId == order.id,
                            onToggleExpand = {
                                expandedOrderId = if (expandedOrderId == order.id) null else order.id
                            },
                            onComplete = { onMarkCompleted(order.id) },
                            onReturn = { onMarkReturned(order.id) }
                        )
                    }
                }
            }
        }
    }

    if (showRecentCompletedSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showRecentCompletedSheet = false },
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
                    text = "የወጡ ትኬቶች • Mark Return (ተመልሷል)",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "የወጣ ትዕዛዝ ከተመለሰ 'ተመልሷል' ይንኩ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(recentCompleted.take(25), key = { it.id }) { order ->
                        val seqNo = seqMap[order.id] ?: order.ticketNumber
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, posColors.borderSubtle),
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
                                    Text(
                                        text = "#$seqNo • ${order.staffName} • ${CafeViewModel.formatTimeShort(order.createdAt)}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = order.items.joinToString(", ") { "${it.n}×${it.q}" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                OutlinedButton(
                                    onClick = { onMarkReturned(order.id) },
                                    border = BorderStroke(1.dp, posColors.returnRed),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = posColors.returnRed
                                    ),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Text("ተመልሷል")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketerOrderCard(
    order: CafeOrder,
    displayTicketNumber: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onComplete: () -> Unit,
    onReturn: () -> Unit
) {
    val posColors = LocalPosColors.current
    var dragOffsetY by remember(order.id) { mutableFloatStateOf(0f) }
    var visible by remember(order.id) { mutableStateOf(true) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(220)
        ) + shrinkVertically(tween(220)) + fadeOut(tween(180))
    ) {
        Surface(
            onClick = onToggleExpand,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                if (order.urgent) 2.dp else 1.5.dp,
                if (order.urgent) posColors.returnRed else posColors.borderSubtle
            ),
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, dragOffsetY.roundToInt().coerceAtMost(0)) }
                .pointerInput(order.id) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffsetY < -110f) {
                                visible = false
                                onReturn()
                            } else {
                                dragOffsetY = 0f
                            }
                        },
                        onDragCancel = { dragOffsetY = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            val next = dragOffsetY + dragAmount
                            if (next <= 0f) dragOffsetY = next.coerceAtLeast(-160f)
                        }
                    )
                }
                .testTag("ticketer_order_${order.id}")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = if (order.urgent) posColors.returnRed else MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "#$displayTicketNumber",
                                color = if (order.urgent) Color.White else MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = order.staffName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (order.urgent) {
                                    Surface(
                                        color = posColors.returnRed,
                                        shape = RoundedCornerShape(999.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Bolt,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = "ቅድሚያ",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
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
                                text = "${CafeViewModel.formatTimeShort(order.createdAt)} • ${order.items.sumOf { it.q }} ትዕዛዝ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                visible = false
                                onComplete()
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = posColors.dispatchGreen,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("ticketer_complete_${order.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "ወጥቷል",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ወጥቷል",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!order.note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "ማስታወሻ: ${order.note}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(color = posColors.borderSubtle)
                        order.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${item.n}×${item.q}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatBirr(item.lineTotal()),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(color = posColors.borderSubtle)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    visible = false
                                    onReturn()
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, posColors.returnRed),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = posColors.returnRed),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("ticketer_return_${order.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "ተመልሷል",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ተመልሷል",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            Text(
                                text = formatBirr(order.totalAmount),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
