package com.dkanada.gramophone.util

import com.dkanada.gramophone.model.NetworkPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkUtilTest {

    @Test
    fun isAllowed_noNetwork_neverAllowed() {
        for (policy in NetworkPolicy.values()) {
            assertFalse(NetworkUtil.isAllowed(policy, connected = false, unmetered = false))
            assertFalse(NetworkUtil.isAllowed(policy, connected = false, unmetered = true))
        }
    }

    @Test
    fun isAllowed_any_allowsMetered() {
        assertTrue(NetworkUtil.isAllowed(NetworkPolicy.ANY, connected = true, unmetered = false))
        assertTrue(NetworkUtil.isAllowed(NetworkPolicy.ANY, connected = true, unmetered = true))
    }

    @Test
    fun isAllowed_unmetered_needsUnmetered() {
        assertFalse(NetworkUtil.isAllowed(NetworkPolicy.UNMETERED, connected = true, unmetered = false))
        assertTrue(NetworkUtil.isAllowed(NetworkPolicy.UNMETERED, connected = true, unmetered = true))
    }
}
