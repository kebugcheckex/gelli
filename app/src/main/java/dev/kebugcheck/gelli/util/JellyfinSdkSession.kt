package dev.kebugcheck.gelli.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.annotation.VisibleForTesting
import dev.kebugcheck.gelli.App
import dev.kebugcheck.gelli.BuildConfig
import dev.kebugcheck.gelli.R
import dev.kebugcheck.gelli.model.User
import okhttp3.OkHttpClient
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
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

    // every address the session pointed at with these credentials, as urls built through one may still be queued
    private val usedAddresses = LinkedHashSet<String>()

    @JvmStatic
    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    @JvmStatic
    fun updateSession(baseUrl: String?, accessToken: String?, userId: String?) {
        synchronized(lock) {
            val token = accessToken?.trim()?.takeIf { it.isNotEmpty() }
            val user = userId?.trim()?.takeIf { it.isNotEmpty() }
            if (token != this.accessToken || user != this.userId) usedAddresses.clear()

            this.baseUrl = normalizeAddress(baseUrl)
            this.accessToken = token
            this.userId = user
            this.baseUrl?.let(usedAddresses::add)
        }
    }

    /**
     * Points the session at another address of the same server, unless the session has moved on to other
     * credentials meanwhile. Returns whether the address changed.
     */
    @JvmStatic
    fun switchAddress(userId: String?, accessToken: String?, address: String): Boolean {
        val normalized = normalizeAddress(address) ?: return false

        synchronized(lock) {
            if (this.userId != userId?.trim() || this.accessToken != accessToken?.trim()) return false
            if (baseUrl == normalized) return false

            baseUrl = normalized
            usedAddresses.add(normalized)
            return true
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
    fun clearSession() {
        synchronized(lock) {
            baseUrl = null
            accessToken = null
            userId = null
            usedAddresses.clear()
        }
    }

    /**
     * Points a url built through an earlier address of the session at the current one, e.g. a song queued
     * before the device moved to another network. Any other url comes back as it is.
     */
    @JvmStatic
    fun toCurrentAddress(url: String): String {
        val (current, used) = synchronized(lock) { (baseUrl ?: return url) to usedAddresses.toList() }
        return rewriteAddress(url, used, current)
    }

    @JvmStatic
    fun getCurrentUserId(): String? = userId

    @JvmStatic
    fun getBaseUrl(): String? = baseUrl

    @JvmStatic
    fun getAccessToken(): String? = accessToken

    @JvmStatic
    fun getDeviceId(): String {
        val context = appContext ?: return "gelli-device"
        return resolveDeviceId(context)
    }

    fun createApiOrNull() = snapshotOrNull()?.let { snapshot ->
        val context = appContext ?: App.getInstance()?.applicationContext ?: return@let null
        buildJellyfin(context).createApi(
            baseUrl = snapshot.baseUrl,
            accessToken = snapshot.accessToken
        )
    }

    @JvmStatic
    fun createApiForServer(serverUrl: String) = normalizeAddress(serverUrl)?.let { normalized ->
        val context = appContext ?: App.getInstance()?.applicationContext ?: return@let null
        buildJellyfin(context).createApi(baseUrl = normalized)
    }

    internal fun createApiForUser(user: User) = normalizeAddress(user.server)?.let { server ->
        val token = user.token?.takeIf { it.isNotBlank() } ?: return@let null
        val context = appContext ?: App.getInstance()?.applicationContext ?: return@let null
        buildJellyfin(context).createApi(baseUrl = server, accessToken = token)
    }

    // shared by every api client and socket, so one interceptor sees all their connection failures
    private val httpFactory by lazy {
        OkHttpFactory(OkHttpClient.Builder().addInterceptor(ServerConnection.failureInterceptor).build())
    }

    private fun buildJellyfin(context: Context) = createJellyfin {
        apiClientFactory = httpFactory
        socketConnectionFactory = httpFactory
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

    @JvmStatic
    fun normalizeAddress(raw: String?): String? {
        return raw
            ?.trim()
            ?.trimEnd('/')
            ?.takeIf { it.isNotEmpty() }
    }

    /** Whether the url goes to the address the session uses now. */
    @JvmStatic
    fun isOnCurrentAddress(url: String): Boolean = baseUrl?.let { startsWithAddress(url, it) } ?: false

    /** Replaces the address the url starts with by [current], if it is one of [addresses]. */
    @VisibleForTesting
    internal fun rewriteAddress(url: String, addresses: Collection<String>, current: String): String {
        // the longest match wins, so https://host/jellyfin isn't taken for https://host
        val prefix = (addresses + current).filter { startsWithAddress(url, it) }.maxByOrNull { it.length }
        if (prefix == null || prefix == current) return url

        return current + url.substring(prefix.length)
    }

    // the address has to end where the url's path, query or fragment begins, so http://host doesn't match http://host.example
    private fun startsWithAddress(url: String, address: String): Boolean =
        url.startsWith(address, ignoreCase = true) &&
            (url.length == address.length || url[address.length] in "/?#")

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
