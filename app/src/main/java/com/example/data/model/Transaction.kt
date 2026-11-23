package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val invoiceNumber: String,
    val timestamp: Long = System.currentTimeMillis(),
    val totalAmount: Double,
    val paymentMethod: String = "TUNAI", // "TUNAI" or "QRIS"
    val amountReceived: Double = 0.0,
    val changeAmount: Double = 0.0,
    val customerName: String = "",
    val note: String = "",
    val totalItemsCount: Int = 0,
    val status: String = "COMPLETED" // COMPLETED, VOID
)
