package com.dkanada.gramophone.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import com.dkanada.gramophone.model.NetworkPolicy
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Answers whether the network settings allow the app to use the current network, and reports when that may change.
 * Offline mode allows no network at all, whatever is connected.
 */
object NetworkUtil {
    /** Streaming needs a network that the streaming setting allows, so with no network at all only stored songs play. */
    @JvmStatic
    fun canStream(context: Context): Boolean = isAllowed(context, PreferenceUtil.getInstance(context).networkStreaming)

    @JvmStatic
    fun isAllowed(context: Context, policy: NetworkPolicy): Boolean {
        if (isOffline(context)) return false

        val (connected, unmetered) = getState(context)
        return isAllowed(policy, connected, unmetered)
    }

    /** Offline mode also stops saving, playback reporting and favorite changes, which don't go through [isAllowed]. */
    @JvmStatic
    fun isOffline(context: Context): Boolean = PreferenceUtil.getInstance(context).offlineMode

    @JvmStatic
    @VisibleForTesting
    fun isAllowed(policy: NetworkPolicy, connected: Boolean, unmetered: Boolean): Boolean = when (policy) {
        NetworkPolicy.ANY -> connected
        NetworkPolicy.UNMETERED -> connected && unmetered
    }

    private fun getState(context: Context): Pair<Boolean, Boolean> {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)

        val connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
        val unmetered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ?: false

        return Pair(connected, unmetered)
    }

    /** Called on the main thread when the network or a network setting changes, so playability may have changed. */
    fun interface Listener {
        fun onNetworkChanged()
    }

    private val listeners = CopyOnWriteArraySet<Listener>()

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private var lastState: Pair<Boolean, Boolean>? = null

    @JvmStatic
    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    @JvmStatic
    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /** Follows the active network for the life of the process. Called once from the application. */
    @JvmStatic
    fun init(context: Context) {
        val appContext = context.applicationContext
        lastState = getState(appContext)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh(appContext)
            override fun onLost(network: Network) = refresh(appContext)
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh(appContext)
        }

        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            manager.registerDefaultNetworkCallback(callback)
        } else {
            val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            manager.registerNetworkCallback(request, callback)
        }
    }

    // capabilities change often (e.g. bandwidth estimates), so listeners only hear about what the policies look at
    private fun refresh(context: Context) {
        mainHandler.post {
            val state = getState(context)
            if (state != lastState) {
                lastState = state
                listeners.forEach { it.onNetworkChanged() }
            }
        }
    }

    /** Notifies listeners on the main thread, e.g. after a network setting changed. */
    @JvmStatic
    fun notifyChanged() {
        mainHandler.post { listeners.forEach { it.onNetworkChanged() } }
    }
}
