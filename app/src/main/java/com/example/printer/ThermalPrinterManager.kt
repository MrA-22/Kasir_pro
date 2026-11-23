package com.example.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.example.data.model.StoreConfig
import com.example.data.model.Transaction
import com.example.data.model.TransactionItem
import com.example.util.Code128Encoder
import com.example.util.CurrencyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.UUID

data class BluetoothDeviceItem(
    val name: String,
    val address: String,
    val isConnected: Boolean = false
)

class ThermalPrinterManager(private val context: Context) {

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDeviceItem> {
        return try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
            if (!bluetoothAdapter.isEnabled) return emptyList()

            bluetoothAdapter.bondedDevices.map { device ->
                BluetoothDeviceItem(
                    name = device.name ?: "Unknown Printer",
                    address = device.address
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun printReceipt(
        storeConfig: StoreConfig,
        transaction: Transaction,
        items: List<TransactionItem>
    ): Result<String> = withContext(Dispatchers.IO) {
        val targetMac = storeConfig.printerMacAddress
        if (targetMac.isBlank()) {
            return@withContext Result.failure(Exception("Alamat MAC Printer belum dipilih di Pengaturan"))
        }

        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(Exception("Bluetooth tidak tersedia di perangkat"))

            if (!bluetoothAdapter.isEnabled) {
                return@withContext Result.failure(Exception("Bluetooth belum diaktifkan"))
            }

            val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(targetMac)
            val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(sppUuid)

            socket.connect()
            val outputStream: OutputStream = socket.outputStream

            val escPosBytes = generateEscPosReceipt(storeConfig, transaction, items)
            outputStream.write(escPosBytes)
            outputStream.flush()

            socket.close()
            Result.success("Struk berhasil dikirim ke printer!")
        } catch (e: Exception) {
            Result.failure(Exception("Gagal mencetak ke printer: ${e.localizedMessage}"))
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun testPrint(storeConfig: StoreConfig): Result<String> = withContext(Dispatchers.IO) {
        val targetMac = storeConfig.printerMacAddress
        if (targetMac.isBlank()) {
            return@withContext Result.failure(Exception("Alamat MAC Printer belum dipilih"))
        }

        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(Exception("Bluetooth tidak tersedia"))

            val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(targetMac)
            val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(sppUuid)

            socket.connect()
            val outputStream: OutputStream = socket.outputStream

            val escPosBytes = generateEscPosTest(storeConfig)
            outputStream.write(escPosBytes)
            outputStream.flush()

            socket.close()
            Result.success("Test print berhasil!")
        } catch (e: Exception) {
            Result.failure(Exception("Gagal test print: ${e.localizedMessage}"))
        }
    }

    private fun generateEscPosReceipt(
        storeConfig: StoreConfig,
        transaction: Transaction,
        items: List<TransactionItem>
    ): ByteArray {
        val stream = ByteArrayOutputStream()
        val charset = Charset.forName("GBK")

        // 1. Init
        stream.write(byteArrayOf(0x1B, 0x40))

        // 2. Store Header (Center, Double Size)
        stream.write(byteArrayOf(0x1B, 0x61, 0x01)) // Center
        stream.write(byteArrayOf(0x1D, 0x21, 0x11)) // Double size
        stream.write("${storeConfig.storeName.uppercase()}\n".toByteArray(charset))

        // Store Address & Phone (Normal)
        stream.write(byteArrayOf(0x1D, 0x21, 0x00))
        if (storeConfig.storeAddress.isNotBlank()) {
            stream.write("${storeConfig.storeAddress}\n".toByteArray(charset))
        }
        if (storeConfig.storePhone.isNotBlank()) {
            stream.write("Telp / WA: ${storeConfig.storePhone}\n".toByteArray(charset))
        }

        // Separator
        stream.write("- - - - - - - - - - - - - - - - \n".toByteArray(charset))

        // 3. Transaction Meta (Left align)
        stream.write(byteArrayOf(0x1B, 0x61, 0x00))
        stream.write(byteArrayOf(0x1B, 0x45, 0x01)) // Bold on
        stream.write("No. Nota : ${transaction.invoiceNumber}\n".toByteArray(charset))
        stream.write(byteArrayOf(0x1B, 0x45, 0x00)) // Bold off
        stream.write("Tanggal : ${CurrencyUtils.formatDateTime(transaction.timestamp)}\n".toByteArray(charset))
        val paymentMethodStr = if (transaction.paymentMethod == "TUNAI") "Tunai" else "QRIS"
        stream.write("Metode  : $paymentMethodStr\n".toByteArray(charset))
        if (transaction.customerName.isNotBlank()) {
            stream.write("Pelanggan : ${transaction.customerName}\n".toByteArray(charset))
        }

        stream.write("- - - - - - - - - - - - - - - - \n".toByteArray(charset))

        // 4. Column Header
        stream.write(byteArrayOf(0x1B, 0x45, 0x01))
        stream.write("${formatTwoColumns("PRODUK / ITEM", "SUBTOTAL", 32)}\n".toByteArray(charset))
        stream.write(byteArrayOf(0x1B, 0x45, 0x00))
        stream.write("- - - - - - - - - - - - - - - - \n".toByteArray(charset))

        // 5. Items (32 columns formatting)
        for (item in items) {
            val name = item.productName.uppercase()
            val truncatedName = if (name.length > 20) name.substring(0, 18) + ".." else name
            val subtotal = CurrencyUtils.formatRupiah(item.subtotal)
            stream.write(byteArrayOf(0x1B, 0x45, 0x01)) // Bold item name
            stream.write("${formatTwoColumns(truncatedName, subtotal, 32)}\n".toByteArray(charset))
            stream.write(byteArrayOf(0x1B, 0x45, 0x00))

            val qtyPrice = " ${item.quantity} x ${CurrencyUtils.formatRupiah(item.pricePerUnit)}"
            if (item.discountValue > 0) {
                val discText = if (item.isDiscountPercentage) "Disc ${item.discountValue.toInt()}%" else "D-${CurrencyUtils.formatRupiah(item.discountValue)}"
                stream.write("${formatTwoColumns(qtyPrice, "($discText)", 32)}\n".toByteArray(charset))
            } else {
                stream.write("$qtyPrice\n".toByteArray(charset))
            }
        }

        stream.write("- - - - - - - - - - - - - - - - \n".toByteArray(charset))

        // 6. Totals
        val totalLine = formatTwoColumns("TOTAL TRANSAKSI", CurrencyUtils.formatRupiah(transaction.totalAmount), 32)
        stream.write(byteArrayOf(0x1B, 0x45, 0x01)) // Bold on
        stream.write("$totalLine\n".toByteArray(charset))
        stream.write(byteArrayOf(0x1B, 0x45, 0x00)) // Bold off

        if (transaction.paymentMethod == "TUNAI") {
            val bayarLine = formatTwoColumns("Tunai Diterima", CurrencyUtils.formatRupiah(transaction.amountReceived), 32)
            val kembaliLine = formatTwoColumns("Kembalian", CurrencyUtils.formatRupiah(transaction.changeAmount), 32)
            stream.write("$bayarLine\n".toByteArray(charset))
            stream.write(byteArrayOf(0x1B, 0x45, 0x01))
            stream.write("$kembaliLine\n".toByteArray(charset))
            stream.write(byteArrayOf(0x1B, 0x45, 0x00))
        } else {
            val qrisLine = formatTwoColumns("Status", "LUNAS (QRIS)", 32)
            stream.write("$qrisLine\n".toByteArray(charset))
        }

        stream.write("- - - - - - - - - - - - - - - - \n".toByteArray(charset))

        // 7. Footer Note & Scannable Code-128 Barcode (Center)
        stream.write(byteArrayOf(0x1B, 0x61, 0x01)) // Center
        val greeting = storeConfig.receiptFooter.ifBlank { "Terima kasih atas kunjungan Anda!" }
        stream.write("$greeting\n\n".toByteArray(charset))

        val barcodeText = Code128Encoder.sanitizeCode128BText(transaction.invoiceNumber)
        val barcodeDataBytes = barcodeText.toByteArray(Charsets.US_ASCII)
        val moduleWidth: Byte = if (barcodeDataBytes.size <= 12) 0x02 else 0x01
        stream.write(byteArrayOf(0x1D, 0x48, 0x02)) // HRI position below barcode
        stream.write(byteArrayOf(0x1D, 0x66, 0x00)) // Font A for HRI
        stream.write(byteArrayOf(0x1D, 0x68, 0x48)) // Barcode height 72 dots
        stream.write(byteArrayOf(0x1D, 0x77, moduleWidth)) // Barcode module width
        // Code-128 Set B prefix "{B" + ASCII payload
        val code128Len = (barcodeDataBytes.size + 2).coerceAtMost(255)
        stream.write(byteArrayOf(0x1D, 0x6B, 0x49, code128Len.toByte(), 0x7B, 0x42))
        stream.write(barcodeDataBytes, 0, code128Len - 2)
        stream.write("\n".toByteArray(charset))

        stream.write("Simpan nota ini sebagai bukti sah\n".toByteArray(charset))

        // Generous feed before cut/tear so bottom content is never cut off
        stream.write("\n\n\n\n\n".toByteArray(charset))
        stream.write(byteArrayOf(0x1B, 0x64, 0x06)) // ESC d 6 (feed 6 lines)
        stream.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00)) // GS V 66 0 (Cut after feeding)

        return stream.toByteArray()
    }

    private fun generateEscPosTest(storeConfig: StoreConfig): ByteArray {
        val stream = ByteArrayOutputStream()
        val charset = Charset.forName("GBK")

        stream.write(byteArrayOf(0x1B, 0x40))
        stream.write(byteArrayOf(0x1B, 0x61, 0x01))
        stream.write(byteArrayOf(0x1D, 0x21, 0x11))
        stream.write("${storeConfig.storeName.uppercase()}\n".toByteArray(charset))
        stream.write(byteArrayOf(0x1D, 0x21, 0x00))
        stream.write("TES KONEKSI PRINTER THERMAL\n".toByteArray(charset))
        stream.write("- - - - - - - - - - - - - - - - \n".toByteArray(charset))
        stream.write("Status: BERHASIL TERHUBUNG!\n".toByteArray(charset))
        stream.write("Printer siap mencetak nota kasir.\n".toByteArray(charset))
        stream.write("- - - - - - - - - - - - - - - - \n".toByteArray(charset))
        stream.write("\n\n\n\n".toByteArray(charset))
        stream.write(byteArrayOf(0x1B, 0x64, 0x04))
        stream.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))

        return stream.toByteArray()
    }

    private fun formatTwoColumns(left: String, right: String, totalWidth: Int): String {
        val spacesNeeded = totalWidth - left.length - right.length
        return if (spacesNeeded > 0) {
            left + " ".repeat(spacesNeeded) + right
        } else {
            val truncatedLeft = if (left.length > (totalWidth - right.length - 1)) {
                left.substring(0, totalWidth - right.length - 1)
            } else left
            val sp = totalWidth - truncatedLeft.length - right.length
            truncatedLeft + " ".repeat(if (sp > 0) sp else 1) + right
        }
    }
}
