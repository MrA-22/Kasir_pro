package com.example.data.repository

import com.example.data.dao.CategoryDao
import com.example.data.dao.ProductDao
import com.example.data.dao.StoreConfigDao
import com.example.data.dao.TransactionDao
import com.example.data.model.Category
import com.example.data.model.Product
import com.example.data.model.StoreConfig
import com.example.data.model.Transaction
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionWithItems
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicLong

class KasirRepository(
    private val categoryDao: CategoryDao,
    private val productDao: ProductDao,
    private val transactionDao: TransactionDao,
    private val storeConfigDao: StoreConfigDao
) {
    private val lastGeneratedId = AtomicLong(0L)

    fun nextUniqueCloudId(): Long {
        while (true) {
            val prev = lastGeneratedId.get()
            val candidate = maxOf(System.currentTimeMillis(), prev + 1L)
            if (lastGeneratedId.compareAndSet(prev, candidate)) {
                return candidate
            }
        }
    }

    // Categories
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()

    suspend fun insertCategory(category: Category): Long {
        val target = if (category.id == 0L && com.example.util.FirebaseSyncManager.isReady()) {
            category.copy(id = nextUniqueCloudId())
        } else category
        val id = categoryDao.insertCategory(target)
        val synced = if (target.id == 0L) target.copy(id = id) else target
        com.example.util.FirebaseSyncManager.syncCategory(synced)
        return id
    }

    suspend fun replaceCategoriesFromCloud(categories: List<Category>) {
        categoryDao.deleteAllCategories()
        if (categories.isNotEmpty()) {
            categoryDao.insertCategories(categories)
        }
    }

    suspend fun updateCategory(category: Category) {
        categoryDao.updateCategory(category)
        com.example.util.FirebaseSyncManager.syncCategory(category)
    }

    suspend fun deleteCategory(category: Category) {
        categoryDao.deleteCategory(category)
        com.example.util.FirebaseSyncManager.deleteCategory(category.id)
    }

    suspend fun deleteCategoryById(id: Long) {
        categoryDao.deleteCategoryById(id)
        com.example.util.FirebaseSyncManager.deleteCategory(id)
    }

    // Products
    val allProducts: Flow<List<Product>> = productDao.getAllProducts()
    val lowStockProducts: Flow<List<Product>> = productDao.getLowStockProducts()

    fun getProductsByCategory(categoryId: Long): Flow<List<Product>> =
        productDao.getProductsByCategory(categoryId)

    suspend fun getProductById(id: Long): Product? = productDao.getProductById(id)

    suspend fun insertProduct(product: Product): Long {
        val target = if (product.id == 0L && com.example.util.FirebaseSyncManager.isReady()) {
            product.copy(id = nextUniqueCloudId())
        } else product
        val id = productDao.insertProduct(target)
        val synced = if (target.id == 0L) target.copy(id = id) else target
        com.example.util.FirebaseSyncManager.syncProduct(synced)
        return id
    }

    suspend fun replaceProductsFromCloud(products: List<Product>) {
        productDao.deleteAllProducts()
        if (products.isNotEmpty()) {
            productDao.insertProducts(products)
        }
    }

    suspend fun updateProduct(product: Product) {
        productDao.updateProduct(product)
        com.example.util.FirebaseSyncManager.syncProduct(product)
    }

    suspend fun updateStock(productId: Long, newStock: Int) {
        productDao.updateStock(productId, newStock)
        productDao.getProductById(productId)?.let {
            com.example.util.FirebaseSyncManager.syncProduct(it)
        }
    }

    suspend fun deleteProduct(product: Product) {
        productDao.deleteProduct(product)
        com.example.util.FirebaseSyncManager.deleteProduct(product.id)
    }

    suspend fun deleteProductById(id: Long) {
        productDao.deleteProductById(id)
        com.example.util.FirebaseSyncManager.deleteProduct(id)
    }

    // Transactions
    val allTransactions: Flow<List<TransactionWithItems>> =
        transactionDao.getAllTransactionsWithItems()

    fun getTransactionsBetween(start: Long, end: Long): Flow<List<TransactionWithItems>> =
        transactionDao.getTransactionsBetween(start, end)

    suspend fun getTransactionById(id: Long): TransactionWithItems? =
        transactionDao.getTransactionWithItemsById(id)

    suspend fun getTransactionByInvoice(invoiceNumber: String): TransactionWithItems? =
        transactionDao.getTransactionWithItemsByInvoice(invoiceNumber)

    suspend fun deleteTransaction(id: Long, restoreStock: Boolean = true) {
        if (restoreStock) {
            val tx = transactionDao.getTransactionWithItemsById(id)
            if (tx != null) {
                for (item in tx.items) {
                    if (item.productId > 0) {
                        productDao.increaseStock(item.productId, item.quantity)
                        productDao.getProductById(item.productId)?.let {
                            com.example.util.FirebaseSyncManager.syncProduct(it)
                        }
                    }
                }
            }
        }
        transactionDao.deleteTransactionById(id)
        com.example.util.FirebaseSyncManager.deleteTransaction(id)
    }

    suspend fun deleteAllData() {
        transactionDao.deleteAllTransactionItems()
        transactionDao.deleteAllTransactions()
        productDao.deleteAllProducts()
        categoryDao.deleteAllCategories()
    }

    suspend fun createTransaction(
        transaction: Transaction,
        items: List<TransactionItem>
    ): Long {
        val targetTx = if (transaction.id == 0L && com.example.util.FirebaseSyncManager.isReady()) {
            transaction.copy(id = nextUniqueCloudId())
        } else transaction
        val txId = transactionDao.insertTransaction(targetTx)
        val itemsWithTxId = items.map { it.copy(transactionId = txId) }
        transactionDao.insertTransactionItems(itemsWithTxId)

        // Automatically deduct stock
        for (item in items) {
            if (item.productId > 0) {
                productDao.reduceStock(item.productId, item.quantity)
                productDao.getProductById(item.productId)?.let {
                    com.example.util.FirebaseSyncManager.syncProduct(it)
                }
            }
        }
        val finalTx = if (targetTx.id == 0L) targetTx.copy(id = txId) else targetTx
        com.example.util.FirebaseSyncManager.syncTransaction(finalTx, itemsWithTxId)
        return txId
    }

    suspend fun replaceTransactionsFromCloud(transactions: List<Pair<Transaction, List<TransactionItem>>>) {
        transactionDao.deleteAllTransactionItems()
        transactionDao.deleteAllTransactions()
        for ((tx, items) in transactions) {
            val insertedId = transactionDao.insertTransaction(tx)
            val resolvedId = if (tx.id == 0L) insertedId else tx.id
            if (items.isNotEmpty()) {
                transactionDao.insertTransactionItems(items.map { it.copy(transactionId = resolvedId) })
            }
        }
    }

    suspend fun getItemsBetween(start: Long, end: Long): List<TransactionItem> =
        transactionDao.getItemsBetween(start, end)

    // Store Configuration
    val storeConfig: Flow<StoreConfig?> = storeConfigDao.getStoreConfig()

    suspend fun getStoreConfigSync(): StoreConfig? = storeConfigDao.getStoreConfigSync()

    suspend fun updateStoreConfig(config: StoreConfig) {
        storeConfigDao.insertOrUpdateConfig(config)
        com.example.util.FirebaseSyncManager.syncStoreConfig(config)
    }

    suspend fun updateStoreConfigLocalOnly(config: StoreConfig) {
        storeConfigDao.insertOrUpdateConfig(config)
    }
}
