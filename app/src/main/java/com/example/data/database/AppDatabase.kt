package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.CategoryDao
import com.example.data.dao.ProductDao
import com.example.data.dao.StoreConfigDao
import com.example.data.dao.TransactionDao
import com.example.data.model.Category
import com.example.data.model.Product
import com.example.data.model.StoreConfig
import com.example.data.model.Transaction
import com.example.data.model.TransactionItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Category::class,
        Product::class,
        Transaction::class,
        TransactionItem::class,
        StoreConfig::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao
    abstract fun storeConfigDao(): StoreConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kasir_pro_database"
                )
                    .addCallback(DatabaseCallback(context, scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val context: Context,
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(context, database)
                    }
                }
            }
        }

        suspend fun populateInitialData(context: Context, database: AppDatabase) {
            val categoryDao = database.categoryDao()
            val productDao = database.productDao()
            val storeConfigDao = database.storeConfigDao()
            val transactionDao = database.transactionDao()

            // 1. Initial Store Config (with internal storage check for persistent google-services.json)
            val sdJson = try {
                val file = java.io.File(context.filesDir, "google-services-pos.json")
                if (file.exists()) file.readText() else ""
            } catch (e: Exception) { "" }

            storeConfigDao.insertOrUpdateConfig(
                StoreConfig(
                    id = 1,
                    storeName = "Warung Makan",
                    storeAddress = "Jl. Merdeka No. 45, Jakarta",
                    storePhone = "0812-3456-7890",
                    receiptFooter = "Terima kasih atas kunjungan Anda!\nSemoga berkah & datang kembali.",
                    qrisMerchantName = "WARUNG MAKAN / KASIR PRO",
                    isBluetoothAutoPrint = false,
                    themeMode = "DARK",
                    googleServicesJson = sdJson
                )
            )

            // 2. Initial Categories
            val cat1 = Category(id = 1, name = "Makanan Utama", iconName = "restaurant", colorHex = "#FF6B4A")
            val cat2 = Category(id = 2, name = "Minuman", iconName = "local_cafe", colorHex = "#4ECDC4")
            val cat3 = Category(id = 3, name = "Camilan", iconName = "cookie", colorHex = "#FFD166")
            val cat4 = Category(id = 4, name = "Lain-lain", iconName = "category", colorHex = "#118AB2")

            categoryDao.insertCategories(listOf(cat1, cat2, cat3, cat4))

            // 3. Initial Products
            val p1 = Product(id = 1, name = "PISANG", categoryId = 3, categoryName = "Camilan", price = 5000.0, stock = 25, minStockAlert = 5, unit = "Porsi")
            val p2 = Product(id = 2, name = "Nasi Goreng Spesial", categoryId = 1, categoryName = "Makanan Utama", price = 20000.0, stock = 30, minStockAlert = 5, unit = "Porsi")
            val p3 = Product(id = 3, name = "Ayam Bakar Madu", categoryId = 1, categoryName = "Makanan Utama", price = 25000.0, stock = 20, minStockAlert = 5, unit = "Porsi")
            val p4 = Product(id = 4, name = "Es Teh Manis", categoryId = 2, categoryName = "Minuman", price = 5000.0, stock = 50, minStockAlert = 10, unit = "Gelas")
            val p5 = Product(id = 5, name = "Kopi Susu Gula Aren", categoryId = 2, categoryName = "Minuman", price = 12000.0, stock = 40, minStockAlert = 8, unit = "Cup")
            val p6 = Product(id = 6, name = "Tempe Mendoan", categoryId = 3, categoryName = "Camilan", price = 10000.0, stock = 15, minStockAlert = 5, unit = "Porsi")

            productDao.insertProducts(listOf(p1, p2, p3, p4, p5, p6))

            // 4. Initial Sample Transaction for Live Dashboard view matching user's initial state
            val currentTime = System.currentTimeMillis()
            val sampleTx = Transaction(
                id = 1,
                invoiceNumber = "INV-20260830-001",
                timestamp = currentTime - (15 * 60 * 1000), // 15 mins ago
                totalAmount = 35000.0,
                paymentMethod = "QRIS",
                amountReceived = 35000.0,
                changeAmount = 0.0,
                customerName = "Pelanggan 1",
                note = "",
                totalItemsCount = 7,
                status = "COMPLETED"
            )
            val txId = transactionDao.insertTransaction(sampleTx)
            val item1 = TransactionItem(
                transactionId = txId,
                productId = 1,
                productName = "PISANG",
                categoryName = "Camilan",
                quantity = 7,
                pricePerUnit = 5000.0,
                subtotal = 35000.0
            )
            transactionDao.insertTransactionItems(listOf(item1))
        }
    }
}
