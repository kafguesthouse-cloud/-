package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class CafeConfig(
    @DocumentId val id: String = "",
    val userId: String = "",
    val cashierPin: String = "1111",
    val ticketerPin: String = "2222",
    val pinsCustomized: Boolean = false,
    val alias78To79: Boolean = true,
    val staff: List<String> = listOf("ሰራተኛ 1", "ሰራተኛ 2", "ሰራተኛ 3"),
    val nextTicketSeq: Long = 1L,
    val lastTicketDay: String = "",
    val dayEpoch: Long = 1L,
    val linkedEmail: String = "",
    val appPasswordHash: String = "",
    val appPasswordSalt: String = "",
    val appPasswordSet: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MenuItem(
    @DocumentId val id: String = "",
    val code: Int = 0,
    val name: String = "",
    val price: Double = 0.0,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OrderLineItem(
    val c: Int = 0,
    val n: String = "",
    val q: Int = 1,
    val p: Double = 0.0
) {
    fun lineTotal(): Double = q * p
}

enum class OrderStatus(val labelAm: String, val labelEn: String) {
    PENDING("በመጠባበቅ", "Pending"),
    COMPLETED("ወጥቷል", "Completed"),
    RETURNED("የተመለሰ", "Returned");

    companion object {
        fun fromRaw(raw: String?): OrderStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

@Keep
@IgnoreExtraProperties
data class CafeOrder(
    @DocumentId val id: String = "",
    val ticketNumber: Int = 0,
    val dayKey: String = "",
    val dayEpoch: Long = 1L,
    val staffName: String = "",
    val status: String = OrderStatus.PENDING.name,
    val items: List<OrderLineItem> = emptyList(),
    val totalAmount: Double = 0.0,
    val unpaid: Boolean = false,
    val urgent: Boolean = false,
    val note: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val completedAt: Timestamp? = null,
    val paidAt: Timestamp? = null
) {
    fun orderStatus(): OrderStatus = OrderStatus.fromRaw(status)
}

data class DraftTicketLine(
    val code: Int,
    val enteredCode: Int,
    val name: String,
    val qty: Int,
    val unitPrice: Double
) {
    val lineTotal: Double get() = qty * unitPrice
}

data class StaffDailySummary(
    val staffName: String,
    val completedOrderCount: Int,
    val completedSalesTotal: Double,
    val unpaidCount: Int,
    val unpaidTotal: Double,
    val returnedCount: Int
)

data class CafeBackupData(
    val alias78To79: Boolean,
    val staff: List<String>,
    val menuItems: List<MenuItem>
)

enum class AppRole {
    CASHIER,
    TICKETER
}

sealed interface AppRoute {
    data object RoleSelect : AppRoute
    data object Cashier : AppRoute
    data object Ticketer : AppRoute
    data object Settings : AppRoute
    data object Report : AppRoute
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
