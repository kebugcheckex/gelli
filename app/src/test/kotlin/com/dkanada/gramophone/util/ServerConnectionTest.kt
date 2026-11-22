package com.dkanada.gramophone.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConnectionTest {

    private val serverId = "0123456789abcdef0123456789abcdef"
    private val lan = "http://192.168.1.10:8096"
    private val tunnel = "https://music.example.com"

    private suspend fun resolve(serverId: String?, vararg answers: Pair<String, suspend () -> String?>) =
        ServerConnection.resolve(answers.map { it.first }, serverId, 2_000) { address ->
            answers.first { it.first == address }.second()
        }

    @Test
    fun theFirstAddressToAnswerWins() = runBlocking {
        val endpoint = resolve(serverId,
            tunnel to { delay(200); serverId },
            lan to { delay(10); serverId })

        assertEquals(ServerConnection.Endpoint(lan, serverId), endpoint)
    }

    @Test
    fun anUnreachableAddressDoesNotHoldUpAnother() = runBlocking {
        val started = System.nanoTime()
        val endpoint = resolve(serverId,
            lan to { delay(10_000); serverId },
            tunnel to { delay(10); serverId })

        assertEquals(tunnel, endpoint?.address)
        assertTrue((System.nanoTime() - started) / 1_000_000 < 1_000)
    }

    @Test
    fun anotherServerAtAnAddressIsPassedOver() = runBlocking {
        val endpoint = resolve(serverId,
            lan to { "ffffffffffffffffffffffffffffffff" },
            tunnel to { delay(50); serverId })

        assertEquals(tunnel, endpoint?.address)
    }

    @Test
    fun failedProbesArePassedOver() = runBlocking {
        val endpoint = resolve(serverId,
            lan to { throw java.io.IOException("refused") },
            tunnel to { delay(50); serverId })

        assertEquals(tunnel, endpoint?.address)
    }

    @Test
    fun anUnknownServerIdAcceptsAnyServer() = runBlocking {
        val endpoint = resolve(null, lan to { "01234567-89AB-CDEF-0123-456789ABCDEF" })

        assertEquals(ServerConnection.Endpoint(lan, serverId), endpoint)
    }

    @Test
    fun noAnswerResolvesToNull() = runBlocking {
        assertNull(resolve(serverId, lan to { null }, tunnel to { throw java.io.IOException("refused") }))
    }

    @Test
    fun theTimeoutEndsTheProbe() = runBlocking {
        assertNull(resolve(serverId, lan to { delay(10_000); serverId }))
    }
}
