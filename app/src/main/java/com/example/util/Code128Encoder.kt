package com.example.util

/**
 * Pure Kotlin ISO/IEC 15417 Code-128 (Set B) barcode encoder & receipt invoice matcher.
 * Encodes receipt invoice numbers (e.g. "INV-20261121-040228") into valid scannable bars.
 */
object Code128Encoder {

    internal val PATTERNS: Array<IntArray> = arrayOf(
        intArrayOf(2, 1, 2, 2, 2, 2), // 0
        intArrayOf(2, 2, 2, 1, 2, 2), // 1
        intArrayOf(2, 2, 2, 2, 2, 1), // 2
        intArrayOf(1, 2, 1, 2, 2, 3), // 3
        intArrayOf(1, 2, 1, 3, 2, 2), // 4
        intArrayOf(1, 3, 1, 2, 2, 2), // 5
        intArrayOf(1, 2, 2, 2, 1, 3), // 6
        intArrayOf(1, 2, 2, 3, 1, 2), // 7
        intArrayOf(1, 3, 2, 2, 1, 2), // 8
        intArrayOf(2, 2, 1, 2, 1, 3), // 9
        intArrayOf(2, 2, 1, 3, 1, 2), // 10
        intArrayOf(2, 3, 1, 2, 1, 2), // 11
        intArrayOf(1, 1, 2, 2, 3, 2), // 12
        intArrayOf(1, 2, 2, 1, 3, 2), // 13
        intArrayOf(1, 2, 2, 2, 3, 1), // 14
        intArrayOf(1, 1, 3, 2, 2, 2), // 15
        intArrayOf(1, 2, 3, 1, 2, 2), // 16
        intArrayOf(1, 2, 3, 2, 2, 1), // 17
        intArrayOf(2, 2, 3, 2, 1, 1), // 18
        intArrayOf(2, 2, 1, 1, 3, 2), // 19
        intArrayOf(2, 2, 1, 2, 3, 1), // 20
        intArrayOf(2, 1, 3, 2, 1, 2), // 21
        intArrayOf(2, 2, 3, 1, 1, 2), // 22
        intArrayOf(3, 1, 2, 1, 3, 1), // 23
        intArrayOf(3, 1, 1, 2, 2, 2), // 24
        intArrayOf(3, 2, 1, 1, 2, 2), // 25
        intArrayOf(3, 2, 1, 2, 2, 1), // 26
        intArrayOf(3, 1, 2, 2, 1, 2), // 27
        intArrayOf(3, 2, 2, 1, 1, 2), // 28
        intArrayOf(3, 2, 2, 2, 1, 1), // 29
        intArrayOf(2, 1, 2, 1, 2, 3), // 30
        intArrayOf(2, 1, 2, 3, 2, 1), // 31
        intArrayOf(2, 3, 2, 1, 2, 1), // 32
        intArrayOf(1, 1, 1, 3, 2, 3), // 33
        intArrayOf(1, 3, 1, 1, 2, 3), // 34
        intArrayOf(1, 3, 1, 3, 2, 1), // 35
        intArrayOf(1, 1, 2, 3, 1, 3), // 36
        intArrayOf(1, 3, 2, 1, 1, 3), // 37
        intArrayOf(1, 3, 2, 3, 1, 1), // 38
        intArrayOf(2, 1, 1, 3, 1, 3), // 39
        intArrayOf(2, 3, 1, 1, 1, 3), // 40
        intArrayOf(2, 3, 1, 3, 1, 1), // 41
        intArrayOf(1, 1, 2, 1, 3, 3), // 42
        intArrayOf(1, 1, 2, 3, 3, 1), // 43
        intArrayOf(1, 3, 2, 1, 3, 1), // 44
        intArrayOf(1, 1, 3, 1, 2, 3), // 45
        intArrayOf(1, 1, 3, 3, 2, 1), // 46
        intArrayOf(1, 3, 3, 1, 2, 1), // 47
        intArrayOf(3, 1, 3, 1, 2, 1), // 48
        intArrayOf(2, 1, 1, 3, 3, 1), // 49
        intArrayOf(2, 3, 1, 1, 3, 1), // 50
        intArrayOf(2, 1, 3, 1, 1, 3), // 51
        intArrayOf(2, 1, 3, 3, 1, 1), // 52
        intArrayOf(2, 1, 3, 1, 3, 1), // 53
        intArrayOf(3, 1, 1, 1, 2, 3), // 54
        intArrayOf(3, 1, 1, 3, 2, 1), // 55
        intArrayOf(3, 3, 1, 1, 2, 1), // 56
        intArrayOf(3, 1, 2, 1, 1, 3), // 57
        intArrayOf(3, 1, 2, 3, 1, 1), // 58
        intArrayOf(3, 3, 2, 1, 1, 1), // 59
        intArrayOf(3, 1, 4, 1, 1, 1), // 60
        intArrayOf(2, 2, 1, 4, 1, 1), // 61
        intArrayOf(4, 3, 1, 1, 1, 1), // 62
        intArrayOf(1, 1, 1, 2, 2, 4), // 63
        intArrayOf(1, 1, 1, 4, 2, 2), // 64
        intArrayOf(1, 2, 1, 1, 2, 4), // 65
        intArrayOf(1, 2, 1, 4, 2, 1), // 66
        intArrayOf(1, 4, 1, 1, 2, 2), // 67
        intArrayOf(1, 4, 1, 2, 2, 1), // 68
        intArrayOf(1, 1, 2, 2, 1, 4), // 69
        intArrayOf(1, 1, 2, 4, 1, 2), // 70
        intArrayOf(1, 2, 2, 1, 1, 4), // 71
        intArrayOf(1, 2, 2, 4, 1, 1), // 72
        intArrayOf(1, 4, 2, 1, 1, 2), // 73
        intArrayOf(1, 4, 2, 2, 1, 1), // 74
        intArrayOf(2, 4, 1, 2, 1, 1), // 75
        intArrayOf(2, 2, 1, 1, 1, 4), // 76
        intArrayOf(4, 1, 3, 1, 1, 1), // 77
        intArrayOf(2, 4, 1, 1, 1, 2), // 78
        intArrayOf(1, 3, 4, 1, 1, 1), // 79
        intArrayOf(1, 1, 1, 2, 4, 2), // 80
        intArrayOf(1, 2, 1, 1, 4, 2), // 81
        intArrayOf(1, 2, 1, 2, 4, 1), // 82
        intArrayOf(1, 1, 4, 2, 1, 2), // 83
        intArrayOf(1, 2, 4, 1, 1, 2), // 84
        intArrayOf(1, 2, 4, 2, 1, 1), // 85
        intArrayOf(4, 1, 1, 2, 1, 2), // 86
        intArrayOf(4, 2, 1, 1, 1, 2), // 87
        intArrayOf(4, 2, 1, 2, 1, 1), // 88
        intArrayOf(2, 1, 2, 1, 4, 1), // 89
        intArrayOf(2, 1, 4, 1, 2, 1), // 90
        intArrayOf(4, 1, 2, 1, 2, 1), // 91
        intArrayOf(1, 1, 1, 1, 4, 3), // 92
        intArrayOf(1, 1, 1, 3, 4, 1), // 93
        intArrayOf(1, 3, 1, 1, 4, 1), // 94
        intArrayOf(1, 1, 4, 1, 1, 3), // 95
        intArrayOf(1, 1, 4, 3, 1, 1), // 96
        intArrayOf(4, 1, 1, 1, 1, 3), // 97
        intArrayOf(4, 1, 1, 3, 1, 1), // 98
        intArrayOf(1, 1, 3, 1, 4, 1), // 99
        intArrayOf(1, 1, 4, 1, 3, 1), // 100
        intArrayOf(3, 1, 1, 1, 4, 1), // 101
        intArrayOf(4, 1, 1, 1, 3, 1), // 102
        intArrayOf(2, 1, 1, 4, 1, 2), // 103 Start Code A
        intArrayOf(2, 1, 1, 2, 1, 4), // 104 Start Code B
        intArrayOf(2, 1, 1, 2, 3, 2), // 105 Start Code C
        intArrayOf(2, 3, 3, 1, 1, 1, 2) // 106 Stop
    )

