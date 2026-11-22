package dev.kebugcheck.gelli.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import androidx.annotation.VisibleForTesting
import dev.kebugcheck.gelli.App
import dev.kebugcheck.gelli.helper.EventListener
import dev.kebugcheck.gelli.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.extensions.systemApi

/**
 * Picks which address of an account to talk to. The account's addresses are in priority order, and the
 * first one in that order that answers as the account's server is used, so with a LAN address ahead of a
 * public one the LAN address is used at home and the public one anywhere else. The choice is made again
 * whenever the device moves to another network, or the addresses of the current account change.
 */
object ServerConnection {
    private const val TAG = "ServerConnection"

    // how long an address that doesn't answer holds up the ones after it, e.g. a LAN address on mobile data
    private const val PROBE_TIMEOUT_MS = 3_000L
    // a network that just became the default may not carry traffic right away
    private const val SETTLE_MS = 1_000L

    data class Endpoint(val address: String, val serverId: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var reconnecting: Job? = null

    @Volatile
    private var network: Network? = null

    /** Follows the default network for the life of the process. Called once from the application. */
    @JvmStatic
    fun init(context: Context) {
        val manager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        network = manager.activeNetwork

        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // the callback reports the network it starts on, which needs no new choice
                if (network == this@ServerConnection.network) return
                this@ServerConnection.network = network
                reconnect()
            }
        })
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

    /** Picks the address again after the addresses of the current account changed. */
    @JvmStatic
    fun addressesChanged() {
        reconnect()
    }

    /** Moves the session to the first address, in priority order, that answers on the current network. */
    private fun reconnect() = synchronized(this) {
        reconnecting?.cancel()
        reconnecting = scope.launch {
            delay(SETTLE_MS)

            val context = App.getInstance()
            if (NetworkUtil.isOffline(context)) return@launch

            val user = App.getDatabase().userDao().getUser(PreferenceUtil.getInstance(context).user) ?: return@launch
            // the login service identifies an account without a server id
            if (user.serverId == null) return@launch

            val endpoint = resolve(user) ?: return@launch
            if (JellyfinSdkSession.switchAddress(user.jellyfinUserId, user.token, endpoint.address)) {
                Accounts.useAddress(user.id, endpoint.address)
                EventListener.restart()
                Log.i(TAG, "reconnect: now using ${endpoint.address}")
            }
        }
    }
}
