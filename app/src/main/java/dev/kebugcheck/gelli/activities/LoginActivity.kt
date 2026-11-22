package dev.kebugcheck.gelli.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import dev.kebugcheck.gelli.R
import dev.kebugcheck.gelli.activities.base.AbsBaseActivity
import dev.kebugcheck.gelli.databinding.ActivityLoginBinding
import dev.kebugcheck.gelli.mapper.SdkMapperUtil
import dev.kebugcheck.gelli.util.Accounts
import dev.kebugcheck.gelli.util.JellyfinSdkSession
import dev.kebugcheck.gelli.util.PreferenceUtil
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.AuthenticateUserByName

class LoginActivity : AbsBaseActivity(), View.OnClickListener {
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpViews()
    }

    override fun onPause() {
        super.onPause()
        overridePendingTransition(0, R.anim.fade_quick)
    }

    private fun setUpViews() {
        setSupportActionBar(binding.toolbar)
        setUpOnClickListeners()
    }

    private fun setUpOnClickListeners() {
        binding.login.setOnClickListener(this)
        binding.select.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        if (v == binding.select) {
            startActivity(Intent(this, SelectActivity::class.java))
            return
        }

        val username = binding.username.text.toString()
        val password = binding.password.text.toString()
        val server = binding.server.text.toString()

        if (TextUtils.isEmpty(username)) {
            Toast.makeText(this, getString(R.string.error_empty_username), Toast.LENGTH_SHORT).show()
            return
        }

        if (TextUtils.isEmpty(server)) {
            Toast.makeText(this, getString(R.string.error_empty_server), Toast.LENGTH_SHORT).show()
            return
        }

        val address = JellyfinSdkSession.normalizeAddress(server)
        val api = address?.let { JellyfinSdkSession.createApiForServer(it) } ?: run {
            Toast.makeText(this, getString(R.string.error_unreachable_server), Toast.LENGTH_SHORT).show()
            return
        }

        binding.login.isEnabled = false

        Thread {
            val result = runCatching {
                runBlocking {
                    val publicInfo by api.systemApi.getPublicSystemInfo()
                    if (publicInfo.version?.firstOrNull() != '1') throw UnsupportedVersionException()
                    val serverId = publicInfo.id ?: throw IllegalStateException("missing server id")

                    val authResult by api.userApi.authenticateUserByName(
                        AuthenticateUserByName(username = username, pw = password)
                    )
                    val user = authResult.user ?: throw IllegalStateException("missing user in auth response")
                    val token = authResult.accessToken ?: throw IllegalStateException("missing token in auth response")

                    // signing in through another address of a saved server adds the address to its account
                    Accounts.signIn(serverId, SdkMapperUtil.uuidToId(user.id), user.name ?: "", token, address)
                }
            }

            Handler(Looper.getMainLooper()).post {
                result.fold(
                    onSuccess = { user ->
                        PreferenceUtil.getInstance(this).setUser(user.id)
                        JellyfinSdkSession.updateSessionFromUser(user)
                        startActivity(
                            Intent(this, SplashActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                        )
                    },
                    onFailure = { e ->
                        binding.login.isEnabled = true
                        val msgRes = when {
                            e is UnsupportedVersionException -> R.string.error_version
                            e is InvalidStatusException && e.status == 401 -> R.string.error_login_credentials
                            else -> R.string.error_unreachable_server
                        }
                        Toast.makeText(this, getString(msgRes), Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }.start()
    }

    private class UnsupportedVersionException : Exception("unsupported server version")
}
