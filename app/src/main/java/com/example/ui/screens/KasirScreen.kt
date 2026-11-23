package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Product
import com.example.ui.components.QrisDisplayDialog
import com.example.ui.theme.LocalCustomColors
import com.example.ui.theme.StatusQris
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusTunai
import com.example.ui.theme.StatusWarning
import com.example.ui.viewmodel.KasirViewModel
import com.example.util.CurrencyUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KasirScreen(
    viewModel: KasirViewModel,
    onNavigateBack: () -> Unit
) {
    val products by viewModel.allProducts.collectAsStateWithLifecycle()
    val categories by viewModel.allCategories.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val cartTotal by viewModel.cartTotal.collectAsStateWithLifecycle()
    val cartItemCount by viewModel.cartItemCount.collectAsStateWithLifecycle()
    val storeConfig by viewModel.storeConfig.collectAsStateWithLifecycle()
    val customColors = LocalCustomColors.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableLongStateOf(0L) } // 0 = Semua

    var isCartSheetOpen by remember { mutableStateOf(false) }
    var isQrisDialogOpen by remember { mutableStateOf(false) }

    // Discount states
    var showDiscountDialog by remember { mutableStateOf(false) }
    var selectedItemForDiscount by remember { mutableStateOf<com.example.ui.viewmodel.CartItem?>(null) }
    var discountInputValue by remember { mutableStateOf("") }
    var isDiscountPercentageInput by remember { mutableStateOf(false) }

    // Payment form states
    var selectedPaymentMethod by remember { mutableStateOf("TUNAI") } // "TUNAI" or "QRIS"
    var cashReceivedInput by remember { mutableStateOf("") }
    var customerNameInput by remember { mutableStateOf("") }
    var orderNoteInput by remember { mutableStateOf("") }

    // Listen to AI Checkout trigger
    LaunchedEffect(Unit) {
        viewModel.aiCheckoutTrigger.collect { (method, amount) ->
            if (cartItemCount > 0) {
                selectedPaymentMethod = method
                if (amount > 0) {
                    cashReceivedInput = amount.toLong().toString()
                }
                isCartSheetOpen = true
            } else {
                viewModel.emitToast("Keranjang kosong, tidak bisa checkout.")
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.qrisPaymentTrigger.collect {
            if (cartItemCount > 0) {
                isQrisDialogOpen = true
            } else {
                viewModel.emitToast("Keranjang kosong, tidak ada QRIS untuk dibayar.")
            }
        }
    }

    val filteredProducts = remember(products, searchQuery, selectedCategoryId) {
        products.filter { prod ->
            val matchCategory = if (selectedCategoryId == 0L) true else prod.categoryId == selectedCategoryId
            val matchSearch = if (searchQuery.isBlank()) true else prod.name.contains(searchQuery, ignoreCase = true)
            matchCategory && matchSearch
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isTablet = maxWidth >= 720.dp

        if (isTablet) {
            // Tablet 2-Pane POS Layout
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Pane: Catalog & Products
                Column(
                    modifier = Modifier
                        .weight(1.35f)
                        .fillMaxHeight()
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PointOfSale,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Kasir Penjualan",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        Text(
                            text = "${filteredProducts.size} Produk Tersedia",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag("search_product_input"),
                        placeholder = { Text("Cari menu / item...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Cari",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Hapus",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = customColors.borderStroke,
                            focusedContainerColor = customColors.heroCardBg,
                            unfocusedContainerColor = customColors.heroCardBg
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Category Filter Horizontal Row
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategoryId == 0L,
                                onClick = { selectedCategoryId = 0L },
                                label = { Text("Semua") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = customColors.chipSelectedBg,
                                    selectedLabelColor = Color.White,
                                    containerColor = customColors.subCardBg,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }

                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = { selectedCategoryId = cat.id },
                                label = { Text(cat.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = customColors.chipSelectedBg,
                                    selectedLabelColor = Color.White,
                                    containerColor = customColors.subCardBg,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Products Grid Adaptive
                    if (filteredProducts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Tidak ada produk ditemukan.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 140.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(filteredProducts, key = { it.id }) { product ->
                                val cartItem = cart[product.id]
                                val inCartQty = cartItem?.quantity ?: 0
                                val isOutOfStock = product.stock <= 0

                                ProductPosCard(
                                    product = product,
                                    inCartQty = inCartQty,
                                    isOutOfStock = isOutOfStock,
                                    bgColor = customColors.heroCardBg,
                                    borderStroke = customColors.borderStroke,
                                    onCardClick = {
                                        if (isOutOfStock) {
                                            viewModel.emitToast("Stok ${product.name} habis! Tambahkan stok terlebih dahulu.")
                                        } else {
                                            viewModel.addToCart(product)
                                        }
                                    },
                                    onIncrement = { viewModel.addToCart(product) },
                                    onDecrement = { viewModel.decrementFromCart(product) }
                                )
                            }
                        }
                    }
                }

                // Pane Divider
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(customColors.borderStroke)
                )

                // Right Pane: Dedicated POS Cart & Checkout Terminal
                Column(
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight()
                        .background(customColors.heroCardBg)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Keranjang Pesanan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (cartItemCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(customColors.actionPrimaryBg)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$cartItemCount",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        if (cartItemCount > 0) {
                            OutlinedButton(
                                onClick = { viewModel.clearCart() },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Kosongkan", fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (cart.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Keranjang Masih Kosong",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Pilih menu di katalog untuk menambahkan pesanan.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                                )
                            }
                        }
                    } else {
                        // Cart Items Scrollable List
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(cart.values.toList(), key = { it.product.id }) { cartItem ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(customColors.subCardBg)
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cartItem.product.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${CurrencyUtils.formatRupiah(cartItem.product.price)} x ${cartItem.quantity}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (cartItem.discountValue > 0) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                val discText = if (cartItem.isDiscountPercentage) "-${cartItem.discountValue.toInt()}%" else "-${CurrencyUtils.formatRupiah(cartItem.discountValue)}"
                                                Text(
                                                    text = discText,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = StatusWarning
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (cartItem.discountValue > 0) "Ubah Diskon" else "+ Diskon",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clickable {
                                                    selectedItemForDiscount = cartItem
                                                    discountInputValue = if (cartItem.discountValue > 0) cartItem.discountValue.toInt().toString() else ""
                                                    isDiscountPercentageInput = cartItem.isDiscountPercentage
                                                    showDiscountDialog = true
                                                }
                                                .padding(top = 2.dp)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { viewModel.decrementFromCart(cartItem.product) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (cartItem.quantity == 1) Icons.Default.Delete else Icons.Default.Remove,
                                                contentDescription = "Kurang",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Text(
                                            text = "${cartItem.quantity}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        IconButton(
                                            onClick = { viewModel.addToCart(cartItem.product) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Tambah",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = CurrencyUtils.formatRupiah(cartItem.subtotal),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.widthIn(min = 82.dp),
                                        textAlign = TextAlign.End,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Total Box
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(customColors.subCardBg)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total Tagihan",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = CurrencyUtils.formatRupiah(cartTotal),
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = customColors.heroAmountColor
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                customerNameInput = ""
                                cashReceivedInput = ""
                                isCartSheetOpen = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = customColors.actionPrimaryBg
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("checkout_tablet_button")
                        ) {
                            Text("Bayar Sekarang", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // Phone Single-Column Layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (cartItemCount > 0) 88.dp else 12.dp)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PointOfSale,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kasir Penjualan",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (cartItemCount > 0) {
                        OutlinedButton(
                            onClick = { viewModel.clearCart() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Kosongkan", fontSize = 11.sp)
                        }
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag("search_product_input"),
                    placeholder = { Text("Cari menu / item...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Cari",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Hapus",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = customColors.borderStroke,
                        focusedContainerColor = customColors.heroCardBg,
                        unfocusedContainerColor = customColors.heroCardBg
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Category Filter Horizontal Row
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId == 0L,
                            onClick = { selectedCategoryId = 0L },
                            label = { Text("Semua") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = customColors.chipSelectedBg,
                                selectedLabelColor = Color.White,
                                containerColor = customColors.subCardBg,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = customColors.chipSelectedBg,
                                selectedLabelColor = Color.White,
                                containerColor = customColors.subCardBg,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Products Grid
                if (filteredProducts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tidak ada produk ditemukan.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredProducts, key = { it.id }) { product ->
                            val cartItem = cart[product.id]
                            val inCartQty = cartItem?.quantity ?: 0
                            val isOutOfStock = product.stock <= 0

                            ProductPosCard(
                                product = product,
                                inCartQty = inCartQty,
                                isOutOfStock = isOutOfStock,
                                bgColor = customColors.heroCardBg,
                                borderStroke = customColors.borderStroke,
                                onCardClick = {
                                    if (isOutOfStock) {
                                        viewModel.emitToast("Stok ${product.name} habis! Tambahkan stok terlebih dahulu.")
                                    } else {
                                        viewModel.addToCart(product)
                                    }
                                },
                                onIncrement = { viewModel.addToCart(product) },
                                onDecrement = { viewModel.decrementFromCart(product) }
                            )
                        }
                    }
                }
            }

            // Bottom Floating Cart Bar (Phone Only)
            if (cartItemCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = customColors.heroCardBg,
                    tonalElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, customColors.borderStroke)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { isCartSheetOpen = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(customColors.actionPrimaryBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = "Keranjang",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "$cartItemCount Item",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = CurrencyUtils.formatRupiah(cartTotal),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = customColors.heroAmountColor
                                )
                            }
                        }

                        Button(
                            onClick = { isCartSheetOpen = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = customColors.actionPrimaryBg
                            ),
                            modifier = Modifier.testTag("checkout_button")
                        ) {
                            Text("Bayar", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Checkout Bottom Sheet
    if (isCartSheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()

        ModalBottomSheet(
            onDismissRequest = { isCartSheetOpen = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                // Sheet Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ringkasan Pembayaran",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { isCartSheetOpen = false }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Cart items list
                cart.values.forEach { cartItem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cartItem.product.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${CurrencyUtils.formatRupiah(cartItem.product.price)} x ${cartItem.quantity}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (cartItem.discountValue > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val discText = if (cartItem.isDiscountPercentage) "-${cartItem.discountValue.toInt()}%" else "-${CurrencyUtils.formatRupiah(cartItem.discountValue)}"
                                    Text(
                                        text = discText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusWarning,
                                        modifier = Modifier
                                            .background(StatusWarning.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (cartItem.discountValue > 0) "Ubah Diskon" else "Beri Diskon",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable {
                                        selectedItemForDiscount = cartItem
                                        discountInputValue = if (cartItem.discountValue > 0) cartItem.discountValue.toInt().toString() else ""
                                        isDiscountPercentageInput = cartItem.isDiscountPercentage
                                        showDiscountDialog = true
                                    }
                                    .padding(top = 4.dp)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.decrementFromCart(cartItem.product) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (cartItem.quantity == 1) Icons.Default.Delete else Icons.Default.Remove,
                                    contentDescription = "Kurang",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "${cartItem.quantity}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = { viewModel.addToCart(cartItem.product) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Tambah",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Text(
                            text = CurrencyUtils.formatRupiah(cartItem.subtotal),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(80.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Total Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(customColors.heroCardBg)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total Tagihan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = CurrencyUtils.formatRupiah(cartTotal),
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = customColors.heroAmountColor
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Customer Name (Optional)
                OutlinedTextField(
                    value = customerNameInput,
                    onValueChange = { customerNameInput = it },
                    label = { Text("Nama Pelanggan (Opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Payment Method Selector (Tunai vs QRIS)
                Text(
                    text = "Metode Pembayaran",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tunai Option
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedPaymentMethod = "TUNAI" },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedPaymentMethod == "TUNAI") customColors.actionPrimaryBg.copy(alpha = 0.2f) else customColors.heroCardBg
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            if (selectedPaymentMethod == "TUNAI") 2.dp else 1.dp,
                            if (selectedPaymentMethod == "TUNAI") customColors.actionPrimaryBg else customColors.borderStroke
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PointOfSale,
                                contentDescription = null,
                                tint = if (selectedPaymentMethod == "TUNAI") customColors.actionPrimaryBg else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tunai / Cash",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // QRIS Option
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedPaymentMethod = "QRIS" },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedPaymentMethod == "QRIS") StatusQris.copy(alpha = 0.2f) else customColors.heroCardBg
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            if (selectedPaymentMethod == "QRIS") 2.dp else 1.dp,
                            if (selectedPaymentMethod == "QRIS") StatusQris else customColors.borderStroke
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                tint = if (selectedPaymentMethod == "QRIS") StatusQris else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "QRIS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // If TUNAI is selected: Nominal received + Quick buttons + Kembalian
                if (selectedPaymentMethod == "TUNAI") {
                    val cashReceived = cashReceivedInput.toDoubleOrNull() ?: 0.0
                    val change = if (cashReceived >= cartTotal) cashReceived - cartTotal else 0.0

                    OutlinedTextField(
                        value = cashReceivedInput,
                        onValueChange = { cashReceivedInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Jumlah Uang Diterima") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cash_received_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = { Text("Rp", modifier = Modifier.padding(start = 12.dp), fontWeight = FontWeight.Bold) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Nominal Buttons: Pas, 10k, 20k, 50k, 100k, 200k
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            OutlinedButton(
                                onClick = { cashReceivedInput = cartTotal.toLong().toString() },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Uang Pas", fontSize = 12.sp)
                            }
                        }
                        listOf(10000L, 20000L, 50000L, 100000L, 200000L, 500000L).forEach { nom ->
                            if (nom >= cartTotal || nom == 100000L || nom == 50000L) {
                                item {
                                    OutlinedButton(
                                        onClick = { cashReceivedInput = nom.toString() },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("${nom / 1000}k", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Kembalian Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (cashReceived >= cartTotal) Color(0xFF1E3A20) else Color(0xFF3D2E14)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Kembalian",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            Text(
                                text = if (cashReceived >= cartTotal) CurrencyUtils.formatRupiah(change) else "Uang Kurang",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = if (cashReceived >= cartTotal) StatusSuccess else StatusWarning
                            )
                        }
                    }
                } else {
                    // QRIS preview
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isQrisDialogOpen = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusQris.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = null,
                                    tint = StatusQris,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = storeConfig?.qrisMerchantName ?: "QRIS Toko",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Ketuk untuk tampilkan barcode",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = "Lihat QRIS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = StatusQris
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Selesai & Simpan Button
                Button(
                    onClick = {
                        val cash = if (selectedPaymentMethod == "TUNAI") {
                            cashReceivedInput.toDoubleOrNull() ?: 0.0
                        } else {
                            cartTotal
                        }

                        if (selectedPaymentMethod == "TUNAI" && cash < cartTotal) {
                            viewModel.emitToast("Uang yang diterima kurang dari total belanja!")
                            return@Button
                        }

                        viewModel.processCheckout(
                            paymentMethod = selectedPaymentMethod,
                            amountReceived = cash,
                            customerName = customerNameInput,
                            note = orderNoteInput,
                            onSuccess = {
                                scope.launch {
                                    isCartSheetOpen = false
                                }
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("submit_transaction_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = customColors.actionPrimaryBg
                    )
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Selesai & Simpan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // QRIS Full Display Dialog
    if (isQrisDialogOpen && storeConfig != null) {
        QrisDisplayDialog(
            amount = cartTotal,
            storeConfig = storeConfig!!,
            onDismiss = { isQrisDialogOpen = false },
            onConfirmPaid = {
                isQrisDialogOpen = false
                viewModel.processCheckout(
                    paymentMethod = "QRIS",
                    amountReceived = cartTotal,
                    customerName = customerNameInput,
                    note = orderNoteInput,
                    onSuccess = {
                        isCartSheetOpen = false
                    }
                )
            },
            onUploadNewQris = { uri ->
                viewModel.saveQrisImageUri(uri)
            }
        )
    }

    // Item Discount Dialog
    if (showDiscountDialog && selectedItemForDiscount != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDiscountDialog = false },
            title = { Text("Diskon Item: ${selectedItemForDiscount!!.product.name}") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.material3.FilterChip(
                            selected = !isDiscountPercentageInput,
                            onClick = { isDiscountPercentageInput = false },
                            label = { Text("Nominal (Rp)") },
                            modifier = Modifier.weight(1f)
                        )
                        androidx.compose.material3.FilterChip(
                            selected = isDiscountPercentageInput,
                            onClick = { isDiscountPercentageInput = true },
                            label = { Text("Persen (%)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = discountInputValue,
                        onValueChange = { discountInputValue = it.filter { ch -> ch.isDigit() } },
                        label = { Text(if (isDiscountPercentageInput) "Persen Diskon" else "Nominal Diskon (Total Row)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        prefix = { if (!isDiscountPercentageInput) Text("Rp") },
                        suffix = { if (isDiscountPercentageInput) Text("%") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val value = discountInputValue.toDoubleOrNull() ?: 0.0
                        viewModel.updateItemDiscount(selectedItemForDiscount!!.product.id, value, isDiscountPercentageInput)
                        showDiscountDialog = false
                    }
                ) {
                    Text("Terapkan")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDiscountDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun ProductPosCard(
    product: Product,
    inCartQty: Int,
    isOutOfStock: Boolean,
    bgColor: Color,
    borderStroke: Color,
    onCardClick: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(
            if (inCartQty > 0) 2.dp else 1.dp,
            if (inCartQty > 0) MaterialTheme.colorScheme.primary else borderStroke
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Category Tag & Stock Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = product.categoryName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isOutOfStock) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF4A1E1E))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Habis",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF6B6B)
                        )
                    }
                } else if (product.stock <= product.minStockAlert) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF3D2E14))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Sisa ${product.stock}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusWarning
                        )
                    }
                } else {
                    Text(
                        text = "Stok: ${product.stock}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Product Name
            Text(
                text = product.name,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Price & Add / Quantity Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = CurrencyUtils.formatRupiah(product.price),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (inCartQty > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clickable { onDecrement() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Kurang",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "$inCartQty",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clickable { onIncrement() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Tambah",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isOutOfStock) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Tambah",
                            tint = if (isOutOfStock) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
