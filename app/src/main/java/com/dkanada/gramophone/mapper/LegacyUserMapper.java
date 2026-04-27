package com.dkanada.gramophone.mapper;

import com.dkanada.gramophone.model.User;

import org.jellyfin.apiclient.model.users.AuthenticationResult;

public final class LegacyUserMapper {
    private LegacyUserMapper() {
    }

    public static User toUser(AuthenticationResult result, String server) {
        return new User(
                server,
                result.getAccessToken(),
                result.getUser().getId(),
                result.getUser().getName()
        );
    }
}
