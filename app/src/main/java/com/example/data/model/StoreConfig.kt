package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "store_config")
data class StoreConfig(
    @PrimaryKey
    val id: Int = 1,
    val storeName: String = "Warung Makan",
    val storeAddress: String = "Jl. Raya Barokah No. 88",
    val storePhone: String = "0812-3456-7890",
    val receiptFooter: String = "Terima Kasih Atas Kunjungan Anda!\nBarang yang sudah dibeli tidak dapat ditukar.",
    val qrisMerchantName: String = "Warung Makan / Toko Sukses",
    val qrisPayload: String = "00020101021126570011ID.CO.QRIS.WWW011893600914000000000002030005204581253033605802ID5913WARUNG MAKAN6007JAKARTA61051234062070703A016304E8A2",
    val qrisImagePath: String = "",
    val isBluetoothAutoPrint: Boolean = false,
    val printerMacAddress: String = "",
    val printerName: String = "",
    val isAiAssistantEnabled: Boolean = true,
    val themeMode: String = "DARK", // "DARK", "SAGE", "LIGHT"
    val noiseCancellationLevel: Float = 0.8f,
    val voiceSensitivity: Float = 0.7f,
    val isSmartCurrencyFilterEnabled: Boolean = true,
    val googleServicesJson: String = "",
    val qrisBase64: String = ""
)
