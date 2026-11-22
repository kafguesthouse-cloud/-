package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.CafeOrder
import com.example.data.OrderLineItem
import com.example.data.OrderStatus
import com.example.ui.CafeViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("BunaPOS", appName)
    }

    @Test
    fun `report summary separates completed sales from returned orders`() {
        val orders = listOf(
            CafeOrder(
                id = "o1",
                ticketNumber = 1,
                dayKey = "2026-11-22",
                staffName = "ሰራተኛ 1",
                status = OrderStatus.COMPLETED.name,
                items = listOf(OrderLineItem(c = 1, n = "ማኪያቶ", q = 2, p = 50.0)),
                totalAmount = 100.0
            ),
            CafeOrder(
                id = "o2",
                ticketNumber = 2,
                dayKey = "2026-11-22",
                staffName = "ሰራተኛ 2",
                status = OrderStatus.RETURNED.name,
                items = listOf(OrderLineItem(c = 79, n = "ልዩ ማኪያቶ", q = 1, p = 65.0)),
                totalAmount = 65.0
            ),
            CafeOrder(
                id = "o3",
                ticketNumber = 3,
                dayKey = "2026-11-22",
                staffName = "ሰራተኛ 1",
                status = OrderStatus.PENDING.name,
                items = listOf(OrderLineItem(c = 2, n = "ቡና", q = 1, p = 40.0)),
                totalAmount = 40.0
            )
        )

        val summary = CafeViewModel.buildReportSummary("2026-11-22", orders)
        assertEquals(100.0, summary.completedTotalRevenue, 0.01)
        assertEquals(65.0, summary.returnedTotalAmount, 0.01)
        assertEquals(1, summary.completedOrders.size)
        assertEquals(1, summary.returnedOrders.size)
        assertEquals(1, summary.pendingCount)
        assertEquals(2, summary.itemSales.first().qtySold)
    }

    @Test
    fun `app password pbkdf2 hashing verifies matching password and rejects wrong password`() {
        val salt = com.example.ui.auth.AppPasswordCrypto.generateSaltHex()
        val hash = com.example.ui.auth.AppPasswordCrypto.hashAppPassword("bunaSecret26", salt)
        org.junit.Assert.assertTrue(
            com.example.ui.auth.AppPasswordCrypto.verifyAppPassword("bunaSecret26", salt, hash)
        )
        org.junit.Assert.assertFalse(
            com.example.ui.auth.AppPasswordCrypto.verifyAppPassword("wrongPass", salt, hash)
        )
    }

    @Test
    fun `report summary aggregates per-staff sales unpaid totals and counts tickets instead of items`() {
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
                staffName = "ሰራተኛ 1",
                status = OrderStatus.PENDING.name,
                items = listOf(
                    OrderLineItem(c = 1, n = "ማኪያቶ", q = 3, p = 60.0),
                    OrderLineItem(c = 3, n = "ሻይ", q = 2, p = 25.0)
                ),
                totalAmount = 230.0,
                unpaid = false
            )
        )

        val summary = CafeViewModel.buildReportSummary("2026-11-22", orders)
        // 1 pending ticket even though it has 5 items inside it
        assertEquals(1, summary.pendingCount)
        // Price snapshot on t1 remains 50.0 (not 60.0)
        assertEquals(140.0, summary.completedTotalRevenue, 0.01)

        val s1 = summary.staffSummaries.first { it.staffName == "ሰራተኛ 1" }
        assertEquals(1, s1.completedOrderCount)
        assertEquals(140.0, s1.completedSalesTotal, 0.01)
        assertEquals(1, s1.unpaidCount)
        assertEquals(140.0, s1.unpaidTotal, 0.01)
    }

    @Test
    fun `backup json export and parse preserves menu items staff and alias setting`() {
        val config = com.example.data.CafeConfig(
            alias78To79 = true,
            staff = listOf("ሰራተኛ 1", "ሰራተኛ 2")
        )
        val menu = listOf(
            com.example.data.MenuItem(id = "1", code = 1, name = "ማኪያቶ", price = 50.0),
            com.example.data.MenuItem(id = "79", code = 79, name = "ልዩ ማኪያቶ", price = 65.0)
        )
        val json = CafeViewModel.buildBackupJson(config, menu)
        val parsed = CafeViewModel.parseBackupJson(json).getOrThrow()
        assertEquals(true, parsed.alias78To79)
        assertEquals(listOf("ሰራተኛ 1", "ሰራተኛ 2"), parsed.staff)
        assertEquals(2, parsed.menuItems.size)
        assertEquals("ማኪያቶ", parsed.menuItems[0].name)
        assertEquals(65.0, parsed.menuItems[1].price, 0.01)
    }
}
