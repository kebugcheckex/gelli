package com.dkanada.gramophone.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // --- estimateSize ---

    @Test
    fun estimateSize_fromBitRateAndDuration() {
        // 320 kbps for 5 minutes is 12 MB
        assertEquals(12_000_000L, SaveWorker.estimateSize(320_000, 300_000))
    }

    @Test
    fun estimateSize_unknown() {
        assertNull(SaveWorker.estimateSize(0, 300_000))
        assertNull(SaveWorker.estimateSize(320_000, 0))
    }

    // --- transcodeBitRate ---

    private val cap = 15L * 1024 * 1024

    @Test
    fun transcodeBitRate_shortTrack_usesHighestStep() {
        assertEquals(320_000, SaveWorker.transcodeBitRate(cap, 5 * 60_000))
    }

    @Test
    fun transcodeBitRate_picksHighestStepThatFits() {
        // 15 MiB over 10 minutes leaves about 199 kbps
        assertEquals(192_000, SaveWorker.transcodeBitRate(cap, 10 * 60_000))
        // and over 20 minutes about 99 kbps
        assertEquals(96_000, SaveWorker.transcodeBitRate(cap, 20 * 60_000))
    }

    @Test
    fun transcodeBitRate_longTrack_usesLowestStep() {
        assertEquals(64_000, SaveWorker.transcodeBitRate(cap, 120 * 60_000))
    }

    @Test
    fun transcodeBitRate_unknownDuration_usesHighestStep() {
        assertEquals(320_000, SaveWorker.transcodeBitRate(cap, 0))
    }

    // --- transcodeContainer ---

    @Test
    fun transcodeContainer_followsCodec() {
        assertEquals("mp3", SaveWorker.transcodeContainer("mp3"))
        assertEquals("aac", SaveWorker.transcodeContainer("aac"))
    }
}
