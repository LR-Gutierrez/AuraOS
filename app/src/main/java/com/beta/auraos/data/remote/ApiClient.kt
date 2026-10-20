package com.beta.auraos.data.remote

import com.beta.auraos.AuraApplication
import com.beta.auraos.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {

    /**
     * Placeholder every Retrofit service is built with. The real address can change at runtime
     * (or not exist at all), so [serverRouter] swaps it for [ServerConfig.baseUrl] per request.
     */
    const val BASE_URL: String = "http://aura.server/"

    /**
     * Shared by every Retrofit service so all calls get the same guard and timeouts. The
     * connect timeout is short on purpose: an unreachable server should send the operator to
     * local data in a few seconds, not leave the screen waiting half a minute. Reads and writes
     * get more room because photo uploads on a weak connection are slow but still progressing.
     */
    val okHttpClient: OkHttpClient by lazy {
        // Bodies carry passwords, tokens and plates: never log them outside debug builds.
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(localOnlyGuard)
            .addInterceptor(serverRouter)
            c.addInterceptor(sessionAuth)
            .addInterceptor(logging)
            .build()
    }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * An account created by the no-server setup exists only on this device: until the backend
     * accepts a login for it, nothing but that login may reach the server, or the device would
     * show (and cache over its own data) another account's branches and entries. Repositories
     * see the IOException as "offline" and serve local data.
     */
    private val localOnlyGuard = Interceptor { chain ->
        val request = chain.request()
        val path = request.url.encodedPath
        val allowed = path.endsWith("/login") || path.endsWith("/health")
        val localOnly = runCatching { AuraApplication.instance.sessionManager.isLocalOnlyAccount() }.getOrDefault(false)
        if (localOnly && !allowed) throw IOException("Local-only account: backend disabled until linked")
        chain.proceed(request)
    }

    /**
     * Sends placeholder requests to the configured server. With no server configured the call
     * fails as an IOException, which every repository already treats as "offline".
     */
    private val serverRouter = Interceptor { chain ->
        val request = chain.request()
        if (!request.url.toString().startsWith(BASE_URL)) return@Interceptor chain.proceed(request)
        val server = ServerConfig.baseUrl() ?: throw IOException("No server configured")
        val target = (server.toString() + request.url.toString().removePrefix(BASE_URL)).toHttpUrl()
        chain.proceed(request.newBuilder().url(target).build())
    }

    /**
     * Attaches the session token to calls bound for the configured server (the backend
     * requires it on everything but login and health). Calls that set their own header keep
     * it, and a 401 passes through so the sync coordinator can refresh the session.
     */
    private val sessionAuth = Interceptor { chain ->
        val request = chain.request()
        val server = ServerConfig.baseUrl()?.toString()
        val token = runCatching { AuraApplication.instance.sessionManager.getToken() }.getOrNull()
        if (server == null || token.isNullOrBlank() || request.header("Authorization") != null ||
            !request.url.toString().startsWith(server)
        ) {
            return@Interceptor chain.proceed(request)
        }
        chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
    }

    inline fun <reified T> createApi(): T = retrofit.create(T::class.java)
}
