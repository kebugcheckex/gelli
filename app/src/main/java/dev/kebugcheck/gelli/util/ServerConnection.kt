package dev.kebugcheck.gelli.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import dev.kebugcheck.gelli.App
import dev.kebugcheck.gelli.R
import dev.kebugcheck.gelli.helper.EventListener
import dev.kebugcheck.gelli.model.User
import dev.kebugcheck.gelli.service.SaveWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.model.api.ClientCapabilitiesDto
import java.util.concurrent.CopyOnWriteArraySet

/**
 * How the app reaches the server of the current account, as a [ConnectionState] that screens can follow.
 *
 * The account's addresses are in priority order, and the first one in that order that answers as the
 * account's server is used, so with a LAN address ahead of a public one the LAN address is used at home and
 * the public one anywhere else. The choice is made again when the device moves to another network, or the
 * addresses of the current account change. When no address answers the app is [ConnectionState.Unreachable],
 * which [NetworkUtil.isOffline] treats like offline mode, and it keeps trying while the app is in the
 * foreground or playing.
 */
object ServerConnection {
    private const val TAG = "ServerConnection"

    // how long an address that doesn't answer holds up the ones after it, e.g. a LAN address on mobile data
    private const val PROBE_TIMEOUT_MS = 3_000L
    // a network that just became the default may not carry traffic right away
    private const val SETTLE_MS = 1_000L
    private const val HTTP_UNAUTHORIZED = 401

    data class Endpoint(val address: String, val serverId: String)

    /** Called on the main thread after every attempt to reach the server, also when the state stayed the same. */
    fun interface Listener {
        fun onConnectionChanged(state: ConnectionState)
    }

    private val listeners = CopyOnWriteArraySet<Listener>()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private val loop = ConnectionLoop(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        attempt = ::attempt,
        mayRetry = ::mayRetry,
        onResult = ::onResult,
    )

    /** The account whose token the server accepted in this process, so later attempts only pick an address. */
    @Volatile
    private var verified: String? = null

    @Volatile
    private var network: Network? = null

    @Volatile
    private var startedActivities = 0

    @Volatile
    private var playing = false

    /** Follows the default network and the app's activities for the life of the process. Called once from the application. */
    @JvmStatic
    fun init(context: Context) {
        val app = context.applicationContext as Application
        val manager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        network = manager.activeNetwork

        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // the callback reports the network it starts on, which needs no new choice
                if (network == this@ServerConnection.network) return
                this@ServerConnection.network = network
                networkChanged()
            }

