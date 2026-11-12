package com.kaung139abc.hyperrecap

import android.content.Context
import android.provider.Settings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object LicenseManager {
    const val SERVER_URL = "https://kaung139abc.workers.dev"
    private const val PREFS = "hyper_recap_license"
    private const val KEY = "license_key"
    private const val VALIDATED_AT = "validated_at"
    private const val CACHED_PLAN = "cached_plan"
    private const val CACHED_EXPIRY = "cached_expiry"
    private const val OFFLINE_GRACE_MS = 7L * 24L * 60L * 60L * 1000L

    data class Result(val valid: Boolean, val message: String, val plan: String = "", val expiresAt: String? = null)

    fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown-device"

    fun savedKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun activate(context: Context, licenseKey: String): Result = request(context, "/v1/activate", licenseKey)

    fun check(context: Context): Result {
        val key = savedKey(context)
        if (key.isBlank()) return Result(false, "LICENSE_REQUIRED")
        return request(context, "/v1/check", key)
    }

    private fun request(context: Context, path: String, licenseKey: String): Result {
        if (SERVER_URL.contains("YOUR-WORKER")) {
            return Result(false, "License Server မတပ်ဆင်ရသေးပါ။ Server URL ထည့်သွင်းရန်လိုသည်။")
        }
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(SERVER_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 12_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            val payload = JSONObject()
                .put("licenseKey", licenseKey.trim())
                .put("deviceId", deviceId(context))
                .toString()
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            val json = JSONObject(response)
            if (json.optBoolean("valid", false)) {
                val plan = json.optString("plan", "")
                val expiry = json.optString("expiresAt").takeIf { it.isNotBlank() && it != "null" }
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY, licenseKey.trim())
                    .putLong(VALIDATED_AT, System.currentTimeMillis())
                    .putString(CACHED_PLAN, plan)
                    .putString(CACHED_EXPIRY, expiry ?: "")
                    .apply()
                Result(true, "License အတည်ပြုပြီးပါပြီ", plan, expiry)
            } else {
                val reason = json.optString("reason", json.optString("error", "LICENSE_INVALID"))
                Result(false, when (reason) {
                    "EXPIRED" -> "License သက်တမ်းကုန်သွားပါပြီ။"
                    "DEVICE_LIMIT" -> "ဒီ License ရဲ့ Device အရေအတွက် ပြည့်နေပါပြီ။"
                    "DEVICE_NOT_ACTIVATED" -> "ဒီဖုန်းမှာ License မဖွင့်ရသေးပါ။"
                    "INVALID_OR_REVOKED" -> "License Key မမှန်ပါ သို့မဟုတ် ပိတ်ထားပါသည်။"
                    else -> reason
                })
            }
        } catch (_: Exception) {
            offlineGrace(context, licenseKey)
        } finally {
            connection?.disconnect()
        }
    }

    private fun offlineGrace(context: Context, requestedKey: String): Result {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY, "") ?: ""
        val validatedAt = prefs.getLong(VALIDATED_AT, 0L)
        val age = System.currentTimeMillis() - validatedAt
        if (saved.isBlank() || !saved.equals(requestedKey.trim(), ignoreCase = true) ||
            validatedAt <= 0L || age < 0L || age > OFFLINE_GRACE_MS) {
            return Result(false, "Server ချိတ်ဆက်မရပါ။ Internet လိုအပ်သည် သို့မဟုတ် Offline grace သက်တမ်းကုန်နေပါပြီ။")
        }
        val expiry = prefs.getString(CACHED_EXPIRY, "")?.takeIf { it.isNotBlank() }
        if (expiry != null) {
            val expiryTime = try { java.time.Instant.parse(expiry).toEpochMilli() } catch (_: Exception) { 0L }
            if (expiryTime <= System.currentTimeMillis()) return Result(false, "License သက်တမ်းကုန်သွားပါပြီ။")
        }
        val plan = prefs.getString(CACHED_PLAN, "") ?: ""
        return Result(true, "Offline အသုံးပြုခွင့်", plan, expiry)
    }
}
