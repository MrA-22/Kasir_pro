package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val categoryId: Long,
    val categoryName: String,
    val price: Double,
    val costPrice: Double = 0.0,
    val stock: Int = 0,
    val minStockAlert: Int = 5,
    val unit: String = "Porsi", // Porsi, Pcs, Bks, Botol, Cup, Box, Kg, dll
    val barcode: String = "",
    val isAvailable: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
