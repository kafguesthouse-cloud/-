package com.example

import com.example.data.CafeOrder
import com.example.data.OrderLineItem
import com.example.data.OrderStatus
import com.example.ui.CafeViewModel
import com.example.ui.auth.AppPasswordCrypto
import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test

/**
 * Local JVM unit tests for BunaPOS business logic.
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun reportSummary_separatesCompletedReturned_andAggregatesStaffAndTicketCounts() {
        val orders = listOf(
            CafeOrder(
                id = "t1",
                ticketNumber = 1,
                dayKey = "2026-11-22",
                staffName = "ሰራተኛ 1",
                status = OrderStatus.COMPLETED.name,
                items = listOf(
                    OrderLineItem(c = 1, n = "ማኪያቶ", q = 2, p = 50.0),
                    OrderLineItem(c = 2, n = "ቡና", q = 1, p = 40.0)
                ),
                totalAmount = 140.0,
                unpaid = true
            ),
            CafeOrder(
                id = "t2",
                ticketNumber = 2,
                dayKey = "2026-11-22",
                staffName = "ሰራተኛ 2",
                status = OrderStatus.RETURNED.name,
                items = listOf(OrderLineItem(c = 79, n = "ልዩ ማኪያቶ", q = 1, p = 65.0)),
                totalAmount = 65.0
            ),
            CafeOrder(
                id = "t3",
                ticketNumber = 3,
                dayKey = "2026-11-22",
                staffName = "ሰራተኛ 1",
                status = OrderStatus.PENDING.name,
                items = listOf(
                    OrderLineItem(c = 1, n = "ማኪያቶ", q = 3, p = 60.0),
                    OrderLineItem(c = 3, n = "ሻይ", q = 2, p = 25.0)
                ),
                totalAmount = 230.0
            )
        )

        val summary = CafeViewModel.buildReportSummary("2026-11-22", orders)
        // t3 has 5 items across 2 lines, but counts as 1 pending ticket
        assertEquals(1, summary.pendingCount)
        // t1 price snapshot stays 50.0 even when newer t3 has 60.0
        assertEquals(140.0, summary.completedTotalRevenue, 0.01)
        assertEquals(65.0, summary.returnedTotalAmount, 0.01)

        val staff1 = summary.staffSummaries.first { it.staffName == "ሰራተኛ 1" }
        assertEquals(1, staff1.completedOrderCount)
        assertEquals(140.0, staff1.completedSalesTotal, 0.01)
        assertEquals(1, staff1.unpaidCount)
        assertEquals(140.0, staff1.unpaidTotal, 0.01)

        val staff2 = summary.staffSummaries.first { it.staffName == "ሰራተኛ 2" }
        assertEquals(0, staff2.completedOrderCount)
        assertEquals(1, staff2.returnedCount)
    }

    @Test
    fun chronologicalSequence_ordersEarliestAsNumber1() {
        val orders = listOf(
            CafeOrder(id = "late", ticketNumber = 1, createdAt = Timestamp(2000L, 0)),
            CafeOrder(id = "early", ticketNumber = 9, createdAt = Timestamp(1000L, 0))
        )
        val seq = CafeViewModel.computeChronologicalSequenceMap(orders)
        assertEquals(1, seq["early"])
        assertEquals(2, seq["late"])
    }

    @Test
    fun appPasswordCrypto_verifiesCorrectPasswordAndRejectsWrongPassword() {
        val salt = AppPasswordCrypto.generateSaltHex()
        val hash = AppPasswordCrypto.hashAppPassword("bunaSecret26", salt)
        assertTrue(AppPasswordCrypto.verifyAppPassword("bunaSecret26", salt, hash))
        assertFalse(AppPasswordCrypto.verifyAppPassword("wrongPass", salt, hash))
    }
}
