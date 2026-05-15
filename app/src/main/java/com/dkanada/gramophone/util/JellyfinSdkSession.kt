package com.dkanada.gramophone.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.annotation.VisibleForTesting
import com.dkanada.gramophone.App
import com.dkanada.gramophone.BuildConfig
import com.dkanada.gramophone.R
import com.dkanada.gramophone.model.User
import org.jellyfin.apiclient.interaction.ApiClient
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo

object JellyfinSdkSession {
    private data class SessionSnapshot(
        val baseUrl: String,
        val accessToken: String,
        val userId: String
    )

    private val lock = Any()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var baseUrl: String? = null

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var userId: String? = null

    @JvmStatic
    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    @JvmStatic
    fun updateSession(baseUrl: String?, accessToken: String?, userId: String?) {
        synchronized(lock) {
            this.baseUrl = normalizeBaseUrl(baseUrl)
            this.accessToken = accessToken?.trim()?.takeIf { it.isNotEmpty() }
            this.userId = userId?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    @JvmStatic
    fun updateSessionFromUser(user: User?) {
        if (user == null) {
            clearSession()
            return
        }
        updateSession(user.server, user.token, user.jellyfinUserId)
    }

    @JvmStatic
    fun updateSessionFromApiClient(apiClient: ApiClient?) {
        if (apiClient == null) {
            clearSession()
            return
        }
        updateSession(
            apiClient.getApiUrl(),
            apiClient.getAccessToken(),
            apiClient.getCurrentUserId()
        )
    }

    @JvmStatic
    fun clearSession() {
        synchronized(lock) {
            baseUrl = null
            accessToken = null
            userId = null
        }
    }

    @JvmStatic
    fun getCurrentUserId(): String? = userId

    @JvmStatic
    fun getBaseUrl(): String? = baseUrl

    fun createApiOrNull() = snapshotOrNull()?.let { snapshot ->
        val context = appContext ?: App.getInstance()?.applicationContext ?: return@let null
        buildJellyfin(context).createApi(
            baseUrl = snapshot.baseUrl,
            accessToken = snapshot.accessToken
        )
    }

    @JvmStatic
    fun createApiForServer(serverUrl: String) = normalizeBaseUrl(serverUrl)?.let { normalized ->
        val context = appContext ?: App.getInstance()?.applicationContext ?: return@let null
        buildJellyfin(context).createApi(baseUrl = normalized)
    }

    private fun buildJellyfin(context: Context) = createJellyfin {
        clientInfo = ClientInfo(
            name = context.getString(R.string.app_name),
            version = BuildConfig.VERSION_NAME
        )
        deviceInfo = DeviceInfo(
            id = resolveDeviceId(context),
            name = Build.MODEL
        )
        this.context = context
    }

    private fun snapshotOrNull(): SessionSnapshot? {
        val baseUrlSnapshot = baseUrl
        val accessTokenSnapshot = accessToken
        val userIdSnapshot = userId

        if (baseUrlSnapshot.isNullOrBlank() || accessTokenSnapshot.isNullOrBlank() || userIdSnapshot.isNullOrBlank()) {
            return null
        }

        return SessionSnapshot(baseUrlSnapshot, accessTokenSnapshot, userIdSnapshot)
    }

    private fun normalizeBaseUrl(raw: String?): String? {
        return raw
            ?.trim()
            ?.trimEnd('/')
            ?.takeIf { it.isNotEmpty() }
    }

    @SuppressLint("HardwareIds")
    private fun resolveDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?: "gelli-device"
    }

    @VisibleForTesting
    internal fun resetForTest() {
        appContext = null
        clearSession()
    }
}
