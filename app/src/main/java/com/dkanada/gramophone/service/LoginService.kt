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
import com.dkanada.gramophone.util.JellyfinSdkSession
import com.dkanada.gramophone.util.PreferenceUtil
import kotlinx.coroutines.runBlocking
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

        JellyfinSdkSession.updateSession(user.server, user.token, user.jellyfinUserId)

        Thread {
            try {
                runBlocking {
                    val api = JellyfinSdkSession.createApiOrNull()
                        ?: throw IllegalStateException("no SDK session after updateSession")
                    api.systemApi.getSystemInfo()
                    api.sessionApi.postFullCapabilities(
                        data = ClientCapabilitiesDto(
                            playableMediaTypes = listOf(),
                            supportedCommands = listOf(),
                            supportsMediaControl = true,
                            supportsPersistentIdentifier = true,
                        )
                    )
                }
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

    companion object {
        private const val TAG = "LoginService"

        const val PACKAGE_NAME = BuildConfig.APPLICATION_ID
        const val STATE_POLLING = "$PACKAGE_NAME.unknown"
        const val STATE_ONLINE = "$PACKAGE_NAME.online"
        const val STATE_OFFLINE = "$PACKAGE_NAME.offline"
    }
}
