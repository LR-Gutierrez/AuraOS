package com.beta.auraos.util

import com.beta.auraos.data.remote.ApiClient

object PhotoUrlBuilder {

    fun build(relativePath: String?): String? {
        if (relativePath.isNullOrBlank()) return null
        val path = relativePath.trimStart('/')
        return ApiClient.BASE_URL + path
    }
}