    private const val START_CODE_B = 104
    private const val STOP_CODE = 106

    /**
     * Filters text to printable ASCII (32..126) safe for Code-128 Set B.
     */
    fun sanitizeCode128BText(raw: String): String {
        val cleaned = raw.trim().filter { it.code in 32..126 }
        return cleaned.ifEmpty { "INV-0000" }
    }

    /**
     * Encodes text using Code-128 Set B into alternating bar/space module widths.
     * Index 0 is a black bar, index 1 is a white space, ..., final element is a black bar.
     */
    fun encodeToBarWidths(rawText: String): IntArray {
        val text = sanitizeCode128BText(rawText)
        val codes = ArrayList<Int>(text.length + 3)
        codes.add(START_CODE_B)

        var checksum = START_CODE_B
        text.forEachIndexed { index, ch ->
            val value = ch.code - 32
            codes.add(value)
            checksum += value * (index + 1)
        }
        codes.add(checksum % 103)
        codes.add(STOP_CODE)

        val totalElements = (codes.size - 1) * 6 + 7
        val widths = IntArray(totalElements)
        var pos = 0
        for (code in codes) {
            val pattern = PATTERNS[code]
            for (w in pattern) {
                widths[pos++] = w
            }
        }
        return widths
    }

    /**
     * Normalizes scanned barcode content for matching stored receipt invoice numbers.
     */
    fun normalizeScannedInvoice(rawScan: String): String {
        val trimmed = rawScan.trim()
        if (trimmed.isBlank()) return ""
        val cleanedPrefix = trimmed
            .removePrefix("No. Nota :")
            .removePrefix("No. Nota:")
            .removePrefix("NOTA:")
            .trim()
        return cleanedPrefix.uppercase()
    }
}
