package com.dkanada.gramophone.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.VisibleForTesting
import com.dkanada.gramophone.model.NetworkPolicy

/** Answers whether the network settings allow the app to use the current network. */
object NetworkUtil {
    /** Streaming needs a network that the streaming setting allows, so with no network at all only stored songs play. */
    @JvmStatic
    fun canStream(context: Context): Boolean = isAllowed(context, PreferenceUtil.getInstance(context).networkStreaming)

    @JvmStatic
    fun isAllowed(context: Context, policy: NetworkPolicy): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)

        val connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
        val unmetered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ?: false

        return isAllowed(policy, connected, unmetered)
    }

    @JvmStatic
    @VisibleForTesting
    fun isAllowed(policy: NetworkPolicy, connected: Boolean, unmetered: Boolean): Boolean = when (policy) {
        NetworkPolicy.ANY -> connected
        NetworkPolicy.UNMETERED -> connected && unmetered
    }
}
