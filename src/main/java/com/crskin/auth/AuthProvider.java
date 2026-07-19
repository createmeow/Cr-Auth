package com.crskin.auth;

import com.crskin.config.AuthSource;
import com.crskin.config.CrSkinConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * 认证源代理 - 向 Mojang/第三方 Yggdrasil 服务端转发验证请求
 */
public class AuthProvider {

    private static final Logger logger = Logger.getLogger(AuthProvider.class.getName());
    private static final Gson gson = new Gson();

    private final CrSkinConfig config;

    public AuthProvider(CrSkinConfig config) {
        this.config = config;
    }

    public AuthResult verifyHasJoined(String username, String serverId, String ip) {
        AuthResult result = tryMojangHasJoined(username, serverId, ip);
        if (result.success) {
            logger.info("hasJoined: " + username + " verified via Mojang");
            return result;
        }

        for (AuthSource source : config.getAuthSources()) {
            logger.fine("Trying " + source.getName() + " hasJoined for " + username);
            result = tryCustomHasJoined(source, username, serverId, ip);
            if (result.success) {
                logger.info("hasJoined: " + username + " verified via " + source.getName());
                return result;
            }
        }

        logger.info("hasJoined: " + username + " failed all sources (offline player)");
        return AuthResult.failure();
    }

    private AuthResult tryMojangHasJoined(String username, String serverId, String ip) {
        try {
            String url = CrSkinConfig.MOJANG_HASJOINED_URL + "?" +
                    "username=" + encode(username) +
                    "&serverId=" + encode(serverId) +
                    (ip != null ? "&ip=" + encode(ip) : "");

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.connect();

            int statusCode = conn.getResponseCode();
            if (statusCode == 204 || statusCode != 200) { conn.disconnect(); return AuthResult.failure(); }

            String body = readResponse(conn);
            conn.disconnect();

            JsonObject data = gson.fromJson(body, JsonObject.class);
            if (data == null || !data.has("id")) return AuthResult.failure();

            return new AuthResult(true, data, "Mojang",
                    data.get("id").getAsString().replace("-", ""),
                    data.has("name") ? data.get("name").getAsString() : username);
        } catch (Exception e) {
            logger.warning("Mojang hasJoined request failed: " + e.getMessage());
            return AuthResult.failure();
        }
    }

    private AuthResult tryCustomHasJoined(AuthSource source, String username, String serverId, String ip) {
        try {
            String hasJoinedUrl = source.getApiRoot() + "sessionserver/session/minecraft/hasJoined";
            String url = hasJoinedUrl + "?" +
                    "username=" + encode(username) +
                    "&serverId=" + encode(serverId) +
                    (ip != null ? "&ip=" + encode(ip) : "");

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.connect();

            int statusCode = conn.getResponseCode();
            if (statusCode == 204 || statusCode != 200) { conn.disconnect(); return AuthResult.failure(); }

            String body = readResponse(conn);
            conn.disconnect();

            JsonObject data = gson.fromJson(body, JsonObject.class);
            if (data == null || !data.has("id")) return AuthResult.failure();

            return new AuthResult(true, data, source.getName(),
                    data.get("id").getAsString().replace("-", ""),
                    data.has("name") ? data.get("name").getAsString() : username);
        } catch (Exception e) {
            logger.warning("[" + source.getName() + "] hasJoined request failed: " + e.getMessage());
            return AuthResult.failure();
        }
    }

    public JsonObject queryProfileFromSource(AuthSource source, String uuid, boolean unsigned) {
        try {
            String url = source.getApiRoot() + "sessionserver/session/minecraft/profile/" + uuid +
                    (unsigned ? "" : "?unsigned=false");

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.connect();

            int statusCode = conn.getResponseCode();
            if (statusCode != 200) { conn.disconnect(); return null; }

            String body = readResponse(conn);
            conn.disconnect();
            return gson.fromJson(body, JsonObject.class);
        } catch (Exception e) {
            logger.warning("[" + source.getName() + "] profile query " + uuid + " failed: " + e.getMessage());
            return null;
        }
    }

    public JsonObject queryMojangProfile(String uuid, boolean unsigned) {
        try {
            String url = CrSkinConfig.MOJANG_PROFILE_URL + "/" + uuid +
                    (unsigned ? "" : "?unsigned=false");

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.connect();

            int statusCode = conn.getResponseCode();
            if (statusCode != 200) { conn.disconnect(); return null; }

            String body = readResponse(conn);
            conn.disconnect();
            return gson.fromJson(body, JsonObject.class);
        } catch (Exception e) {
            logger.warning("Mojang profile query " + uuid + " failed: " + e.getMessage());
            return null;
        }
    }

    public JsonObject getProfileWithMapping(String uuid, String source, String sourceApiUrl,
                                            boolean unsigned, String namePrefix, int maxLen) {
        JsonObject profile;
        if ("Mojang".equals(source)) {
            profile = queryMojangProfile(uuid, unsigned);
        } else {
            AuthSource authSource = findAuthSourceByApiUrl(source, sourceApiUrl);
            profile = authSource != null ? queryProfileFromSource(authSource, uuid, unsigned) : null;
        }

        if (profile == null) return null;

        String currentName = profile.has("name") ? profile.get("name").getAsString() : "";
        profile.addProperty("name", applyNamePrefix(currentName, namePrefix, maxLen));
        return profile;
    }

    private AuthSource findAuthSourceByApiUrl(String sourceName, String sourceApiUrl) {
        AuthSource source = config.getAuthSource(sourceName);
        if (source != null) return source;
        for (AuthSource s : config.getAuthSources()) {
            if (s.getApiRoot().equals(sourceApiUrl) || s.getServerLink().equals(sourceApiUrl)) return s;
        }
        return null;
    }

    private String applyNamePrefix(String name, String prefix, int maxLen) {
        String mapped = prefix + name;
        if (mapped.length() > maxLen) {
            return prefix + name.substring(0, Math.min(name.length(), maxLen - prefix.length()));
        }
        return mapped;
    }

    public static JsonObject filterProperties(JsonObject profile) {
        JsonObject result = new JsonObject();
        if (profile.has("id")) result.add("id", profile.get("id"));
        if (profile.has("name")) result.add("name", profile.get("name"));

        com.google.gson.JsonArray filteredProps = new com.google.gson.JsonArray();
        if (profile.has("properties") && profile.get("properties").isJsonArray()) {
            for (var elem : profile.get("properties").getAsJsonArray()) {
                if (elem.isJsonObject()) {
                    JsonObject prop = elem.getAsJsonObject();
                    String propName = prop.has("name") ? prop.get("name").getAsString() : "";
                    if ("textures".equals(propName)) {
                        JsonObject filtered = new JsonObject();
                        filtered.addProperty("name", "textures");
                        if (prop.has("value")) filtered.add("value", prop.get("value"));
                        if (prop.has("signature")) filtered.add("signature", prop.get("signature"));
                        filteredProps.add(filtered);
                    }
                }
            }
        }
        result.add("properties", filteredProps);
        return result;
    }

    private static String readResponse(HttpURLConnection conn) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
