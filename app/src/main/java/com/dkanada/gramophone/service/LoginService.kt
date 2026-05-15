package com.dkanada.gramophone.service

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.dkanada.gramophone.App
import com.dkanada.gramophone.BuildConfig
import com.dkanada.gramophone.R
import com.dkanada.gramophone.util.JellyfinSdkSession
import com.dkanada.gramophone.util.PreferenceUtil
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.systemApi

class LoginService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sendStateBroadcast(STATE_POLLING)
        authenticate()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun sendStateBroadcast(action: String) {
        val intent = Intent(action)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun authenticate() {
        val user = App.getDatabase().userDao().getUser(PreferenceUtil.getInstance(this).user)
        if (user == null) {
            JellyfinSdkSession.clearSession()
            Toast.makeText(this, getString(R.string.error_unexpected), Toast.LENGTH_SHORT).show()
            return
        }

        JellyfinSdkSession.updateSession(user.server, user.token, user.jellyfinUserId)

        val api = JellyfinSdkSession.createApiOrNull()
        if (api == null) {
            sendStateBroadcast(STATE_OFFLINE)
            return
        }

        // Phase 6 shim: the legacy ApiClient is still the WebSocket transport for
        // EventListener. Keep its credentials in sync until EventListener is migrated.
        legacyWebSocketBootstrap(user.server, user.token, user.jellyfinUserId)

        Thread {
            val success = runCatching {
                runBlocking { bootstrapAuthenticatedSession(api) }
            }.onFailure { Log.w(TAG, "session restore failed", it) }.isSuccess

            Handler(Looper.getMainLooper()).post {
                sendStateBroadcast(if (success) STATE_ONLINE else STATE_OFFLINE)
            }
        }.start()
    }

    private fun legacyWebSocketBootstrap(server: String, token: String, userId: String) {
        val legacy = App.getApiClient()
        legacy.ChangeServerLocation(server)
        legacy.SetAuthenticationInfo(token, userId)
        legacy.ensureWebSocket()
    }

    companion object {
        private const val TAG = "LoginService"

        const val STATE_POLLING: String = BuildConfig.APPLICATION_ID + ".unknown"
        const val STATE_ONLINE: String = BuildConfig.APPLICATION_ID + ".online"
        const val STATE_OFFLINE: String = BuildConfig.APPLICATION_ID + ".offline"

        internal val DEFAULT_CAPABILITIES = ClientCapabilityArgs(
            supportsMediaControl = true,
            supportsPersistentIdentifier = true
        )

        internal data class ClientCapabilityArgs(
            val supportsMediaControl: Boolean,
            val supportsPersistentIdentifier: Boolean
        )

        internal suspend fun bootstrapAuthenticatedSession(
            api: ApiClient,
            capabilities: ClientCapabilityArgs = DEFAULT_CAPABILITIES
        ) {
            api.systemApi.getSystemInfo()
            api.sessionApi.postCapabilities(
                supportsMediaControl = capabilities.supportsMediaControl,
                supportsPersistentIdentifier = capabilities.supportsPersistentIdentifier
            )
        }
    }
}
