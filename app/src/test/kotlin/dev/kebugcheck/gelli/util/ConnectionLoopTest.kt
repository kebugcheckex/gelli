package dev.kebugcheck.gelli.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionLoopTest {

    private val online = ConnectionState.Online("https://music.example.com")

    /** A loop whose attempts answer from [answers] in turn, repeating the last one. */
    private class Harness(scope: CoroutineScope, vararg answers: ConnectionState?) {
        private val queue = answers.toMutableList()
        val attempts = mutableListOf<Long>()
        val results = mutableListOf<Pair<ConnectionState, ConnectionState>>()
        var retryAllowed = true
        var clock: () -> Long = { 0 }

        val loop = ConnectionLoop(
            scope = scope,
            attempt = {
                attempts += clock()
                if (queue.size > 1) queue.removeAt(0) else queue.first()
            },
            mayRetry = { retryAllowed },
            onResult = { previous, current -> results += previous to current },
        )
    }

    // advanceUntilIdle leaves the background scope alone, so time moves on by hand: past any retry
    private fun TestScope.settle() = advanceTimeBy(3_600_000)

    private fun TestScope.harness(vararg answers: ConnectionState?) =
        Harness(backgroundScope, *answers).also { it.clock = { currentTime } }

    @Test
    fun anAnsweringServerGoesOnline() = runTest {
        val h = harness(online)

        h.loop.run()
        runCurrent()

        assertEquals(online, h.loop.state)
        assertEquals(listOf(ConnectionState.Connecting to online), h.results)
    }

    @Test
    fun unreachableRetriesOnTheBackoffSchedule() = runTest {
        val h = harness(ConnectionState.Unreachable)

        h.loop.run()
        advanceTimeBy(1_500_000)

        // 30 s, 1 min, 2 min, then every 5 min
        assertEquals(listOf(0L, 30_000, 90_000, 210_000, 510_000, 810_000, 1_110_000, 1_410_000), h.attempts)
        assertEquals(ConnectionState.Unreachable, h.loop.state)
    }

    @Test
    fun retriesStopOnceAnAddressAnswers() = runTest {
        val h = harness(ConnectionState.Unreachable, ConnectionState.Unreachable, online)

        h.loop.run()
        settle()

        assertEquals(listOf(0L, 30_000, 90_000), h.attempts)
        assertEquals(online, h.loop.state)
        assertEquals(
            listOf(
                ConnectionState.Connecting to ConnectionState.Unreachable,
                ConnectionState.Unreachable to ConnectionState.Unreachable,
                ConnectionState.Unreachable to online,
            ),
            h.results
        )
    }

    @Test
    fun aRejectedTokenIsNotRetried() = runTest {
        val h = harness(ConnectionState.Rejected)

        h.loop.run()
        settle()

        assertEquals(listOf(0L), h.attempts)
        assertEquals(ConnectionState.Rejected, h.loop.state)
    }

    @Test
    fun retriesPauseWhenNotAllowedAndWakeResumesThem() = runTest {
        val h = harness(ConnectionState.Unreachable)
        h.retryAllowed = false

        h.loop.run()
        advanceTimeBy(600_000)
        assertEquals(listOf(0L), h.attempts)

        h.retryAllowed = true
        h.loop.wake()
        runCurrent()
        advanceTimeBy(30_001)

        // an attempt right away, then the schedule from its start
        assertEquals(listOf(0L, 600_000, 630_000), h.attempts)
    }

    @Test
    fun wakeLeavesAPendingRetryAlone() = runTest {
        val h = harness(ConnectionState.Unreachable)

        h.loop.run()
        advanceTimeBy(10_000)
        h.loop.wake()
        advanceTimeBy(25_000)

        assertEquals(listOf(0L, 30_000), h.attempts)
    }

    @Test
    fun wakeDoesNothingWhileOnline() = runTest {
        val h = harness(online)

        h.loop.run()
        runCurrent()
        h.loop.wake()
        settle()

        assertEquals(listOf(0L), h.attempts)
    }

    @Test
    fun aNewRunReplacesThePendingRetryAndStartsTheScheduleOver() = runTest {
        val h = harness(ConnectionState.Unreachable, ConnectionState.Unreachable, ConnectionState.Unreachable, online)

        h.loop.run()
        advanceTimeBy(100_000)
        // attempts at 0, 30 s and 90 s, with the next one due at 210 s, then the network changes
        h.loop.run(delayMs = 1_000)
        advanceTimeBy(1_001)

        assertEquals(listOf(0L, 30_000, 90_000, 101_000), h.attempts)
        assertEquals(online, h.loop.state)
    }

    @Test
    fun aNewRunCancelsAnAttemptInProgress() = runTest {
        var calls = 0
        val results = mutableListOf<ConnectionState>()
        val loop = ConnectionLoop(
            scope = backgroundScope,
            attempt = {
                // the first attempt hangs on a dead address until the second takes over
                if (calls++ == 0) delay(10_000)
                online
            },
            mayRetry = { true },
            onResult = { _, current -> results += current },
        )

        loop.run()
        advanceTimeBy(1_000)
        loop.run()
        settle()

        assertEquals(listOf<ConnectionState>(online), results)
    }

    @Test
    fun noAccountLeavesTheStateAlone() = runTest {
        val h = harness(null)

        h.loop.run()
        settle()

        assertEquals(ConnectionState.Connecting, h.loop.state)
        assertEquals(emptyList<Pair<ConnectionState, ConnectionState>>(), h.results)
    }

    @Test
    fun resetGoesBackToConnectingAndDropsRetries() = runTest {
        val h = harness(ConnectionState.Unreachable)

        h.loop.run()
        runCurrent()
        h.loop.reset()
        advanceTimeBy(600_000)

        assertEquals(listOf(0L), h.attempts)
        assertEquals(ConnectionState.Connecting, h.loop.state)
        assertEquals(ConnectionState.Unreachable to ConnectionState.Connecting, h.results.last())
    }
}
