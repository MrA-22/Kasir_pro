package com.example.util

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.example.data.model.Category
import com.example.data.model.Product
import com.example.data.model.Transaction
import com.example.data.model.TransactionItem
import com.example.data.model.StoreConfig
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

object FirebaseSyncManager {
    private const val TAG = "FirebaseSyncManager"
    private var isInitialized = false

    fun initialize(context: Context, jsonStr: String): Boolean {
        if (jsonStr.isBlank()) return false
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                isInitialized = true
                return true
            }

            val root = JSONObject(jsonStr)
            val projectInfo = root.getJSONObject("project_info")
            val projectId = projectInfo.getString("project_id")
            val storageBucket = projectInfo.optString("storage_bucket", "")
            
            val clients = root.getJSONArray("client")
            if (clients.length() == 0) return false
            
            val client = clients.getJSONObject(0)
            val clientInfo = client.getJSONObject("client_info")
            val appId = clientInfo.getString("mobilesdk_app_id")
            
            val apiKeys = client.getJSONArray("api_key")
            if (apiKeys.length() == 0) return false
            val apiKey = apiKeys.getJSONObject(0).getString("current_key")

            val options = FirebaseOptions.Builder()
                .setApiKey(apiKey)
                .setApplicationId(appId)
                .setProjectId(projectId)
                .setStorageBucket(storageBucket)
                .build()

            FirebaseApp.initializeApp(context, options)
            isInitialized = true
            Log.d(TAG, "Firebase initialized successfully with options!")
            
