package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CafeOrder
import com.example.ui.CafeViewModel
import com.example.ui.DailyReportSummary
import com.example.ui.components.formatBirr
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalPosColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ReportScreen(
    selectedDayKey: String,
    activeDayEpoch: Long,
    orders: List<CafeOrder>,
    onChangeDayKey: (String) -> Unit,
    onStartNewDay: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val posColors = LocalPosColors.current
    val isToday = selectedDayKey == CafeViewModel.todayDayKey()
    var showCurrentEpochOnly by remember(selectedDayKey, activeDayEpoch) { mutableStateOf(isToday) }
    var showNewDayConfirmDialog by remember { mutableStateOf(false) }

    val filteredOrders = remember(orders, isToday, activeDayEpoch, showCurrentEpochOnly) {
        if (isToday && showCurrentEpochOnly) {
            orders.filter { it.dayEpoch == activeDayEpoch }
        } else {
            orders
        }
    }

    val seqMap = remember(filteredOrders) {
        CafeViewModel.computeChronologicalSequenceMap(filteredOrders)
    }
    val summary = remember(selectedDayKey, filteredOrders) {
        CafeViewModel.buildReportSummary(selectedDayKey, filteredOrders)
    }

    var phoneSection by remember { mutableIntStateOf(0) } // 0 = Sales & Archive, 1 = Returned

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("report_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Cashier"
                                )
                            }
                            Text(
                                text = "ሪፖርት • Report",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }

                        // Date stepper
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(onClick = { onChangeDayKey(shiftDayKey(selectedDayKey, -1)) }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = if (isToday) "ዛሬ ($selectedDayKey)" else selectedDayKey,
                                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onChangeDayKey(shiftDayKey(selectedDayKey, 1)) },
                                enabled = selectedDayKey < CafeViewModel.todayDayKey()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                            }
                        }
                    }

                    if (isToday) {
                        HorizontalDivider(color = posColors.borderSubtle)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (activeDayEpoch > 1L) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        onClick = { showCurrentEpochOnly = true },
                                        color = if (showCurrentEpochOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 10.dp)
                                        ) {
                                            Text(
                                                text = "አሁን (#$activeDayEpoch)",
                                                color = if (showCurrentEpochOnly) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                    Surface(
                                        onClick = { showCurrentEpochOnly = false },
                                        color = if (!showCurrentEpochOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 10.dp)
                                        ) {
                                            Text(
                                                text = "ሙሉ ቀን ታሪክ",
                                                color = if (!showCurrentEpochOnly) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "የዛሬ ሽያጭ • Current Day",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedButton(
                                onClick = { showNewDayConfirmDialog = true },
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("report_start_new_day_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.RestartAlt,
                                    contentDescription = "Start New Day",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "አዲስ ቀን ጀምር",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
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
                .padding(12.dp)
        ) {
            val isWide = maxWidth >= 680.dp

            if (isWide) {
                // Two-column layout: Main Sales & Archive on left, Dedicated Returned ("የተመለሰ") side column on right
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MainSalesColumn(
                        summary = summary,
                        seqMap = seqMap,
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight()
                    )
                    ReturnedSideColumn(
                        returnedOrders = summary.returnedOrders,
                        returnedTotal = summary.returnedTotalAmount,
                        seqMap = seqMap,
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Side-by-side KPI strip keeping Sales & Returned strictly separate
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryStatCard(
                            title = "ጠቅላላ ሽያጭ",
                            value = formatBirr(summary.completedTotalRevenue),
                            subtitle = "${summary.completedOrders.size} ትኬት",
                            containerColor = posColors.dispatchGreenContainer,
                            contentColor = posColors.dispatchGreenOnContainer,
                            onClick = { phoneSection = 0 },
                            selected = phoneSection == 0,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("report_sales_tab_card")
                        )
                        SummaryStatCard(
                            title = "የተመለሰ (ለብቻ)",
                            value = formatBirr(summary.returnedTotalAmount),
                            subtitle = "${summary.returnedOrders.size} ትኬት",
                            containerColor = posColors.returnRedContainer,
                            contentColor = posColors.returnRedOnContainer,
                            onClick = { phoneSection = 1 },
                            selected = phoneSection == 1,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("report_returned_tab_card")
                        )
                    }

                    if (phoneSection == 0) {
                        MainSalesColumn(
                            summary = summary,
                            seqMap = seqMap,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    } else {
                        ReturnedSideColumn(
                            returnedOrders = summary.returnedOrders,
                            returnedTotal = summary.returnedTotalAmount,
                            seqMap = seqMap,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showNewDayConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showNewDayConfirmDialog = false },
            title = { Text("አዲስ ቀን / ዙር ጀምር?") },
            text = {
                Text("ያለፉት ትኬቶች በሙሉ ቀን ታሪክ ውስጥ ተቀምጠው ይቆያሉ፤ የትኬት ቁጥር ግን ከ#1 እና የአዲስ ቀን ሽያጭ በአዲስ ይጀምራል።")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNewDayConfirmDialog = false
                        showCurrentEpochOnly = true
                        onStartNewDay()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = posColors.dispatchGreen,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_start_new_day_button")
                ) {
                    Text("አዲስ ቀን ጀምር")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewDayConfirmDialog = false }) {
                    Text("ተመለስ")
                }
            }
        )
    }
}

@Composable
private fun SummaryStatCard(
    title: String,
    value: String,
    subtitle: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = containerColor,
        shape = RoundedCornerShape(16.dp),
        border = if (selected) BorderStroke(2.dp, contentColor) else null,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = contentColor,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = subtitle,
                color = contentColor.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MainSalesColumn(
    summary: DailyReportSummary,
    seqMap: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val posColors = LocalPosColors.current

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Revenue Banner
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, posColors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "የተጣራ ሽያጭ (Net Completed)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatBirr(summary.completedTotalRevenue),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = posColors.dispatchGreen
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${summary.completedOrders.size} የወጡ",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (summary.pendingCount > 0) {
                            Text(
                                text = "${summary.pendingCount} በመጠባበቅ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = posColors.pendingAmber
                            )
                        }
                    }
                }
            }
        }

        // Per-Staff Daily Summary (Sales + Unpaid count/amount)
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, posColors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "የሰራተኞች ማጠቃለያ • Staff Summary",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (summary.staffSummaries.isEmpty()) {
                        Text(
                            text = "እስካሁን የተመዘገበ የሰራተኛ ትዕዛዝ የለም",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        summary.staffSummaries.forEachIndexed { index, staffRow ->
                            if (index > 0) HorizontalDivider(color = posColors.borderSubtle)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = staffRow.staffName,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${staffRow.completedOrderCount} የወጡ" +
                                            if (staffRow.returnedCount > 0) " • ${staffRow.returnedCount} የተመለሰ" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = formatBirr(staffRow.completedSalesTotal),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = posColors.dispatchGreen
                                    )
                                    if (staffRow.unpaidCount > 0) {
                                        Surface(
                                            color = posColors.pendingAmberContainer,
                                            shape = RoundedCornerShape(999.dp)
                                        ) {
                                            Text(
                                                text = "ያልተከፈለ: ${staffRow.unpaidCount} (${formatBirr(staffRow.unpaidTotal)})",
                                                color = posColors.pendingAmber,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Item Quantities Sold Breakdown
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, posColors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "የተሸጡ ዕቃዎች ብዛት • Item Counts",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (summary.itemSales.isEmpty()) {
                        Text(
                            text = "እስካሁን የተሸጠ የለም",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        summary.itemSales.forEachIndexed { index, row ->
                            if (index > 0) HorizontalDivider(color = posColors.borderSubtle)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${row.code}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(44.dp)
                                )
                                Text(
                                    text = row.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${row.qtySold}×",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = formatBirr(row.totalRevenue),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFamily)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Completed Ticket Archive Header
        item {
            Text(
                text = "የትኬቶች መዝገብ • Ticket Archive (${summary.completedOrders.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (summary.completedOrders.isEmpty()) {
            item {
                Text(
                    text = "የተመዘገበ ትኬት የለም",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(summary.completedOrders, key = { it.id }) { order ->
                ArchiveOrderCard(
                    order = order,
                    displayTicketNumber = seqMap[order.id] ?: order.ticketNumber,
                    isReturned = false
                )
            }
        }
    }
}

@Composable
private fun ReturnedSideColumn(
    returnedOrders: List<CafeOrder>,
    returnedTotal: Double,
    seqMap: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val posColors = LocalPosColors.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = posColors.returnRedContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.5.dp, posColors.returnRed.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = posColors.returnRedContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = "የተመለሰ",
                            tint = posColors.returnRed
                        )
                        Column {
                            Text(
                                text = "የተመለሰ • Returned",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = posColors.returnRedOnContainer
                            )
                            Text(
                                text = "ከሽያጭ ድምር ለብቻ የተለየ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = posColors.returnRedOnContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Text(
                        text = formatBirr(returnedTotal),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = posColors.returnRed
                    )
                }
            }

            if (returnedOrders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "የተመለሰ ትኬት የለም",
                        style = MaterialTheme.typography.bodyLarge,
                        color = posColors.returnRedOnContainer
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(returnedOrders, key = { it.id }) { order ->
                        ArchiveOrderCard(
                            order = order,
                            displayTicketNumber = seqMap[order.id] ?: order.ticketNumber,
                            isReturned = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveOrderCard(
    order: CafeOrder,
    displayTicketNumber: Int,
    isReturned: Boolean
) {
    val posColors = LocalPosColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isReturned) posColors.returnRed.copy(alpha = 0.4f) else posColors.borderSubtle
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#$displayTicketNumber • ${order.staffName}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (order.unpaid && !isReturned) {
                        Surface(
                            color = posColors.pendingAmberContainer,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "ያልተከፈለ",
                                color = posColors.pendingAmber,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (isReturned) {
                        Surface(
                            color = posColors.returnRed,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "የተመለሰ",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = CafeViewModel.formatTimeShort(order.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = order.items.joinToString(" • ") { "${it.n}×${it.q}" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isReturned) {
                val orderTime = CafeViewModel.formatTimeShort(order.createdAt)
                val returnTime = CafeViewModel.formatTimeShort(order.completedAt ?: order.updatedAt)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "የታዘዘ: $orderTime  •  የተመለሰ: $returnTime",
                    style = MaterialTheme.typography.labelMedium,
                    color = posColors.returnRedOnContainer
                )
            }
            if (!order.note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "ማስታወሻ: ${order.note}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatBirr(order.totalAmount),
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                color = if (isReturned) posColors.returnRed else posColors.dispatchGreen
            )
        }
    }
}

private fun shiftDayKey(dayKey: String, deltaDays: Int): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdf.parse(dayKey) ?: return dayKey
        val cal = Calendar.getInstance()
        cal.time = date
        cal.add(Calendar.DAY_OF_YEAR, deltaDays)
        sdf.format(cal.time)
    } catch (_: Exception) {
        dayKey
    }
}
