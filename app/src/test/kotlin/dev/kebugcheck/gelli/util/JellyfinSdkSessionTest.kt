package dev.kebugcheck.gelli.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class JellyfinSdkSessionTest {

    private val lan = "http://192.168.1.10:8096"
    private val tunnel = "https://music.example.com/jellyfin"
    private val token = "0123456789abcdef0123456789abcdef"
    private val userId = "fedcba9876543210fedcba9876543210"

    private val stream = "/Audio/abc/universal?UserId=$userId&Container=mp3,flac&ApiKey=$token"

    @After
    fun tearDown() = JellyfinSdkSession.resetForTest()

    private fun rewrite(url: String, vararg addresses: String, current: String) =
        JellyfinSdkSession.rewriteAddress(url, addresses.toList(), current)

    @Test
    fun anEarlierAddressMovesToTheCurrentOneWithPathAndQueryKept() {
        assertEquals("$tunnel$stream", rewrite("$lan$stream", lan, current = tunnel))
        assertEquals("$lan$stream", rewrite("$tunnel$stream", tunnel, current = lan))
    }

    @Test
    fun hlsSegmentsMoveToo() {
        val segment = "/videos/abc/hls1/main/0.ts?PlaySessionId=1&ApiKey=$token"
        assertEquals("$tunnel$segment", rewrite("$lan$segment", lan, current = tunnel))
    }

    @Test
    fun aUrlOnTheCurrentAddressStaysAsItIs() {
        assertEquals("$tunnel$stream", rewrite("$tunnel$stream", lan, tunnel, current = tunnel))
    }

    @Test
    fun otherHostsAndFilesStayAsTheyAre() {
        assertEquals("https://other.example.com$stream", rewrite("https://other.example.com$stream", lan, current = tunnel))
        assertEquals("file:///data/audio/abc.mp3", rewrite("file:///data/audio/abc.mp3", lan, current = tunnel))
    }

    @Test
    fun anAddressOnlyMatchesUpToAPathBoundary() {
        // a longer host or port is another server
        assertEquals("http://192.168.1.10:80961/Audio", rewrite("http://192.168.1.10:80961/Audio", lan, current = tunnel))
        assertEquals("https://music.example.com/jellyfin2/Audio", rewrite("https://music.example.com/jellyfin2/Audio", tunnel, current = lan))
    }

    @Test
    fun theLongestMatchingAddressWins() {
        val root = "https://music.example.com"
        assertEquals("$lan$stream", rewrite("$tunnel$stream", root, tunnel, current = lan))
        assertEquals("$lan/jellyfin$stream", rewrite("$root/jellyfin$stream", root, current = lan))
    }

    @Test
    fun theSchemeAndHostMatchRegardlessOfCase() {
        assertEquals("$tunnel$stream", rewrite("HTTP://192.168.1.10:8096$stream", lan, current = tunnel))
    }

    @Test
    fun theSessionRewritesUrlsOfEveryAddressItUsed() {
        JellyfinSdkSession.updateSession("$lan/", token, userId)
        val queued = "$lan$stream"

        JellyfinSdkSession.switchAddress(userId, token, tunnel)
        assertEquals("$tunnel$stream", JellyfinSdkSession.toCurrentAddress(queued))

        // and back again
        JellyfinSdkSession.switchAddress(userId, token, lan)
        assertEquals(queued, JellyfinSdkSession.toCurrentAddress("$tunnel$stream"))
    }

    @Test
    fun reconnectingWithTheSameCredentialsKeepsTheEarlierAddresses() {
        JellyfinSdkSession.updateSession(lan, token, userId)
        JellyfinSdkSession.updateSession(tunnel, token, userId)

        assertEquals("$tunnel$stream", JellyfinSdkSession.toCurrentAddress("$lan$stream"))
    }

    @Test
    fun anotherAccountForgetsTheEarlierAddresses() {
        JellyfinSdkSession.updateSession(lan, token, userId)
        JellyfinSdkSession.updateSession(tunnel, "another-token", "another-user")

        // the old account's urls carry its token, so they don't belong to this session
        assertEquals("$lan$stream", JellyfinSdkSession.toCurrentAddress("$lan$stream"))
    }

    @Test
    fun noSessionLeavesUrlsAlone() {
        JellyfinSdkSession.updateSession(lan, token, userId)
        JellyfinSdkSession.clearSession()

        assertEquals("$lan$stream", JellyfinSdkSession.toCurrentAddress("$lan$stream"))
    }
}
