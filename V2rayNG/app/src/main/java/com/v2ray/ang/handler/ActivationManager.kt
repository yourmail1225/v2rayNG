package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.util.ActivationCodec
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client for the first-launch activation gate. Talks to the Python panel's
 * /api/activate (validates a per-user activation code or the master password and
 * optionally returns the subscription to import) and /api/active (best-effort
 * heartbeat so the panel can show online/active state).
 */
object ActivationManager {

    private const val PREF_ACTIVATION_DONE = "pref_activation_done"
    private const val PREF_ACTIVATION_DEVICE_ID = "pref_activation_device_id"
    private const val PREF_ACTIVATION_SERVER = "pref_activation_server"
    private const val PREF_ACTIVATION_CODE = "pref_activation_code"
    private const val PREF_ACTIVATION_MODE = "pref_activation_mode"

    const val DEFAULT_SERVER = "http://127.0.0.1:5050"

    const val MODE_CODE = "code"
    const val MODE_MASTER = "master"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun isActivated(): Boolean = MmkvManager.decodeSettingsBool(PREF_ACTIVATION_DONE, false)

    private fun deviceId(): String {
        var id = MmkvManager.decodeSettingsString(PREF_ACTIVATION_DEVICE_ID)
        if (id.isNullOrBlank()) {
            id = Utils.getUuid()
            MmkvManager.encodeSettings(PREF_ACTIVATION_DEVICE_ID, id)
        }
        return id
    }

    private fun serverUrl(): String =
        MmkvManager.decodeSettingsString(PREF_ACTIVATION_SERVER)?.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER

    private fun savedCode(): String = MmkvManager.decodeSettingsString(PREF_ACTIVATION_CODE).orEmpty()

    /**
     * Resolves the panel server from the optional Base64 configuration field and
     * persists it. When the field is blank the previously stored or default address
     * is kept.
     */
    fun resolveServer(configCode: String?): String {
        val url = parseSetup(configCode)?.server?.takeIf { it.isNotBlank() } ?: serverUrl()
        MmkvManager.encodeSettings(PREF_ACTIVATION_SERVER, url)
        return url
    }

    fun markActivated(mode: String, code: String) {
        MmkvManager.encodeSettings(PREF_ACTIVATION_DONE, true)
        MmkvManager.encodeSettings(PREF_ACTIVATION_MODE, mode)
        MmkvManager.encodeSettings(PREF_ACTIVATION_CODE, code)
    }

    /**
     * Sends the activation request. A success does not mark the app activated; the
     * caller must import the returned subscription (if any) and then call
     * [markActivated].
     */
    internal suspend fun activate(code: String, configCode: String?): ActivationOutcome = withContext(Dispatchers.IO) {
        if (code.isBlank()) {
            return@withContext ActivationOutcome.Error(ActivationErrorKind.DENIED)
        }
        val server = resolveServer(configCode)
        val request = ActivationRequest(
            code = code.trim(),
            device_id = deviceId(),
            app_version = BuildConfig.VERSION_NAME,
        )
        try {
            val body = executePost("$server/api/activate", JsonUtil.toJson(request))
            val response = JsonUtil.fromJsonSafe(body, ActivationResponse::class.java)
            when {
                response == null -> ActivationOutcome.Error(ActivationErrorKind.GENERIC)
                !response.ok -> ActivationOutcome.Error(classifyError(response.error, response.url))
                response.mode == "master" -> ActivationOutcome.Master
                else -> ActivationOutcome.Subscription(response.url, response.row)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Activation request failed for server=$server", e)
            ActivationOutcome.Error(ActivationErrorKind.NETWORK)
        }
    }

    /** Best-effort heartbeat; failures are logged and swallowed (status refresh is optional). */
    suspend fun reportActive() {
        if (!isActivated()) return
        val code = savedCode()
        if (code.isEmpty()) return
        withContext(Dispatchers.IO) {
            try {
                executePost(
                    "${serverUrl()}/api/active",
                    JsonUtil.toJson(ActivationRequest(code, deviceId(), BuildConfig.VERSION_NAME)),
                )
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Activation heartbeat failed", e)
            }
        }
    }

    private fun executePost(url: String, body: String): String {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return text
        }
    }

    private fun classifyError(serverError: String, url: String): ActivationErrorKind {
        if (url.contains("limit", ignoreCase = true) ||
            serverError.contains("limit", ignoreCase = true)
        ) {
            return ActivationErrorKind.LIMIT
        }
        return ActivationErrorKind.DENIED
    }

    /**
     * Parses the optional setup code. A blank or malformed field is the ordinary
     * "use the default server" case rather than a fault, so nothing is logged and JVM
     * unit tests (which have no android.util.Log) can exercise the failure branch.
     */
    internal fun parseSetup(configCode: String?): ActivationSetup? {
        if (configCode.isNullOrBlank()) return null
        return try {
            val raw = ActivationCodec.decode(configCode.trim())
            JsonUtil.fromJsonSafe(String(raw, Charsets.UTF_8), ActivationSetup::class.java)
        } catch (e: Exception) {
            null
        }
    }
}

internal enum class ActivationErrorKind { NONE, NETWORK, DENIED, LIMIT, GENERIC }

internal sealed class ActivationOutcome {
    object Master : ActivationOutcome()
    data class Subscription(val url: String, val row: String) : ActivationOutcome()
    data class Error(val kind: ActivationErrorKind) : ActivationOutcome()
}

internal data class ActivationSetup(val v: Int = 0, val s: String? = null) {
    val server: String? get() = s?.takeIf { it.isNotBlank() }
}

private data class ActivationRequest(
    val code: String,
    val device_id: String,
    val app_version: String,
)

private data class ActivationResponse(
    val ok: Boolean = false,
    val mode: String = "",
    val already: Boolean = false,
    val url: String = "",
    val row: String = "",
    val error: String = "",
)