            // Persist JSON to internal app storage so it survives app lifecycle
            try {
                val persistentFile = java.io.File(context.filesDir, "google-services-pos.json")
                persistentFile.writeText(jsonStr)
                Log.d(TAG, "Successfully persisted google-services.json to internal storage!")
            } catch (err: Exception) {
                Log.e(TAG, "Failed to persist to internal storage: ${err.localizedMessage}")
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase: ${e.localizedMessage}")
            isInitialized = false
            return false
        }
    }

    fun isReady(): Boolean = isInitialized

    fun getFirestore(): FirebaseFirestore? {
        if (!isReady()) return null
        return try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    // --- SYNC OPERATIONS ---

    fun syncCategory(category: Category) {
        val db = getFirestore() ?: return
        val data = hashMapOf(
            "id" to category.id,
            "name" to category.name,
            "iconName" to category.iconName,
            "colorHex" to category.colorHex
        )
        db.collection("categories")
            .document(category.id.toString())
            .set(data)
            .addOnFailureListener { Log.e(TAG, "Failed to sync category: ${it.localizedMessage}") }
    }

    fun deleteCategory(categoryId: Long) {
        val db = getFirestore() ?: return
        db.collection("categories")
            .document(categoryId.toString())
            .delete()
    }

    fun syncProduct(product: Product) {
        val db = getFirestore() ?: return
        val data = hashMapOf(
            "id" to product.id,
            "name" to product.name,
            "categoryId" to product.categoryId,
            "categoryName" to product.categoryName,
            "price" to product.price,
            "costPrice" to product.costPrice,
            "stock" to product.stock,
            "minStockAlert" to product.minStockAlert,
            "unit" to product.unit,
            "barcode" to product.barcode,
            "isAvailable" to product.isAvailable,
            "updatedAt" to product.updatedAt
        )
        db.collection("products")
            .document(product.id.toString())
            .set(data)
            .addOnFailureListener { Log.e(TAG, "Failed to sync product: ${it.localizedMessage}") }
    }

    fun deleteProduct(productId: Long) {
        val db = getFirestore() ?: return
        db.collection("products")
            .document(productId.toString())
            .delete()
    }

    fun syncTransaction(transaction: Transaction, items: List<TransactionItem>) {
        val db = getFirestore() ?: return
        val itemsData = items.map { item ->
            hashMapOf(
                "productId" to item.productId,
                "productName" to item.productName,
                "categoryName" to item.categoryName,
                "quantity" to item.quantity,
                "pricePerUnit" to item.pricePerUnit,
                "subtotal" to item.subtotal
            )
        }
        val data = hashMapOf(
            "id" to transaction.id,
            "invoiceNumber" to transaction.invoiceNumber,
            "timestamp" to transaction.timestamp,
            "totalAmount" to transaction.totalAmount,
            "paymentMethod" to transaction.paymentMethod,
            "amountReceived" to transaction.amountReceived,
            "changeAmount" to transaction.changeAmount,
            "customerName" to transaction.customerName,
            "note" to transaction.note,
            "totalItemsCount" to transaction.totalItemsCount,
            "status" to transaction.status,
            "items" to itemsData
        )
        db.collection("transactions")
            .document(transaction.id.toString())
            .set(data)
            .addOnFailureListener { Log.e(TAG, "Failed to sync transaction: ${it.localizedMessage}") }
    }

    fun deleteTransaction(transactionId: Long) {
        val db = getFirestore() ?: return
        db.collection("transactions")
            .document(transactionId.toString())
            .delete()
    }

    fun syncStoreConfig(config: StoreConfig) {
        val db = getFirestore() ?: return
        val data = hashMapOf(
            "id" to config.id,
            "storeName" to config.storeName,
            "storeAddress" to config.storeAddress,
            "storePhone" to config.storePhone,
            "receiptFooter" to config.receiptFooter,
            "qrisMerchantName" to config.qrisMerchantName,
            "qrisPayload" to config.qrisPayload,
            "qrisImagePath" to config.qrisImagePath,
            "isBluetoothAutoPrint" to config.isBluetoothAutoPrint,
            "printerMacAddress" to config.printerMacAddress,
            "printerName" to config.printerName,
            "isAiAssistantEnabled" to config.isAiAssistantEnabled,
            "themeMode" to config.themeMode,
            "noiseCancellationLevel" to config.noiseCancellationLevel,
            "voiceSensitivity" to config.voiceSensitivity,
            "isSmartCurrencyFilterEnabled" to config.isSmartCurrencyFilterEnabled,
            "googleServicesJson" to config.googleServicesJson,
            "qrisBase64" to config.qrisBase64
        )
        db.collection("store_config")
            .document(config.id.toString())
            .set(data)
            .addOnFailureListener { Log.e(TAG, "Failed to sync store config: ${it.localizedMessage}") }
    }

    suspend fun syncAllDataWithCloud(context: Context, repository: com.example.data.repository.KasirRepository): Boolean {
        val db = getFirestore() ?: return false
        return try {
            val categoriesSnapshot = db.collection("categories").get().await()
            val cloudCategories = categoriesSnapshot.documents.mapNotNull { doc ->
                val id = doc.getLong("id") ?: return@mapNotNull null
                val name = doc.getString("name") ?: ""
                val iconName = doc.getString("iconName") ?: ""
                val colorHex = doc.getString("colorHex") ?: ""
                Category(id = id, name = name, iconName = iconName, colorHex = colorHex)
            }

            val productsSnapshot = db.collection("products").get().await()
            val cloudProducts = productsSnapshot.documents.mapNotNull { doc ->
                val id = doc.getLong("id") ?: return@mapNotNull null
                val name = doc.getString("name") ?: ""
                val categoryId = doc.getLong("categoryId") ?: 0L
                val categoryName = doc.getString("categoryName") ?: ""
                val price = doc.getDouble("price") ?: 0.0
                val costPrice = doc.getDouble("costPrice") ?: 0.0
                val stock = doc.getLong("stock")?.toInt() ?: 0
                val minStockAlert = doc.getLong("minStockAlert")?.toInt() ?: 5
                val unit = doc.getString("unit") ?: "Porsi"
                val barcode = doc.getString("barcode") ?: ""
                val isAvailable = doc.getBoolean("isAvailable") ?: true
                val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                
                Product(
                    id = id,
                    name = name,
                    categoryId = categoryId,
                    categoryName = categoryName,
                    price = price,
                    costPrice = costPrice,
                    stock = stock,
                    minStockAlert = minStockAlert,
                    unit = unit,
                    barcode = barcode,
                    isAvailable = isAvailable,
                    updatedAt = updatedAt
                )
            }

            val transactionsSnapshot = db.collection("transactions").get().await()
            val cloudTransactions = transactionsSnapshot.documents.mapNotNull { doc ->
                val id = doc.getLong("id") ?: return@mapNotNull null
                val invoiceNumber = doc.getString("invoiceNumber") ?: ""
                val timestamp = doc.getLong("timestamp") ?: 0L
                val totalAmount = doc.getDouble("totalAmount") ?: 0.0
                val paymentMethod = doc.getString("paymentMethod") ?: "TUNAI"
                val amountReceived = doc.getDouble("amountReceived") ?: 0.0
                val changeAmount = doc.getDouble("changeAmount") ?: 0.0
                val customerName = doc.getString("customerName") ?: ""
                val note = doc.getString("note") ?: ""
                val totalItemsCount = doc.getLong("totalItemsCount")?.toInt() ?: 0
                val status = doc.getString("status") ?: "COMPLETED"

                val transaction = Transaction(
                    id = id,
                    invoiceNumber = invoiceNumber,
                    timestamp = timestamp,
                    totalAmount = totalAmount,
                    paymentMethod = paymentMethod,
                    amountReceived = amountReceived,
                    changeAmount = changeAmount,
                    customerName = customerName,
                    note = note,
                    totalItemsCount = totalItemsCount,
                    status = status
                )

                val itemsList = mutableListOf<TransactionItem>()
                val itemsArray = doc.get("items") as? List<Map<String, Any>>
                if (itemsArray != null) {
                    for (itemMap in itemsArray) {
                        val prodId = (itemMap["productId"] as? Number)?.toLong() ?: 0L
                        val prodName = itemMap["productName"] as? String ?: ""
                        val catName = itemMap["categoryName"] as? String ?: ""
                        val qty = (itemMap["quantity"] as? Number)?.toInt() ?: 0
                        val pricePer = (itemMap["pricePerUnit"] as? Number)?.toDouble() ?: 0.0
                        val sub = (itemMap["subtotal"] as? Number)?.toDouble() ?: 0.0
                        
                        itemsList.add(
                            TransactionItem(
                                transactionId = id,
                                productId = prodId,
                                productName = prodName,
                                categoryName = catName,
                                quantity = qty,
                                pricePerUnit = pricePer,
                                subtotal = sub
                            )
                        )
                    }
                }
                Pair(transaction, itemsList)
            }

            // Sync Store Configuration
            val configSnapshot = db.collection("store_config").document("1").get().await()
            if (configSnapshot.exists()) {
                val storeName = configSnapshot.getString("storeName") ?: "Warung Makan"
                val storeAddress = configSnapshot.getString("storeAddress") ?: "Jl. Raya Barokah No. 88"
                val storePhone = configSnapshot.getString("storePhone") ?: "0812-3456-7890"
                val receiptFooter = configSnapshot.getString("receiptFooter") ?: ""
                val qrisMerchantName = configSnapshot.getString("qrisMerchantName") ?: ""
                val qrisPayload = configSnapshot.getString("qrisPayload") ?: ""
                val isBluetoothAutoPrint = configSnapshot.getBoolean("isBluetoothAutoPrint") ?: false
                val printerMacAddress = configSnapshot.getString("printerMacAddress") ?: ""
                val printerName = configSnapshot.getString("printerName") ?: ""
                val isAiAssistantEnabled = configSnapshot.getBoolean("isAiAssistantEnabled") ?: true
                val themeMode = configSnapshot.getString("themeMode") ?: "DARK"
                val noiseCancellationLevel = configSnapshot.getDouble("noiseCancellationLevel")?.toFloat() ?: 0.8f
                val voiceSensitivity = configSnapshot.getDouble("voiceSensitivity")?.toFloat() ?: 0.7f
                val isSmartCurrencyFilterEnabled = configSnapshot.getBoolean("isSmartCurrencyFilterEnabled") ?: true
                val googleServicesJson = configSnapshot.getString("googleServicesJson") ?: ""
                val qrisBase64 = configSnapshot.getString("qrisBase64") ?: ""

                var localQrisPath = ""
                if (qrisBase64.isNotBlank()) {
                    try {
                        val qrisFile = java.io.File(context.filesDir, "qris_merchant_code.png")
                        val bytes = android.util.Base64.decode(qrisBase64, android.util.Base64.NO_WRAP)
                        qrisFile.writeBytes(bytes)
                        localQrisPath = qrisFile.absolutePath
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val storeConfig = StoreConfig(
                    id = 1,
                    storeName = storeName,
                    storeAddress = storeAddress,
                    storePhone = storePhone,
                    receiptFooter = receiptFooter,
                    qrisMerchantName = qrisMerchantName,
                    qrisPayload = qrisPayload,
                    qrisImagePath = localQrisPath,
                    isBluetoothAutoPrint = isBluetoothAutoPrint,
                    printerMacAddress = printerMacAddress,
                    printerName = printerName,
                    isAiAssistantEnabled = isAiAssistantEnabled,
                    themeMode = themeMode,
                    noiseCancellationLevel = noiseCancellationLevel,
                    voiceSensitivity = voiceSensitivity,
                    isSmartCurrencyFilterEnabled = isSmartCurrencyFilterEnabled,
                    googleServicesJson = googleServicesJson,
                    qrisBase64 = qrisBase64
                )
                repository.updateStoreConfig(storeConfig)
            } else {
                repository.getStoreConfigSync()?.let { localCfg ->
                    var configToSync = localCfg
                    if (localCfg.qrisImagePath.isNotBlank()) {
                        try {
                            val qrisFile = java.io.File(localCfg.qrisImagePath)
                            if (qrisFile.exists()) {
                                val bytes = qrisFile.readBytes()
                                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                configToSync = localCfg.copy(qrisBase64 = base64)
                                repository.updateStoreConfig(configToSync)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    syncStoreConfig(configToSync)
                }
            }

            if (cloudCategories.isNotEmpty() || cloudProducts.isNotEmpty() || cloudTransactions.isNotEmpty()) {
                repository.deleteAllData()
                
                for (cat in cloudCategories) {
                    repository.insertCategory(cat)
                }
                for (prod in cloudProducts) {
                    repository.insertProduct(prod)
                }
                for (pair in cloudTransactions) {
                    repository.createTransaction(pair.first, pair.second)
                }
                true
            } else {
                val localCats = repository.allCategories.firstOrNull() ?: emptyList()
                val localProds = repository.allProducts.firstOrNull() ?: emptyList()
                val localTxs = repository.allTransactions.firstOrNull() ?: emptyList()
                
                for (cat in localCats) {
                    syncCategory(cat)
                }
                for (prod in localProds) {
                    syncProduct(prod)
                }
                for (txWithItems in localTxs) {
                    syncTransaction(txWithItems.transaction, txWithItems.items)
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.localizedMessage}")
            false
        }
    }

    data class LicenseRequestRecord(
        val deviceId: String,
        val storeName: String,
        val requestedAt: Long
    )

    fun logLicenseRequest(deviceId: String, storeName: String) {
        val db = getFirestore() ?: return
        val normalizedId = LicenseManager.extractDeviceIdFromText(deviceId) ?: deviceId.trim().uppercase()
        if (normalizedId.isBlank()) return
        val data = hashMapOf(
            "deviceId" to normalizedId,
            "storeName" to storeName.trim(),
            "requestedAt" to System.currentTimeMillis(),
            "status" to "PENDING"
        )
        db.collection("license_requests")
            .document(normalizedId)
            .set(data)
            .addOnFailureListener { Log.e(TAG, "Failed to log license request: ${it.localizedMessage}") }
    }

    suspend fun fetchRecentLicenseRequests(limitCount: Long = 20): List<LicenseRequestRecord> {
        val db = getFirestore() ?: return emptyList()
        return try {
            val snap = db.collection("license_requests")
                .orderBy("requestedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limitCount)
                .get()
                .await()
            snap.documents.mapNotNull { doc ->
                val id = doc.getString("deviceId")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                LicenseRequestRecord(
                    deviceId = id,
                    storeName = doc.getString("storeName") ?: "",
                    requestedAt = doc.getLong("requestedAt") ?: 0L
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch license requests: ${e.localizedMessage}")
            emptyList()
        }
    }
}
