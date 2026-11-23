package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.QrisDisplayDialog
import com.example.ui.theme.LocalCustomColors
import com.example.ui.theme.StatusQris
import com.example.ui.viewmodel.KasirViewModel
import com.example.util.CurrencyUtils

data class ManualItemEntry(
    val name: String,
    val amount: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HitungCepatScreen(
    viewModel: KasirViewModel
) {
    val storeConfig by viewModel.storeConfig.collectAsStateWithLifecycle()
    val customColors = LocalCustomColors.current

    // Calculation states
    var currentInput by remember { mutableStateOf("0") }
    val addedItems = remember { mutableStateListOf<ManualItemEntry>() }
    var transactionNote by remember { mutableStateOf("") }

    // UI Dialog & BottomSheet states
    var isQrisDialogOpen by remember { mutableStateOf(false) }
    var isCashPaymentSheetOpen by remember { mutableStateOf(false) }
    var isCustomAmountDialogOpen by remember { mutableStateOf(false) }
    var isItemsListExpanded by remember { mutableStateOf(false) }

    // Custom add dialog states
    var customDialogAmount by remember { mutableStateOf("") }
    var customDialogName by remember { mutableStateOf("") }

    // Active typed amount
    val currentTypedAmount = currentInput.toDoubleOrNull() ?: 0.0

    // Sum of previously committed items
    val itemsTotalSum = addedItems.sumOf { it.amount }

    // Grand total: sum of committed items + currently typed amount
    val grandTotal by remember(itemsTotalSum, currentTypedAmount) {
        derivedStateOf { itemsTotalSum + currentTypedAmount }
    }

    LaunchedEffect(Unit) {
        viewModel.qrisPaymentTrigger.collect {
            if (grandTotal > 0) {
                isQrisDialogOpen = true
            } else {
                viewModel.emitToast("Masukkan nominal terlebih dahulu sebelum QRIS!")
            }
        }
    }

    // Commits the currently typed number into the items accumulator
    fun commitCurrentInput(customName: String? = null) {
        if (currentTypedAmount > 0) {
            val itemName = customName ?: if (addedItems.isEmpty()) "Item 1" else "Item ${addedItems.size + 1}"
            addedItems.add(ManualItemEntry(itemName, currentTypedAmount))
            currentInput = "0"
        }
    }

    // Handles Digit Press (0-9, 00, 000)
    fun onDigitPress(digit: String) {
        if (currentInput == "0") {
            if (digit != "0" && digit != "00" && digit != "000") {
                currentInput = digit
            }
        } else if (currentInput.length < 10) {
            currentInput += digit
        }
    }

    // Handles Plus button (+)
    fun onPlusPress() {
        if (currentTypedAmount > 0) {
            commitCurrentInput()
        }
    }

    // Handles Backspace (⌫)
    fun onBackspace() {
        if (currentInput.length > 1) {
            currentInput = currentInput.substring(0, currentInput.length - 1)
        } else if (currentInput != "0") {
            currentInput = "0"
        } else if (addedItems.isNotEmpty()) {
            // Remove last added item if currentInput is already 0
            addedItems.removeAt(addedItems.lastIndex)
        }
    }

    // Handles Clear (C)
    fun onClear() {
        if (currentInput != "0") {
            currentInput = "0"
        } else {
            addedItems.clear()
        }
    }

    // Handles Full Reset
    fun onResetAll() {
        currentInput = "0"
        addedItems.clear()
        transactionNote = ""
    }

    // Add preset quick nominal (e.g. +10k, +50k)
    fun addQuickNominal(amount: Double, label: String = "") {
        val itemName = if (label.isNotBlank()) label else "Item ${addedItems.size + 1}"
        addedItems.add(ManualItemEntry(itemName, amount))
    }

    // Prepare manual items list for checkout
    fun getCheckoutItems(): List<Pair<String, Double>> {
        val result = mutableListOf<Pair<String, Double>>()
        addedItems.forEach { entry ->
            result.add(entry.name to entry.amount)
        }
        if (currentTypedAmount > 0) {
            val name = if (result.isEmpty()) "Item 1" else "Item ${result.size + 1}"
            result.add(name to currentTypedAmount)
        }
        return result
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        val isTablet = maxWidth >= 720.dp

        @Composable
        fun TopHeader() {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Calculate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hitung Cepat & Kasir Manual",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (addedItems.isNotEmpty() || currentInput != "0" || transactionNote.isNotBlank()) {
                    IconButton(
                        onClick = { onResetAll() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset Semua",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        @Composable
        fun LeftInfoPane(modifier: Modifier = Modifier) {
            Column(modifier = modifier.fillMaxWidth()) {
                // Interactive Display Card (Grand Total, Expression preview, & items counter)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, customColors.borderStroke)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOTAL PEMBAYARAN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Items count pill / button to toggle items list
                            val totalItemCount = addedItems.size + if (currentTypedAmount > 0) 1 else 0
                            if (totalItemCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    modifier = Modifier.clickable { isItemsListExpanded = !isItemsListExpanded }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ListAlt,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "$totalItemCount Item",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            imageVector = if (isItemsListExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Big Main Amount Display
                        Text(
                            text = CurrencyUtils.formatRupiah(grandTotal),
                            fontSize = if (grandTotal >= 10000000) 28.sp else 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = customColors.heroAmountColor,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Expression Line (e.g. 15.000 + 25.000 + 10.000)
                        if (addedItems.isNotEmpty() || currentTypedAmount > 0) {
                            Spacer(modifier = Modifier.height(2.dp))
                            val expressionText = buildString {
                                addedItems.forEachIndexed { idx, item ->
                                    if (idx > 0) append(" + ")
                                    append(CurrencyUtils.formatRupiah(item.amount).replace("Rp ", ""))
                                }
                                if (currentTypedAmount > 0) {
                                    if (addedItems.isNotEmpty()) append(" + ")
                                    append(CurrencyUtils.formatRupiah(currentTypedAmount).replace("Rp ", ""))
                                }
                            }
                            Text(
                                text = expressionText,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Expandable Breakdown of added manual items
                        AnimatedVisibility(
                            visible = isItemsListExpanded && addedItems.isNotEmpty(),
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(customColors.borderStroke)
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 132.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    addedItems.forEachIndexed { index, entry ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${index + 1}. ${entry.name}",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = CurrencyUtils.formatRupiah(entry.amount),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                IconButton(
                                                    onClick = { addedItems.removeAt(index) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Hapus",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Addition Chips Row (+ Tambah Sesuka Hati & Nominal Populer)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Button Tambah Custom Bebas
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            customDialogAmount = ""
                            customDialogName = ""
                            isCustomAmountDialogOpen = true
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "+ Custom (Bebas)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Popular Quick Chips: +1k, +2k, +5k, +10k, +20k, +50k, +100k
                    val quickChips = listOf(
                        1000.0 to "+1k",
                        2000.0 to "+2k",
                        5000.0 to "+5k",
                        10000.0 to "+10k",
                        20000.0 to "+20k",
                        50000.0 to "+50k",
                        100000.0 to "+100k"
                    )

                    quickChips.forEach { (amount, label) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = customColors.subCardBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.borderStroke),
                            modifier = Modifier.clickable { addQuickNominal(amount) }
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Keterangan / Catatan Input
                OutlinedTextField(
                    value = transactionNote,
                    onValueChange = { transactionNote = it },
                    placeholder = { Text("Keterangan transaksi (misal: Pesanan Meja 2, dsb)...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = customColors.subCardBg,
                        unfocusedContainerColor = customColors.subCardBg,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = customColors.borderStroke
                    )
                )
            }
        }

        @Composable
        fun KeypadSection(modifier: Modifier = Modifier) {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Row 1: 7, 8, 9, C
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KeypadButton("7", Modifier.weight(1f), customColors.subCardBg) { onDigitPress("7") }
                    KeypadButton("8", Modifier.weight(1f), customColors.subCardBg) { onDigitPress("8") }
                    KeypadButton("9", Modifier.weight(1f), customColors.subCardBg) { onDigitPress("9") }
                    KeypadButton(
                        text = "C",
                        modifier = Modifier.weight(1f),
                        bgColor = MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                        textColor = MaterialTheme.colorScheme.error
                    ) { onClear() }
                }

                // Row 2: 4, 5, 6, ⌫
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KeypadButton("4", Modifier.weight(1f), customColors.subCardBg) { onDigitPress("4") }
                    KeypadButton("5", Modifier.weight(1f), customColors.subCardBg) { onDigitPress("5") }
                    KeypadButton("6", Modifier.weight(1f), customColors.subCardBg) { onDigitPress("6") }
                    KeypadIconButton(
                        icon = Icons.Default.Backspace,
                        modifier = Modifier.weight(1f),
                        bgColor = customColors.subCardBg
                    ) { onBackspace() }
                }

                // Row 3: 1, 2, 3, + (TAMBAH SEPUASNYA)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KeypadButton("1", Modifier.weight(1f), customColors.subCardBg) { onDigitPress("1") }
                    KeypadButton("2", Modifier.weight(1f), customColors.subCardBg) { onDigitPress("2") }
                    KeypadButton("3", Modifier.weight(1f), customColors.subCardBg) { onDigitPress("3") }
                    KeypadButton(
                        text = "+",
                        modifier = Modifier.weight(1f),
                        bgColor = MaterialTheme.colorScheme.primary,
                        textColor = Color.White,
                        isProminent = true
                    ) { onPlusPress() }
                }

                // Row 4: 0, 00, 000, + Item Bebas
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KeypadButton("0", Modifier.weight(1f), customColors.subCardBg) { onDigitPress("0") }
                    KeypadButton("00", Modifier.weight(1f), customColors.subCardBg) { onDigitPress("00") }
                    KeypadButton("000", Modifier.weight(1f), customColors.subCardBg) { onDigitPress("000") }
                    KeypadButton(
                        text = "+ Rp..",
                        modifier = Modifier.weight(1f),
                        bgColor = customColors.heroCardBg,
                        textColor = MaterialTheme.colorScheme.primary
                    ) {
                        customDialogAmount = ""
                        customDialogName = ""
                        isCustomAmountDialogOpen = true
                    }
                }
            }
        }

        @Composable
        fun PaymentActions(modifier: Modifier = Modifier) {
            val aiEnabled = storeConfig?.isAiAssistantEnabled != false
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(bottom = if (!isTablet && aiEnabled) 68.dp else 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (grandTotal <= 0) {
                            viewModel.emitToast("Masukkan nominal terlebih dahulu!")
                            return@Button
                        }
                        isCashPaymentSheetOpen = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("quick_cash_pay_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = customColors.actionPrimaryBg
                    )
                ) {
                    Icon(imageVector = Icons.Default.PointOfSale, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bayar Tunai", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Button(
                    onClick = {
                        if (grandTotal <= 0) {
                            viewModel.emitToast("Masukkan nominal terlebih dahulu!")
                            return@Button
                        }
                        isQrisDialogOpen = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("quick_qris_pay_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusQris
                    )
                ) {
                    Icon(imageVector = Icons.Default.QrCode, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bayar QRIS", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        if (isTablet) {
            Column(
                modifier = Modifier
                    .widthIn(max = 1160.dp)
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                TopHeader()
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        LeftInfoPane()
                        Spacer(modifier = Modifier.height(14.dp))
                        PaymentActions()
                    }
                    Column(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight()
                    ) {
                        KeypadSection(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TopHeader()
                Spacer(modifier = Modifier.height(10.dp))
                LeftInfoPane()
                Spacer(modifier = Modifier.height(8.dp))
                KeypadSection(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                PaymentActions()
            }
        }
    }

    // Modal Sheet: Bayar Tunai dengan Pilihan Cepat Uang Pas / Nominal Pas
    if (isCashPaymentSheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var cashGivenInput by remember { mutableStateOf(grandTotal.toLong().toString()) }
        val cashGiven = cashGivenInput.toDoubleOrNull() ?: 0.0
        val change = (cashGiven - grandTotal).coerceAtLeast(0.0)
        val isEnough = cashGiven >= grandTotal

        ModalBottomSheet(
            onDismissRequest = { isCashPaymentSheetOpen = false },
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
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp)
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pembayaran Tunai",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { isCashPaymentSheetOpen = false }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tagihan
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total Tagihan", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            CurrencyUtils.formatRupiah(grandTotal),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = customColors.heroAmountColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Uang Diterima Input
                OutlinedTextField(
                    value = cashGivenInput,
                    onValueChange = { cashGivenInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Uang Tunai Diterima") },
                    prefix = { Text("Rp ") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = customColors.subCardBg,
                        unfocusedContainerColor = customColors.subCardBg
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Cash Suggestion Chips
                val suggestions = remember(grandTotal) {
                    val list = mutableListOf<Double>()
                    list.add(grandTotal) // Uang Pas
                    val base50k = ((grandTotal / 50000).toLong() + 1) * 50000.0
                    val base100k = ((grandTotal / 100000).toLong() + 1) * 100000.0
                    if (!list.contains(base50k) && base50k > grandTotal) list.add(base50k)
                    if (!list.contains(base100k) && base100k > grandTotal) list.add(base100k)
                    if (!list.contains(50000.0) && 50000.0 > grandTotal) list.add(50000.0)
                    if (!list.contains(100000.0) && 100000.0 > grandTotal) list.add(100000.0)
                    list.sorted()
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.forEach { sug ->
                        val isPas = sug == grandTotal
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (cashGiven == sug) MaterialTheme.colorScheme.primary else customColors.subCardBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.borderStroke),
                            modifier = Modifier.clickable {
                                cashGivenInput = sug.toLong().toString()
                            }
                        ) {
                            Text(
                                text = if (isPas) "Uang Pas" else CurrencyUtils.formatRupiah(sug),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (cashGiven == sug) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Kembalian Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Kembalian:", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = if (isEnough) CurrencyUtils.formatRupiah(change) else "Uang Kurang",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnough) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        if (!isEnough) {
                            viewModel.emitToast("Uang yang diterima kurang!")
                            return@Button
                        }
                        val itemsToSave = getCheckoutItems()
                        isCashPaymentSheetOpen = false
                        viewModel.processQuickCheckout(
                            nominal = grandTotal,
                            note = transactionNote,
                            paymentMethod = "TUNAI",
                            amountReceived = cashGiven,
                            manualItems = itemsToSave,
                            onSuccess = {
                                onResetAll()
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = isEnough,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = customColors.actionPrimaryBg
                    )
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Selesaikan Pembayaran", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                }
            }
        }
    }

    // Dialog: Tambah Custom Nominal & Nama Item Bebas
    if (isCustomAmountDialogOpen) {
        AlertDialog(
            onDismissRequest = { isCustomAmountDialogOpen = false },
            title = {
                Text("Tambah Nominal Bebas", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = customDialogAmount,
                        onValueChange = { customDialogAmount = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Jumlah / Nominal") },
                        prefix = { Text("Rp ") },
                        placeholder = { Text("Misal: 15000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customDialogName,
                        onValueChange = { customDialogName = it },
                        label = { Text("Nama Item (Opsional)") },
                        placeholder = { Text("Misal: Kerupuk / Jasa") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = customDialogAmount.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            addQuickNominal(amt, customDialogName)
                            isCustomAmountDialogOpen = false
                        } else {
                            viewModel.emitToast("Nominal harus lebih dari 0!")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.actionPrimaryBg)
                ) {
                    Text("Tambah (+)")
                }
            },
            dismissButton = {
                TextButton(onClick = { isCustomAmountDialogOpen = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // QRIS Payment Dialog
    if (isQrisDialogOpen && storeConfig != null) {
        QrisDisplayDialog(
            amount = grandTotal,
            storeConfig = storeConfig!!,
            onDismiss = { isQrisDialogOpen = false },
            onConfirmPaid = {
                val itemsToSave = getCheckoutItems()
                isQrisDialogOpen = false
                viewModel.processQuickCheckout(
                    nominal = grandTotal,
                    note = transactionNote,
                    paymentMethod = "QRIS",
                    amountReceived = grandTotal,
                    manualItems = itemsToSave,
                    onSuccess = {
                        onResetAll()
                    }
                )
            }
        )
    }
}

@Composable
private fun KeypadButton(
    text: String,
    modifier: Modifier = Modifier,
    bgColor: Color,
    textColor: Color = Color.Unspecified,
    isProminent: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = if (isProminent) 26.sp else if (text.length > 3) 16.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (textColor != Color.Unspecified) textColor else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun KeypadIconButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    bgColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}
