package com.dkanada.gramophone.service

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueManagerTest {

    private fun find(size: Int, from: Int, step: Int, wrap: Boolean, vararg playable: Int): Int =
        QueueManager.findPlayable(size, from, step, wrap) { it in playable }

    @Test
    fun findPlayable_forward_returnsNearestAfter() {
        assertEquals(3, find(5, 0, 1, false, 0, 3, 4))
    }

    @Test
    fun findPlayable_forwardWithoutWrap_stopsAtEnd() {
        assertEquals(-1, find(5, 3, 1, false, 0, 1))
    }

    @Test
    fun findPlayable_forwardWithWrap_continuesFromStart() {
        assertEquals(1, find(5, 3, 1, true, 1))
    }

    @Test
    fun findPlayable_wrap_neverReturnsFrom() {
        assertEquals(-1, find(5, 2, 1, true, 2))
    }

    @Test
    fun findPlayable_backward_returnsNearestBefore() {
        assertEquals(1, find(5, 4, -1, false, 0, 1, 4))
        assertEquals(-1, find(5, 1, -1, false, 2, 3))
        assertEquals(3, find(5, 1, -1, true, 3))
    }

    @Test
    fun findPlayable_singleSong_returnsNone() {
        assertEquals(-1, find(1, 0, 1, true, 0))
    }

    @Test
    fun findPlayable_nothingPlayable_returnsNone() {
        assertEquals(-1, find(5, 0, 1, true))
    }
}
