package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class TrialStatus(
    val isActivated: Boolean = false,
    val isTrialExpired: Boolean = false,
    val remainingSeconds: Int = 300,
    val deviceId: String = "",
    val adminWhatsappNumber: String = LicenseManager.DEFAULT_ADMIN_WA
)

object LicenseManager {
    // 5 menit = 300 detik = 300_000 ms
    const val TRIAL_DURATION_MS: Long = 5L * 60L * 1000L

    // Nomor WhatsApp Admin (format internasional tanpa '+' atau '0' di depan)
    const val DEFAULT_ADMIN_WA: String = "6281344195326"

    // PIN untuk mengakses Generator API Key di Developer Mode
    const val ADMIN_KEYGEN_PIN: String = "212280021"

    // Secret key untuk enkripsi tanda tangan lisensi per perangkat (ubah sebelum build release)
    private const val SECRET_SALT: String = "KASIR_PRO_V1_HMAC_2026_PRIVATE_SALT"

    private const val PREFS_NAME = "ksr_lic_prefs_v1"
    private const val BACKUP_FILE_NAME = ".ksr_lic_state_v1"

    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_TRIAL_START_WALL_MS = "trial_start_wall_ms"
    private const val KEY_TRIAL_EXPIRED = "trial_expired"
    private const val KEY_ACTIVATED_KEY = "activated_key"
    private const val KEY_SERVER_ACTIVATED_SIG = "server_activated_sig"
    private const val KEY_ADMIN_WA = "admin_wa_override"

    private val lock = Any()

    private val DEVICE_ID_REGEX = Regex(
        """\bKSR[- ]?([A-Z0-9]{4})[- ]?([A-Z0-9]{4})\b""",
        RegexOption.IGNORE_CASE
    )
    private val LICENSE_KEY_REGEX = Regex(
        """\bKPRO[- ]?([A-Z0-9]{4})[- ]?([A-Z0-9]{4})[- ]?([A-Z0-9]{4})\b""",
        RegexOption.IGNORE_CASE
    )

