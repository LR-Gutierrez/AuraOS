package com.beta.auraos.util

import com.beta.auraos.data.remote.ServerConfig

object PhotoUrlBuilder {

    fun build(relativePath: String?): String? {
        if (relativePath.isNullOrBlank()) return null
        val path = relativePath.trimStart('/')
        val server = ServerConfig.baseUrl() ?: return null
        return server.toString() + path
    }
}
