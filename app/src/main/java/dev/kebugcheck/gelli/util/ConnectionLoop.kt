package dev.kebugcheck.gelli.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** How the app reaches the server of the current account. */
sealed interface ConnectionState {
    /** No address has been picked yet. Requests go to the address used last time meanwhile. */
    data object Connecting : ConnectionState

    /** The session talks to this address. */
    data class Online(val address: String) : ConnectionState

    /** No address of the server answered. Only stored songs play, and the app keeps trying. */
    data object Unreachable : ConnectionState

    /** The server turned down the account's token, so the account has to sign in again. */
    data object Rejected : ConnectionState
}

/**
 * Runs connection attempts one at a time and keeps the state they lead to. A new attempt replaces one in
 * progress. While no address answers it tries again on a growing interval, as long as [mayRetry] allows,
 * and otherwise waits for [wake].
 *
 * [attempt] gets the state before it and whether to pick from every address ([run]) or only to check the
 * one in use ([check]), and returns the new state, or null to leave the state as it is, e.g. when no account
 * is signed in. [onResult] hears every state an attempt or [reset] leads to, also when it
 * didn't change.
 */
internal class ConnectionLoop(
    private val scope: CoroutineScope,
    private val attempt: suspend (previous: ConnectionState, full: Boolean) -> ConnectionState?,
    private val mayRetry: () -> Boolean,
    private val onResult: (previous: ConnectionState, current: ConnectionState) -> Unit,
) {
    @Volatile
    var state: ConnectionState = ConnectionState.Connecting
        private set

    private var job: Job? = null

    /** Attempts after [delayMs], in place of an attempt or retry already pending. */
    fun run(delayMs: Long = 0) {
        synchronized(this) { launch(delayMs, full = true) }
    }

    /**
     * Checks the address in use after [delayMs], e.g. after a request to it failed. An attempt already pending
     * settles the state as well, so it is left to do that, which also turns a burst of failures into one check.
     * Returns whether this started a check.
     */
    fun check(delayMs: Long): Boolean = synchronized(this) {
        if (job?.isActive == true) return false
        launch(delayMs, full = false)
        true
    }

    private fun launch(delayMs: Long, full: Boolean) {
        job?.cancel()
        job = scope.launch {
            delay(delayMs)

            var failures = 0
            var pickFromAll = full
            while (true) {
                val previous = state
                val next = attempt(previous, pickFromAll) ?: return@launch
                // a newer attempt took over while this one ran
                ensureActive()

                state = next
                onResult(previous, next)
                if (next != ConnectionState.Unreachable) return@launch

                // the address in use is gone, so retries look at every address
                pickFromAll = true
                delay(RETRY_DELAYS_MS[minOf(failures++, RETRY_DELAYS_MS.lastIndex)])
                if (!mayRetry()) return@launch
            }
        }
    }

    /** Attempts now if no address answered and retrying had stopped, e.g. as the app comes back to the foreground. */
    fun wake() {
        synchronized(this) {
            if (state == ConnectionState.Unreachable && job?.isActive != true) run()
        }
    }

    /** Drops any pending attempt and goes back to [ConnectionState.Connecting], e.g. for another account. */
    fun reset() {
        val previous = synchronized(this) {
            job?.cancel()
            job = null
            state.also { state = ConnectionState.Connecting }
        }
        onResult(previous, ConnectionState.Connecting)
    }

    companion object {
        /** The waits between attempts while no address answers. The last one repeats. */
        val RETRY_DELAYS_MS = longArrayOf(30_000, 60_000, 120_000, 300_000)
    }
}
