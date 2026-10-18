package com.dkanada.gramophone.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveWorkerTest {

    // --- isRetryable ---

    @Test
    fun isRetryable_serverErrors() {
        assertTrue(SaveWorker.isRetryable(500))
        assertTrue(SaveWorker.isRetryable(503))
    }

    @Test
    fun isRetryable_timeoutAndRateLimit() {
        assertTrue(SaveWorker.isRetryable(408))
        assertTrue(SaveWorker.isRetryable(429))
    }

    @Test
    fun isRetryable_clientErrors() {
        assertFalse(SaveWorker.isRetryable(401))
        assertFalse(SaveWorker.isRetryable(404))
    }

    // --- fileExtension ---

    @Test
    fun fileExtension_plainContainer() {
        assertEquals("flac", SaveWorker.fileExtension("flac"))
        assertEquals("mp3", SaveWorker.fileExtension("MP3"))
    }

    @Test
    fun fileExtension_containerList_usesFirstEntry() {
        assertEquals("mov", SaveWorker.fileExtension("mov,mp4,m4a,3gp,3g2,mj2"))
    }

    @Test
    fun fileExtension_missingOrUnsafe_fallsBack() {
        assertEquals("bin", SaveWorker.fileExtension(null))
        assertEquals("bin", SaveWorker.fileExtension(""))
        assertEquals("bin", SaveWorker.fileExtension("../x"))
    }
}
