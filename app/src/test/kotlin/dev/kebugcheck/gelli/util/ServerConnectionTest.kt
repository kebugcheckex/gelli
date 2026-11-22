package dev.kebugcheck.gelli.util

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
    fun theFirstAddressInPriorityOrderWins() = runBlocking {
        val endpoint = resolve(serverId,
            lan to { delay(10); serverId },
            tunnel to { delay(10); serverId })

        assertEquals(ServerConnection.Endpoint(lan, serverId), endpoint)
    }

    @Test
    fun aHigherAddressThatAnswersLaterStillWins() = runBlocking {
        val endpoint = resolve(serverId,
            tunnel to { delay(300); serverId },
            lan to { delay(10); serverId })

        assertEquals(tunnel, endpoint?.address)
    }

    @Test
    fun aHigherAddressThatAnswersDoesNotWaitForLowerOnes() = runBlocking {
        val started = System.nanoTime()
        val endpoint = resolve(serverId,
            lan to { delay(10); serverId },
            tunnel to { delay(10_000); serverId })

        assertEquals(lan, endpoint?.address)
        assertTrue((System.nanoTime() - started) / 1_000_000 < 1_000)
    }

    @Test
    fun anUnreachableAddressHoldsUpLowerOnesOnlyUntilItsTimeout() = runBlocking {
        val started = System.nanoTime()
        val endpoint = resolve(serverId,
            lan to { delay(10_000); serverId },
            tunnel to { delay(10); serverId })
        val elapsed = (System.nanoTime() - started) / 1_000_000

        assertEquals(tunnel, endpoint?.address)
        assertTrue(elapsed in 2_000 until 3_000)
    }

    @Test
    fun deadAddressesInBetweenAreSkipped() = runBlocking {
        val third = "https://backup.example.com"
        val endpoint = resolve(serverId,
            lan to { throw java.io.IOException("refused") },
            tunnel to { null },
            third to { delay(10); serverId })

        assertEquals(third, endpoint?.address)
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
