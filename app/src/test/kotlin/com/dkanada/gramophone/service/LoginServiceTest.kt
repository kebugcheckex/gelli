package com.dkanada.gramophone.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the pure pieces of [LoginService].
 *
 * The legacy [LoginService] reported the same two client capabilities on every
 * session restore (supportsMediaControl=true, supportsPersistentIdentifier=true).
 * Phase 5C migrates that call to `sessionApi.postCapabilities(...)`; this test
 * pins those default values so a silent flip of either flag during future
 * refactors would fail the build instead of degrading remote-control behavior
 * against a live server (where it is much harder to notice).
 */
class LoginServiceTest {

    @Test
    fun defaultCapabilities_supportsMediaControl_isTrue() {
        assertTrue(
            "media control must remain true to preserve remote command parity with the legacy client",
            LoginService.DEFAULT_CAPABILITIES.supportsMediaControl
        )
    }

    @Test
    fun defaultCapabilities_supportsPersistentIdentifier_isTrue() {
        assertTrue(
            "persistent identifier must remain true to match the legacy ClientCapabilities defaults",
            LoginService.DEFAULT_CAPABILITIES.supportsPersistentIdentifier
        )
    }

    @Test
    fun defaultCapabilities_matchLegacyClientCapabilities() {
        // Mirrors the legacy LoginService.java setup before Phase 5C.
        val expected = LoginService.Companion.ClientCapabilityArgs(
            supportsMediaControl = true,
            supportsPersistentIdentifier = true
        )
        assertEquals(expected, LoginService.DEFAULT_CAPABILITIES)
    }
}
