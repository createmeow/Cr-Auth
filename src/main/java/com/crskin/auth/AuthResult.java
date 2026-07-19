package com.crskin.auth;

import com.google.gson.JsonObject;

/**
 * 认证结果
 */
public class AuthResult {
    public final boolean success;
    public final JsonObject profileJson;
    public final String sourceName;
    public final String uuid;
    public final String username;

    public AuthResult(boolean success, JsonObject profileJson,
                      String sourceName, String uuid, String username) {
        this.success = success;
        this.profileJson = profileJson;
        this.sourceName = sourceName;
        this.uuid = uuid;
        this.username = username;
    }

    public static AuthResult failure() {
        return new AuthResult(false, null, "", "", "");
    }
}
