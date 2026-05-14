package com.dkanada.gramophone.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import com.dkanada.gramophone.App
import com.dkanada.gramophone.R
import com.dkanada.gramophone.activities.base.AbsBaseActivity
import com.dkanada.gramophone.databinding.ActivityLoginBinding
import com.dkanada.gramophone.mapper.SdkUserMapper
import com.dkanada.gramophone.util.JellyfinSdkSession
import com.dkanada.gramophone.util.PreferenceUtil
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.AuthenticateUserByName

class LoginActivity : AbsBaseActivity(), View.OnClickListener {
    private lateinit var binding: ActivityLoginBinding
    private var primaryColor: Int = 0

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
        primaryColor = PreferenceUtil.getInstance(this).getPrimaryColor()
        setUpToolbar()
        setUpOnClickListeners()
        binding.login.setBackgroundColor(primaryColor)
    }

    private fun setUpToolbar() {
        binding.toolbar.setBackgroundColor(primaryColor)
        setSupportActionBar(binding.toolbar)
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

        val api = JellyfinSdkSession.createApiForServer(server) ?: run {
            Toast.makeText(this, getString(R.string.error_unreachable_server), Toast.LENGTH_SHORT).show()
            return
        }

        binding.login.isEnabled = false
        @Suppress("DEPRECATION")
        binding.login.setBackgroundColor(resources.getColor(android.R.color.darker_gray))

        Thread {
            val result = runCatching {
                runBlocking {
                    val authResult by api.userApi.authenticateUserByName(
                        AuthenticateUserByName(username = username, pw = password)
                    )
                    val user = SdkUserMapper.toUserOrNull(authResult, server)
                        ?: throw IllegalStateException("missing user in auth response")
                    val publicInfo by api.systemApi.getPublicSystemInfo()
                    Pair(user, publicInfo.version)
                }
            }

            Handler(Looper.getMainLooper()).post {
                result.fold(
                    onSuccess = { (user, version) ->
                        if (version?.firstOrNull() != '1') {
                            binding.login.isEnabled = true
                            binding.login.setBackgroundColor(primaryColor)
                            Toast.makeText(this, getString(R.string.error_version), Toast.LENGTH_SHORT).show()
                        } else {
                            App.getDatabase().userDao().insertUser(user)
                            PreferenceUtil.getInstance(this).setServer(user.server)
                            PreferenceUtil.getInstance(this).setUser(user.id)
                            JellyfinSdkSession.updateSession(user.server, user.token, user.jellyfinUserId)
                            startActivity(
                                Intent(this, SplashActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                            )
                        }
                    },
                    onFailure = { e ->
                        binding.login.isEnabled = true
                        binding.login.setBackgroundColor(primaryColor)
                        val msgRes = if (e is InvalidStatusException && e.status == 401) {
                            R.string.error_login_credentials
                        } else {
                            R.string.error_unreachable_server
                        }
                        Toast.makeText(this, getString(msgRes), Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }.start()
    }
}
