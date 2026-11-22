package com.dkanada.gramophone.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.dkanada.gramophone.App
import com.dkanada.gramophone.BuildConfig
import com.dkanada.gramophone.R
import com.dkanada.gramophone.helper.EventListener
import com.dkanada.gramophone.model.User
import com.dkanada.gramophone.util.Accounts
import com.dkanada.gramophone.util.JellyfinSdkSession
import com.dkanada.gramophone.util.PreferenceUtil
import com.dkanada.gramophone.util.ServerConnection
import kotlinx.coroutines.runBlocking
import java.io.IOException
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.model.api.ClientCapabilitiesDto

class LoginService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sendStateBroadcast(STATE_POLLING)
        authenticate()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun sendStateBroadcast(action: String) {
        sendBroadcast(Intent(action).apply { setPackage(packageName) })
    }

    private fun authenticate() {
        val user = App.getDatabase().userDao().getUser(PreferenceUtil.getInstance(this).getUser())
        if (user == null) {
            JellyfinSdkSession.clearSession()
            Toast.makeText(this, getString(R.string.error_unexpected), Toast.LENGTH_SHORT).show()
            return
        }

        // the address that worked last time serves until the probe below picks one for this network
        JellyfinSdkSession.updateSessionFromUser(user)

        Thread {
            try {
                runBlocking { connect(user) }
                EventListener.start()
                sendStateBroadcast(STATE_ONLINE)
            } catch (err: ApiClientException) {
                Log.w(TAG, "authenticate: API error: ${err.message}", err)
                sendStateBroadcast(STATE_OFFLINE)
            } catch (err: Exception) {
                Log.w(TAG, "authenticate: ${err.message}", err)
                sendStateBroadcast(STATE_OFFLINE)
            }
        }.start()
    }

    private suspend fun connect(user: User) {
        val endpoint = ServerConnection.resolve(user) ?: throw IOException("no address of the server answered")
        JellyfinSdkSession.updateSession(endpoint.address, user.token, user.jellyfinUserId)

        val api = JellyfinSdkSession.createApiOrNull()
            ?: throw IllegalStateException("no SDK session after updateSession")
        // checks the token, so an account is only identified with credentials that work
        api.systemApi.getSystemInfo()

        if (user.serverId == null) {
            Accounts.identify(user, endpoint.serverId)
        } else {
            Accounts.useAddress(user.id, endpoint.address)
        }

        api.sessionApi.postFullCapabilities(
            data = ClientCapabilitiesDto(
                playableMediaTypes = listOf(),
                supportedCommands = listOf(),
                supportsMediaControl = DEFAULT_CAPABILITIES.supportsMediaControl,
                supportsPersistentIdentifier = DEFAULT_CAPABILITIES.supportsPersistentIdentifier,
            )
        )
    }

    companion object {
        private const val TAG = "LoginService"

        const val PACKAGE_NAME = BuildConfig.APPLICATION_ID
        const val STATE_POLLING = "$PACKAGE_NAME.unknown"
        const val STATE_ONLINE = "$PACKAGE_NAME.online"
        const val STATE_OFFLINE = "$PACKAGE_NAME.offline"

        data class ClientCapabilityArgs(
            val supportsMediaControl: Boolean,
            val supportsPersistentIdentifier: Boolean,
        )

        val DEFAULT_CAPABILITIES = ClientCapabilityArgs(
            supportsMediaControl = true,
            supportsPersistentIdentifier = true,
        )
    }
}
