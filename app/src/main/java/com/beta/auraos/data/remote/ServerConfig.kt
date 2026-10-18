package com.beta.auraos.data.remote

import com.beta.auraos.AuraApplication
import com.beta.auraos.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Where the backend lives, if anywhere. AuraOS is usable without one, so the address is
 * optional: the one typed in the app wins, then the one compiled in from local.properties,
 * and with neither the app runs on this device only.
 */
object ServerConfig {

    private val sessionManager get() = AuraApplication.instance.sessionManager

    fun baseUrl(): HttpUrl? {
        val typed = runCatching { sessionManager.getServerUrl() }.getOrNull()
        return (typed ?: BuildConfig.API_BASE_URL).takeIf { it.isNotBlank() }?.toHttpUrlOrNull()
    }

    /** Saves [url] as already passed through [normalize]; an empty string means "no server". */
    fun save(url: String) = sessionManager.setServerUrl(url)

    /**
     * "192.168.1.10:3000" → "http://192.168.1.10:3000/". Null when the text cannot be a
     * server address; blank input is not an address either (callers treat it as "no server").
     */
    fun normalize(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        val url = (if ("://" in text) text else "http://$text").toHttpUrlOrNull() ?: return null
        if (url.host.isBlank()) return null
        val clean = url.newBuilder().query(null).fragment(null).build().toString()
        return if (clean.endsWith("/")) clean else "$clean/"
    }
}
