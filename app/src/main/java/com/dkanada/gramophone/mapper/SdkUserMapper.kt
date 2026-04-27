package com.dkanada.gramophone.mapper

import com.dkanada.gramophone.model.User
import org.jellyfin.sdk.model.api.AuthenticationResult

object SdkUserMapper {
    @JvmStatic
    fun toUserOrNull(result: AuthenticationResult, server: String): User? {
        val user = result.user ?: return null
        val token = result.accessToken ?: return null
        return User(
            server,
            token,
            SdkMapperUtil.uuidToId(user.id),
            user.name ?: ""
        )
    }
}
