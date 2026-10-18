package com.beta.auraos.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerConfigTest {

    @Test
    fun addsSchemeAndTrailingSlash() {
        assertEquals("http://192.168.1.10:3000/", ServerConfig.normalize("192.168.1.10:3000"))
        assertEquals("https://api.example.com/", ServerConfig.normalize(" https://api.example.com "))
    }

    @Test
    fun keepsPathPrefix() {
        assertEquals("https://example.com/parking/", ServerConfig.normalize("https://example.com/parking"))
    }

    @Test
    fun dropsQueryAndFragment() {
        assertEquals("http://host:3000/", ServerConfig.normalize("http://host:3000/?a=1#x"))
    }

    @Test
    fun rejectsBlankAndGarbage() {
        assertNull(ServerConfig.normalize("   "))
        assertNull(ServerConfig.normalize("not a url"))
        assertNull(ServerConfig.normalize("ftp://host"))
    }
}