    private val gatewayHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Menghasilkan API / Kode Aktivasi unik yang terikat pada ID Perangkat pembeli.
     * Format: KPRO-XXXX-XXXX-XXXX
     */
    fun generateLicenseKeyForDevice(deviceId: String): String {
        val normalizedId = deviceId.trim().uppercase().replace(" ", "")
        if (normalizedId.isBlank()) return ""

        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(SECRET_SALT.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(keySpec)
        val rawHmac = mac.doFinal("KASIR_PRO:$normalizedId".toByteArray(StandardCharsets.UTF_8))

        val hex = rawHmac.joinToString("") { "%02X".format(it) }
        val token = hex.take(12)
        return "KPRO-${token.substring(0, 4)}-${token.substring(4, 8)}-${token.substring(8, 12)}"
    }

    /**
     * Memvalidasi apakah API / Kode Aktivasi yang dimasukkan cocok untuk ID Perangkat ini.
     */
    fun verifyLicenseKey(deviceId: String, inputKey: String): Boolean {
        val normalizedDevice = deviceId.trim().uppercase().replace(" ", "")
        if (normalizedDevice.isBlank()) return false

        val cleanedInput = inputKey.trim().uppercase()
            .replace(" ", "")
            .replace("-", "")

        val expectedFormatted = generateLicenseKeyForDevice(normalizedDevice)
        val expectedCleaned = expectedFormatted.replace("-", "")

        if (cleanedInput.length != expectedCleaned.length) return false

        return MessageDigest.isEqual(
            cleanedInput.toByteArray(StandardCharsets.UTF_8),
            expectedCleaned.toByteArray(StandardCharsets.UTF_8)
        )
    }

    @SuppressLint("HardwareIds")
    fun getOrCreateDeviceId(context: Context): String {
        synchronized(lock) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val saved = prefs.getString(KEY_DEVICE_ID, null)
            if (!saved.isNullOrBlank()) return saved

            val backupJson = readBackupJson(context)
            val backupId = backupJson?.optString(KEY_DEVICE_ID, "")?.takeIf { it.isNotBlank() }
            if (backupId != null) {
                prefs.edit().putString(KEY_DEVICE_ID, backupId).commit()
                return backupId
            }

            val androidId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (_: Exception) {
                null
            }

            val seed = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                "AID:$androidId:${context.packageName}"
            } else {
                "UID:${UUID.randomUUID()}:${context.packageName}"
            }

            val digest = MessageDigest.getInstance("SHA-256")
                .digest(seed.toByteArray(StandardCharsets.UTF_8))
            val hex = digest.joinToString("") { "%02X".format(it) }.take(8)
            val formattedId = "KSR-${hex.substring(0, 4)}-${hex.substring(4, 8)}"

            prefs.edit().putString(KEY_DEVICE_ID, formattedId).commit()
            persistBackupState(
                context = context,
                deviceId = formattedId,
                trialStartMs = prefs.getLong(KEY_TRIAL_START_WALL_MS, 0L),
                trialExpired = prefs.getBoolean(KEY_TRIAL_EXPIRED, false),
                activatedKey = prefs.getString(KEY_ACTIVATED_KEY, "") ?: "",
                adminWa = prefs.getString(KEY_ADMIN_WA, DEFAULT_ADMIN_WA) ?: DEFAULT_ADMIN_WA
            )
            return formattedId
        }
    }

    fun getTrialState(context: Context, nowMs: Long = System.currentTimeMillis()): TrialStatus {
        synchronized(lock) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val deviceId = getOrCreateDeviceId(context)
            val backupJson = readBackupJson(context)

            val adminWa = prefs.getString(KEY_ADMIN_WA, null)
                ?.takeIf { it.isNotBlank() }
                ?: backupJson?.optString(KEY_ADMIN_WA, "")?.takeIf { it.isNotBlank() }
                ?: DEFAULT_ADMIN_WA

            // 1. Cek apakah sudah aktif menggunakan API Key valid (lokal atau terverifikasi server)
            val storedKey = prefs.getString(KEY_ACTIVATED_KEY, null)
                ?.takeIf { it.isNotBlank() }
                ?: backupJson?.optString(KEY_ACTIVATED_KEY, "")?.takeIf { it.isNotBlank() }
                ?: ""
            val storedServerSig = prefs.getString(KEY_SERVER_ACTIVATED_SIG, null)
                ?.takeIf { it.isNotBlank() }
                ?: backupJson?.optString(KEY_SERVER_ACTIVATED_SIG, "")?.takeIf { it.isNotBlank() }
                ?: ""

            val isLocallyValid = storedKey.isNotBlank() && verifyLicenseKey(deviceId, storedKey)
            val isServerBoundValid = storedKey.isNotBlank() && verifyServerActivationBinding(deviceId, storedKey, storedServerSig)

            if (isLocallyValid || isServerBoundValid) {
                // Pastikan sinkron antara prefs dan backup
                if (prefs.getString(KEY_ACTIVATED_KEY, "") != storedKey ||
                    prefs.getString(KEY_SERVER_ACTIVATED_SIG, "") != storedServerSig
                ) {
                    prefs.edit()
                        .putString(KEY_ACTIVATED_KEY, storedKey)
                        .putString(KEY_SERVER_ACTIVATED_SIG, storedServerSig)
                        .commit()
                }
                return TrialStatus(
                    isActivated = true,
                    isTrialExpired = false,
                    remainingSeconds = 0,
                    deviceId = deviceId,
                    adminWhatsappNumber = adminWa
                )
            }

            // 2. Ambil waktu mulai uji coba (ambil yang paling awal antara prefs dan backup file)
            val prefStart = prefs.getLong(KEY_TRIAL_START_WALL_MS, 0L)
            val backupStart = backupJson?.optLong(KEY_TRIAL_START_WALL_MS, 0L) ?: 0L

            var trialStartMs = when {
                prefStart > 0L && backupStart > 0L -> minOf(prefStart, backupStart)
                prefStart > 0L -> prefStart
                backupStart > 0L -> backupStart
                else -> 0L
            }

            var isExpiredFlag = prefs.getBoolean(KEY_TRIAL_EXPIRED, false) ||
                (backupJson?.optBoolean(KEY_TRIAL_EXPIRED, false) == true)

            if (trialStartMs <= 0L) {
                trialStartMs = nowMs
                prefs.edit()
                    .putLong(KEY_TRIAL_START_WALL_MS, trialStartMs)
                    .putBoolean(KEY_TRIAL_EXPIRED, false)
                    .commit()
                persistBackupState(context, deviceId, trialStartMs, false, "", adminWa)
            }

            // 3. Cek jika sudah pernah kadaluarsa atau jam perangkat dimundurkan
            if (isExpiredFlag || nowMs < trialStartMs - 10_000L) {
                isExpiredFlag = true
                prefs.edit()
                    .putLong(KEY_TRIAL_START_WALL_MS, trialStartMs)
                    .putBoolean(KEY_TRIAL_EXPIRED, true)
                    .commit()
                persistBackupState(context, deviceId, trialStartMs, true, "", adminWa)
                return TrialStatus(
                    isActivated = false,
                    isTrialExpired = true,
                    remainingSeconds = 0,
                    deviceId = deviceId,
                    adminWhatsappNumber = adminWa
                )
            }

            // 4. Hitung sisa waktu 5 menit
            val elapsed = (nowMs - trialStartMs).coerceAtLeast(0L)
            val remainingMs = (TRIAL_DURATION_MS - elapsed).coerceAtLeast(0L)

            if (remainingMs <= 0L) {
                prefs.edit()
                    .putLong(KEY_TRIAL_START_WALL_MS, trialStartMs)
                    .putBoolean(KEY_TRIAL_EXPIRED, true)
                    .commit()
                persistBackupState(context, deviceId, trialStartMs, true, "", adminWa)
                return TrialStatus(
                    isActivated = false,
                    isTrialExpired = true,
                    remainingSeconds = 0,
                    deviceId = deviceId,
                    adminWhatsappNumber = adminWa
                )
            }

            val remainingSeconds = ((remainingMs + 999L) / 1000L).toInt()
            return TrialStatus(
                isActivated = false,
                isTrialExpired = false,
                remainingSeconds = remainingSeconds,
                deviceId = deviceId,
                adminWhatsappNumber = adminWa
            )
        }
    }

    private fun computeServerActivationBinding(deviceId: String, canonicalKey: String): String {
        val normalizedDevice = deviceId.trim().uppercase().replace(" ", "")
        val cleanedKey = canonicalKey.trim().uppercase().replace(" ", "")
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(SECRET_SALT.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(keySpec)
        val raw = mac.doFinal("SERVER_VERIFIED:$normalizedDevice:$cleanedKey".toByteArray(StandardCharsets.UTF_8))
        return raw.joinToString("") { "%02X".format(it) }
    }

    private fun verifyServerActivationBinding(deviceId: String, storedKey: String, storedSig: String): Boolean {
        if (storedKey.isBlank() || storedSig.isBlank()) return false
        val expectedSig = computeServerActivationBinding(deviceId, storedKey)
        return MessageDigest.isEqual(
            storedSig.toByteArray(StandardCharsets.UTF_8),
            expectedSig.toByteArray(StandardCharsets.UTF_8)
        )
    }

    fun activateFromServerVerification(context: Context, inputKey: String): Boolean {
        synchronized(lock) {
            val deviceId = getOrCreateDeviceId(context)
            val canonicalKey = extractLicenseKeyFromText(inputKey)
                ?: inputKey.trim().uppercase().takeIf { it.isNotBlank() }
                ?: return false

            val binding = computeServerActivationBinding(deviceId, canonicalKey)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val startMs = prefs.getLong(KEY_TRIAL_START_WALL_MS, System.currentTimeMillis())
            val adminWa = prefs.getString(KEY_ADMIN_WA, DEFAULT_ADMIN_WA) ?: DEFAULT_ADMIN_WA

            prefs.edit()
                .putString(KEY_ACTIVATED_KEY, canonicalKey)
                .putString(KEY_SERVER_ACTIVATED_SIG, binding)
                .putBoolean(KEY_TRIAL_EXPIRED, false)
                .commit()

            persistBackupState(context, deviceId, startMs, false, canonicalKey, adminWa, binding)
            return true
        }
    }

    fun activateWithKey(context: Context, inputKey: String): Boolean {
        synchronized(lock) {
            val deviceId = getOrCreateDeviceId(context)
            if (!verifyLicenseKey(deviceId, inputKey)) {
                return false
            }

            val canonicalKey = generateLicenseKeyForDevice(deviceId)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val startMs = prefs.getLong(KEY_TRIAL_START_WALL_MS, System.currentTimeMillis())
            val adminWa = prefs.getString(KEY_ADMIN_WA, DEFAULT_ADMIN_WA) ?: DEFAULT_ADMIN_WA

            prefs.edit()
                .putString(KEY_ACTIVATED_KEY, canonicalKey)
                .remove(KEY_SERVER_ACTIVATED_SIG)
                .putBoolean(KEY_TRIAL_EXPIRED, false)
                .commit()

            persistBackupState(context, deviceId, startMs, false, canonicalKey, adminWa, "")
            return true
        }
    }

    fun setAdminWhatsapp(context: Context, waNumber: String) {
        synchronized(lock) {
            val cleaned = waNumber.filter { it.isDigit() }
            val finalWa = if (cleaned.startsWith("0")) "62${cleaned.substring(1)}" else cleaned
            if (finalWa.isBlank()) return

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val deviceId = getOrCreateDeviceId(context)
            val startMs = prefs.getLong(KEY_TRIAL_START_WALL_MS, System.currentTimeMillis())
            val expired = prefs.getBoolean(KEY_TRIAL_EXPIRED, false)
            val activatedKey = prefs.getString(KEY_ACTIVATED_KEY, "") ?: ""
            val serverSig = prefs.getString(KEY_SERVER_ACTIVATED_SIG, "") ?: ""

            prefs.edit().putString(KEY_ADMIN_WA, finalWa).commit()
            persistBackupState(context, deviceId, startMs, expired, activatedKey, finalWa, serverSig)
        }
    }

    fun resetTrialForAdminTesting(context: Context, nowMs: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val deviceId = getOrCreateDeviceId(context)
            val adminWa = prefs.getString(KEY_ADMIN_WA, DEFAULT_ADMIN_WA) ?: DEFAULT_ADMIN_WA

            prefs.edit()
                .putLong(KEY_TRIAL_START_WALL_MS, nowMs)
                .putBoolean(KEY_TRIAL_EXPIRED, false)
                .remove(KEY_ACTIVATED_KEY)
                .remove(KEY_SERVER_ACTIVATED_SIG)
                .commit()

            persistBackupState(context, deviceId, nowMs, false, "", adminWa, "")
        }
    }

    fun formatCountdown(seconds: Int): String {
        val safeSec = seconds.coerceAtLeast(0)
        val mins = safeSec / 60
        val secs = safeSec % 60
        return "%02d:%02d".format(mins, secs)
    }

    /**
     * Mengekstrak ID Perangkat (format KSR-XXXX-XXXX) dari teks chat WhatsApp lengkap.
     * Memudahkan admin tanpa perlu menyeleksi kode ID secara manual.
     */
    fun extractDeviceIdFromText(rawText: String): String? {
        val match = DEVICE_ID_REGEX.find(rawText) ?: return null
        val p1 = match.groupValues[1].uppercase()
        val p2 = match.groupValues[2].uppercase()
        return "KSR-$p1-$p2"
    }

    /**
     * Mengekstrak Kode API Aktivasi (format KPRO-XXXX-XXXX-XXXX) dari balasan WhatsApp Admin.
     */
    fun extractLicenseKeyFromText(rawText: String): String? {
        val match = LICENSE_KEY_REGEX.find(rawText) ?: return null
        val p1 = match.groupValues[1].uppercase()
        val p2 = match.groupValues[2].uppercase()
        val p3 = match.groupValues[3].uppercase()
        return "KPRO-$p1-$p2-$p3"
    }

    fun buildWhatsAppPurchaseMessage(deviceId: String, storeName: String): String {
        val storeInfo = if (storeName.isNotBlank()) "\nNama Usaha: $storeName" else ""
        return "Halo Admin, saya ingin mengaktifkan lisensi permanen aplikasi Kasir Pro.$storeInfo\nID Perangkat: $deviceId\n\nMohon info pembayaran agar perangkat ini bisa diaktifkan dari panel Admin. Terima kasih."
    }

    /**
     * Pesan balasan siap kirim dari Admin ke Pembeli setelah pembayaran terkonfirmasi.
     */
    fun buildAdminReplyToBuyerMessage(deviceId: String, generatedKey: String): String {
        val normalizedId = extractDeviceIdFromText(deviceId) ?: deviceId.trim().uppercase()
        return "Terima kasih telah membeli Kasir Pro!\nID Perangkat: $normalizedId\nKode API Aktivasi Anda:\n$generatedKey\n\nSilakan tempel kode di atas pada layar aktivasi aplikasi."
    }

    /**
     * Pesan internal otomatis ke WA Admin via WhatsApp Gateway (diam-diam tanpa diketahui pembeli).
     */
    fun buildAdminAutoMessageWithKey(deviceId: String, storeName: String): String {
        val normalizedId = extractDeviceIdFromText(deviceId) ?: deviceId.trim().uppercase()
        val generatedKey = generateLicenseKeyForDevice(normalizedId)
        val storeLabel = storeName.trim().ifBlank { "(Belum diisi)" }
        return buildString {
            appendLine("[PERMINTAAN LISENSI KASIR PRO]")
            appendLine("Nama Usaha: $storeLabel")
            appendLine("ID Perangkat: $normalizedId")
            appendLine()
            appendLine("Kode API Otomatis (Siap Kirim ke Pembeli):")
            appendLine(generatedKey)
            appendLine()
            append("Catatan: Cek pembayaran pembeli terlebih dahulu sebelum meneruskan kode di atas.")
        }
    }

    data class WebhookDispatchResult(
        val success: Boolean,
        val statusCode: Int = 0,
        val detail: String = ""
    )

    data class ActivationResult(
        val success: Boolean,
        val message: String,
        val verifiedByServer: Boolean = false
    )

    data class ServerStatusResult(
        val reachable: Boolean,
        val isActivated: Boolean,
        val isRevoked: Boolean = false,
        val statusLabel: String = "PENDING",
        val message: String = ""
    )

    fun normalizeHttpsGatewayUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isBlank()) return null
        val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        return candidate.takeIf { it.startsWith("https://", ignoreCase = true) }
    }

    fun buildSessionEndpoint(baseUrl: String): String = "${baseUrl.trimEnd('/')}/api/license/session"
    fun buildRequestEndpoint(baseUrl: String): String = "${baseUrl.trimEnd('/')}/api/license/request"
    fun buildVerifyEndpoint(baseUrl: String): String = "${baseUrl.trimEnd('/')}/api/license/verify"
    fun buildStatusEndpoint(baseUrl: String): String = "${baseUrl.trimEnd('/')}/api/license/status"

    fun getConfiguredGatewayHost(): String {
        val normalized = normalizeHttpsGatewayUrl(BuildConfig.WEBHOOK_BASE_URL) ?: return ""
        return try {
            java.net.URI(normalized).host.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    fun isSilentWaGatewayConfigured(): Boolean {
        val url = normalizeHttpsGatewayUrl(BuildConfig.WEBHOOK_BASE_URL) ?: return false
        return url.isNotBlank() && !url.contains("example.com", ignoreCase = true)
    }

    /**
     * Mengirim permintaan lisensi ke server webhook pribadi secara diam-diam menggunakan
     * token sesi berumur pendek (/api/license/session -> /api/license/request).
     * Tidak ada secret statis di APK dan Kode API (KPRO-...) dibuat 100% di server.
     */
    suspend fun sendSilentLicenseRequestToAdminWa(
        deviceId: String,
        storeName: String,
        adminWaNumber: String,
        packageName: String = "com.mrapro.ksrpro"
    ): Boolean = sendSilentLicenseRequestDetailed(deviceId, storeName, adminWaNumber, packageName).success

    suspend fun sendSilentLicenseRequestDetailed(
        deviceId: String,
        storeName: String,
        adminWaNumber: String,
        packageName: String = "com.mrapro.ksrpro"
    ): WebhookDispatchResult = withContext(Dispatchers.IO) {
        val baseUrl = normalizeHttpsGatewayUrl(BuildConfig.WEBHOOK_BASE_URL)
            ?: return@withContext WebhookDispatchResult(
                success = false,
                detail = "WEBHOOK_BASE_URL harus berupa URL HTTPS yang valid."
            )
        if (!isSilentWaGatewayConfigured()) {
            return@withContext WebhookDispatchResult(
                success = false,
                detail = "WEBHOOK_BASE_URL belum dikonfigurasi."
            )
        }

        val normalizedId = extractDeviceIdFromText(deviceId) ?: deviceId.trim().uppercase()
        if (normalizedId.isBlank()) {
            return@withContext WebhookDispatchResult(
                success = false,
                detail = "ID Perangkat kosong."
            )
        }

        val cleanAdminWa = adminWaNumber.filter { it.isDigit() }.let { digits ->
            if (digits.startsWith("0")) "62${digits.substring(1)}" else digits
        }.ifBlank { DEFAULT_ADMIN_WA }

        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val nowMs = System.currentTimeMillis()

        return@withContext try {
            // Langkah 1: Minta token sesi berumur pendek (single-use) dari server
            val sessionPayload = JSONObject().apply {
                put("deviceId", normalizedId)
                put("device_id", normalizedId)
                put("packageName", packageName)
                put("timestamp", nowMs)
            }
            val sessionReq = Request.Builder()
                .url(buildSessionEndpoint(baseUrl))
                .post(sessionPayload.toString().toRequestBody(jsonMediaType))
                .build()

            val sessionToken = gatewayHttpClient.newCall(sessionReq).execute().use { resp ->
                val rawBody = resp.body?.string()?.trim().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext WebhookDispatchResult(
                        success = false,
                        statusCode = resp.code,
                        detail = if (rawBody.isNotBlank()) "Session HTTP ${resp.code}: ${rawBody.take(120)}" else "Session gagal (HTTP ${resp.code})"
                    )
                }
                val json = runCatching { JSONObject(rawBody) }.getOrNull()
                json?.optString("sessionToken")?.takeIf { it.isNotBlank() }
                    ?: json?.optString("token")?.takeIf { it.isNotBlank() }
                    ?: ""
            }

            if (sessionToken.isBlank()) {
                return@withContext WebhookDispatchResult(
                    success = false,
                    detail = "Server tidak mengembalikan sessionToken berumur pendek."
                )
            }

            // Langkah 2: Kirim permintaan lisensi dengan token sesi sementara (TANPA mengirim licenseKey dari klien)
            val requestPayload = JSONObject().apply {
                put("target", cleanAdminWa)
                put("deviceId", normalizedId)
                put("device_id", normalizedId)
                put("storeName", storeName.trim())
                put("store_name", storeName.trim())
                put("packageName", packageName)
                put("timestamp", System.currentTimeMillis())
            }
            val dispatchReq = Request.Builder()
                .url(buildRequestEndpoint(baseUrl))
                .addHeader("Authorization", "Bearer $sessionToken")
                .addHeader("X-Session-Token", sessionToken)
                .post(requestPayload.toString().toRequestBody(jsonMediaType))
                .build()

            gatewayHttpClient.newCall(dispatchReq).execute().use { response ->
                val bodySnippet = response.body?.string()?.trim()?.take(140).orEmpty()
                WebhookDispatchResult(
                    success = response.isSuccessful,
                    statusCode = response.code,
                    detail = if (bodySnippet.isNotBlank()) bodySnippet else response.message
                )
            }
        } catch (e: Exception) {
            WebhookDispatchResult(
                success = false,
                detail = e.localizedMessage ?: "Gagal terhubung ke server webhook."
            )
        }
    }

    /**
     * Memverifikasi Kode API secara server-side (/api/license/verify).
     * Jika server tidak terjangkau atau endpoint belum tersedia (404/offline),
     * jatuh kembali ke verifikasi lokal untuk mendukung generator PIN Admin offline.
     */
    suspend fun verifyAndActivateLicense(
        context: Context,
        inputKey: String
    ): ActivationResult = withContext(Dispatchers.IO) {
        val cleanedInput = extractLicenseKeyFromText(inputKey) ?: inputKey.trim().uppercase()
        if (cleanedInput.isBlank()) {
            return@withContext ActivationResult(
                success = false,
                message = "Harap masukkan Kode API terlebih dahulu."
            )
        }

        val deviceId = getOrCreateDeviceId(context)
        val baseUrl = normalizeHttpsGatewayUrl(BuildConfig.WEBHOOK_BASE_URL)

        if (baseUrl != null && isSilentWaGatewayConfigured()) {
            try {
                val payload = JSONObject().apply {
                    put("deviceId", deviceId)
                    put("device_id", deviceId)
                    put("licenseKey", cleanedInput)
                    put("license_key", cleanedInput)
                    put("packageName", context.packageName)
                }
                val req = Request.Builder()
                    .url(buildVerifyEndpoint(baseUrl))
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                gatewayHttpClient.newCall(req).execute().use { resp ->
                    val rawBody = resp.body?.string()?.trim().orEmpty()
                    val json = runCatching { JSONObject(rawBody) }.getOrNull()
                    val serverMsg = json?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: json?.optString("error")?.takeIf { it.isNotBlank() }

                    when {
                        resp.isSuccessful -> {
                            val isValid = json?.optBoolean("valid", false) == true
                            return@withContext if (isValid) {
                                activateFromServerVerification(context, cleanedInput)
                                ActivationResult(
                                    success = true,
                                    message = serverMsg ?: "Lisensi aktif permanen! Terverifikasi server.",
                                    verifiedByServer = true
                                )
                            } else {
                                ActivationResult(
                                    success = false,
                                    message = serverMsg ?: "Kode API ditolak server untuk perangkat $deviceId.",
                                    verifiedByServer = true
                                )
                            }
                        }
                        resp.code in 400..403 -> {
                            return@withContext ActivationResult(
                                success = false,
                                message = serverMsg ?: "Kode API tidak valid atau belum dikonfirmasi Admin.",
                                verifiedByServer = true
                            )
                        }
                        // 404 / 5xx -> lanjutkan ke fallback verifikasi lokal offline
                    }
                }
            } catch (_: Exception) {
                // Offline / timeout -> lanjut ke fallback lokal agar aktivasi offline tetap berjalan
            }
        }

        val localOk = activateWithKey(context, cleanedInput)
        return@withContext if (localOk) {
            ActivationResult(
                success = true,
                message = "Lisensi aktif permanen! Terima kasih telah membeli Kasir Pro.",
                verifiedByServer = false
            )
        } else {
            ActivationResult(
                success = false,
                message = "Kode API salah atau bukan untuk perangkat ini ($deviceId).",
                verifiedByServer = false
            )
        }
    }

    fun revokeLocalActivation(context: Context) {
        synchronized(lock) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val deviceId = getOrCreateDeviceId(context)
            val startMs = prefs.getLong(KEY_TRIAL_START_WALL_MS, System.currentTimeMillis())
            val adminWa = prefs.getString(KEY_ADMIN_WA, DEFAULT_ADMIN_WA) ?: DEFAULT_ADMIN_WA

            prefs.edit()
                .remove(KEY_ACTIVATED_KEY)
                .remove(KEY_SERVER_ACTIVATED_SIG)
                .putBoolean(KEY_TRIAL_EXPIRED, true)
                .commit()

            persistBackupState(context, deviceId, startMs, true, "", adminWa, "")
        }
    }

    /**
     * Memeriksa status aktivasi perangkat secara otomatis dari Website Admin (/api/license/status).
     * Ketika Admin menekan tombol Aktifkan di website, aplikasi langsung terbuka tanpa input kode manual.
     */
    suspend fun checkServerActivationStatus(context: Context): ServerStatusResult = withContext(Dispatchers.IO) {
        val baseUrl = normalizeHttpsGatewayUrl(BuildConfig.WEBHOOK_BASE_URL)
        if (baseUrl == null || !isSilentWaGatewayConfigured()) {
            return@withContext ServerStatusResult(
                reachable = false,
                isActivated = false,
                statusLabel = "UNCONFIGURED",
                message = "Server webhook belum dikonfigurasi."
            )
        }

        val deviceId = getOrCreateDeviceId(context)
        val encodedId = java.net.URLEncoder.encode(deviceId, "UTF-8")
        val encodedPkg = java.net.URLEncoder.encode(context.packageName, "UTF-8")
        val url = "${buildStatusEndpoint(baseUrl)}?deviceId=$encodedId&packageName=$encodedPkg"

        return@withContext try {
            val req = Request.Builder().url(url).get().build()
            gatewayHttpClient.newCall(req).execute().use { resp ->
                val rawBody = resp.body?.string()?.trim().orEmpty()
                val json = runCatching { JSONObject(rawBody) }.getOrNull()
                if (!resp.isSuccessful || json == null) {
                    return@withContext ServerStatusResult(
                        reachable = false,
                        isActivated = false,
                        statusLabel = "ERROR_${resp.code}",
                        message = "Belum dapat memeriksa status server (HTTP ${resp.code})."
                    )
                }

                val statusStr = json.optString("status", "").trim().uppercase()
                val isActivatedFlag = json.optBoolean("activated", false) ||
                    json.optBoolean("active", false) ||
                    statusStr in setOf("ACTIVE", "ACTIVATED", "PAID", "APPROVED")
                val isRevokedFlag = json.optBoolean("revoked", false) ||
                    statusStr in setOf("REVOKED", "BLOCKED", "DISABLED")
                val serverMsg = json.optString("message", "").trim()

                when {
                    isActivatedFlag -> {
                        val serverKeyOrProof = json.optString("licenseKey", "").trim()
                            .ifBlank { "WEB-$deviceId" }
                        activateFromServerVerification(context, serverKeyOrProof)
                        ServerStatusResult(
                            reachable = true,
                            isActivated = true,
                            statusLabel = "ACTIVE",
                            message = serverMsg.ifBlank { "Pembayaran dikonfirmasi Admin! Aplikasi aktif permanen." }
                        )
                    }
                    isRevokedFlag -> {
                        revokeLocalActivation(context)
                        ServerStatusResult(
                            reachable = true,
                            isActivated = false,
                            isRevoked = true,
                            statusLabel = "REVOKED",
                            message = serverMsg.ifBlank { "Lisensi perangkat ini dinonaktifkan dari panel Admin." }
                        )
                    }
                    else -> {
                        ServerStatusResult(
                            reachable = true,
                            isActivated = false,
                            statusLabel = statusStr.ifBlank { "PENDING" },
                            message = serverMsg.ifBlank { "Menunggu Admin mengaktifkan perangkat ini di website." }
                        )
                    }
                }
            }
        } catch (_: Exception) {
            ServerStatusResult(
                reachable = false,
                isActivated = false,
                statusLabel = "OFFLINE",
                message = "Tidak ada koneksi internet untuk memeriksa status aktivasi."
            )
        }
    }

    private fun readBackupJson(context: Context): JSONObject? {
        return try {
            val file = File(context.filesDir, BACKUP_FILE_NAME)
            if (!file.exists()) return null
            val raw = file.readText()
            if (raw.isBlank()) null else JSONObject(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun persistBackupState(
        context: Context,
        deviceId: String,
        trialStartMs: Long,
        trialExpired: Boolean,
        activatedKey: String,
        adminWa: String,
        serverActivatedSig: String = ""
    ) {
        try {
            val json = JSONObject().apply {
                put(KEY_DEVICE_ID, deviceId)
                put(KEY_TRIAL_START_WALL_MS, trialStartMs)
                put(KEY_TRIAL_EXPIRED, trialExpired)
                put(KEY_ACTIVATED_KEY, activatedKey)
                put(KEY_SERVER_ACTIVATED_SIG, serverActivatedSig)
                put(KEY_ADMIN_WA, adminWa)
            }
            val file = File(context.filesDir, BACKUP_FILE_NAME)
            file.writeText(json.toString())
        } catch (_: Exception) {
        }
    }
}