            override fun onLost(network: Network) {
                if (network != this@ServerConnection.network) return
                this@ServerConnection.network = null
                networkChanged()
            }
        })

        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (startedActivities++ == 0) loop.wake()
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    @JvmStatic
    fun getState(): ConnectionState = loop.state

    /** Whether no address of the server answered on the last attempt. */
    @JvmStatic
    fun isUnreachable(): Boolean = loop.state == ConnectionState.Unreachable

    @JvmStatic
    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    @JvmStatic
    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /**
     * Connects the current account from scratch: checks its token with the server, and identifies the server
     * of an account that predates server ids. Called at start and after signing in or switching accounts.
     */
    @JvmStatic
    fun start(context: Context) {
        val user = App.getDatabase().userDao().getUser(PreferenceUtil.getInstance(context).user)
        if (user == null) {
            JellyfinSdkSession.clearSession()
            Toast.makeText(context, R.string.error_unexpected, Toast.LENGTH_SHORT).show()
            return
        }

        // the address that worked last time serves until an attempt picks one for this network
        JellyfinSdkSession.updateSessionFromUser(user)
        verified = null
        loop.reset()
        loop.run()
    }

    /** Stops trying to reach the server, e.g. after signing out. */
    @JvmStatic
    fun stop() {
        verified = null
        loop.reset()
    }

    /** Tries again right away while no address answers, e.g. from a retry button or after leaving offline mode. */
    @JvmStatic
    fun retry() {
        if (isUnreachable()) loop.run()
    }

    /** Picks the address again after the addresses of the current account changed. */
    @JvmStatic
    fun addressesChanged() {
        loop.run()
    }

    /** Playback keeps the retries going while the app is in the background. */
    @JvmStatic
    fun setPlaying(playing: Boolean) {
        this.playing = playing
        if (playing) loop.wake()
    }

    private fun networkChanged() {
        if (PreferenceUtil.getInstance(App.getInstance()).offlineMode) return
        loop.run(SETTLE_MS)
    }

    private fun mayRetry(): Boolean =
        !PreferenceUtil.getInstance(App.getInstance()).offlineMode && (startedActivities > 0 || playing)

    private fun onResult(previous: ConnectionState, current: ConnectionState) {
        if (previous != current) Log.i(TAG, "$previous -> $current")

        // everything that follows offline mode now follows this too
        if ((previous == ConnectionState.Unreachable) != (current == ConnectionState.Unreachable)) {
            NetworkUtil.notifyChanged()
            SaveWorker.restart(App.getInstance())
        }

        mainHandler.post { listeners.forEach { it.onConnectionChanged(current) } }
    }

    private suspend fun attempt(previous: ConnectionState): ConnectionState? {
        val context = App.getInstance()
        val user = App.getDatabase().userDao().getUser(PreferenceUtil.getInstance(context).user) ?: return null
        val endpoint = resolve(user) ?: return ConnectionState.Unreachable

        if (verified != user.id) return connect(user, endpoint)

        if (JellyfinSdkSession.switchAddress(user.jellyfinUserId, user.token, endpoint.address)) {
            Accounts.useAddress(user.id, endpoint.address)
            EventListener.restart()
        } else if (previous == ConnectionState.Unreachable) {
            // the socket may have given up while the server was away
            EventListener.restart()
        }

        return ConnectionState.Online(endpoint.address)
    }

    /** Points the session at the address and checks the account's token there. */
    private suspend fun connect(user: User, endpoint: Endpoint): ConnectionState? {
        JellyfinSdkSession.updateSession(endpoint.address, user.token, user.jellyfinUserId)
        val api = JellyfinSdkSession.createApiOrNull() ?: return null

        try {
            api.systemApi.getSystemInfo()
        } catch (err: CancellationException) {
            throw err
        } catch (err: InvalidStatusException) {
            Log.w(TAG, "connect: status ${err.status}", err)
            return if (err.status == HTTP_UNAUTHORIZED) ConnectionState.Rejected else ConnectionState.Unreachable
        } catch (err: Exception) {
            Log.w(TAG, "connect: ${err.message}", err)
            return ConnectionState.Unreachable
        }

        // an account is only identified with credentials that work
        if (user.serverId == null) {
            Accounts.identify(user, endpoint.serverId)
        } else {
            Accounts.useAddress(user.id, endpoint.address)
        }

        postCapabilities(api)
        verified = user.id
        EventListener.start()

        return ConnectionState.Online(endpoint.address)
    }

    private suspend fun postCapabilities(api: ApiClient) {
        try {
            api.sessionApi.postFullCapabilities(
                data = ClientCapabilitiesDto(
                    playableMediaTypes = listOf(),
                    supportedCommands = listOf(),
                    supportsMediaControl = true,
                    supportsPersistentIdentifier = true,
                )
            )
        } catch (err: CancellationException) {
            throw err
        } catch (err: Exception) {
            // remote control from other clients is a nicety, not a reason to stay offline
            Log.w(TAG, "postCapabilities: ${err.message}", err)
        }
    }

    /** Returns the first address of the account, in priority order, that answers, or null if none does. */
    suspend fun resolve(user: User): Endpoint? {
        val addresses = user.addresses.ifEmpty { listOfNotNull(user.server) }
        return resolve(addresses, user.serverId, PROBE_TIMEOUT_MS, ::probe)
    }

    /**
     * Probes the addresses at once and returns the first in the list to answer with the server id, or with
     * any id when it isn't known yet. An address wins once every address before it has failed, timed out
     * or answered for another server, e.g. a LAN address on a foreign network, so the result is the same as
     * trying them one by one but takes at most one timeout.
     */
    @VisibleForTesting
    internal suspend fun resolve(
        addresses: List<String>,
        serverId: String?,
        timeoutMs: Long,
        probe: suspend (String) -> String?
    ): Endpoint? = coroutineScope {
        val expected = serverId?.let(Accounts::normalizeServerId)
        val probes = addresses.distinct().map { address ->
            async {
                val id = withTimeoutOrNull(timeoutMs) {
                    try {
                        probe(address)?.let(Accounts::normalizeServerId)
                    } catch (err: CancellationException) {
                        throw err
                    } catch (err: Exception) {
                        null
                    }
                }

                if (id != null && (expected == null || id == expected)) Endpoint(address, id) else null
            }
        }

        for (pending in probes) {
            val endpoint = pending.await() ?: continue
            // the addresses after it don't matter any more
            coroutineContext.cancelChildren()
            return@coroutineScope endpoint
        }

        null
    }

    /** Returns the id of the server at the address, without signing in, or null if it doesn't answer. */
    suspend fun probe(address: String): String? {
        val api = JellyfinSdkSession.createApiForServer(address) ?: return null

        return try {
            val info by api.systemApi.getPublicSystemInfo()
            info.id
        } catch (err: CancellationException) {
            throw err
        } catch (err: Exception) {
            Log.i(TAG, "probe $address: ${err.message}")
            null
        }
    }
}
