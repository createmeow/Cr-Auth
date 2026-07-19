package com.crskin.handler;

import com.crskin.auth.AuthProvider;
import com.crskin.auth.AuthResult;
import com.crskin.config.CrSkinConfig;
import com.crskin.db.DatabaseManager;
import com.crskin.db.PlayerMapping;
import com.crskin.util.UuidUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * hasJoined 端点处理器
 */
public class HasJoinedHandler implements HttpHandler {

    private static final Logger logger = Logger.getLogger(HasJoinedHandler.class.getName());
    private static final Gson gson = new GsonBuilder().create();

    private final CrSkinConfig config;
    private final DatabaseManager db;
    private final AuthProvider authProvider;

    public HasJoinedHandler(CrSkinConfig config, DatabaseManager db, AuthProvider authProvider) {
        this.config = config;
        this.db = db;
        this.authProvider = authProvider;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            CrSkinHttpServer.sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        Map<String, String> params = parseQueryParams(exchange.getRequestURI());
        String username = params.get("username");
        String serverId = params.get("serverId");
        String ip = params.get("ip");

        if (username == null || username.isEmpty() || serverId == null || serverId.isEmpty()) {
            logger.warning("hasJoined: missing username or serverId");
            CrSkinHttpServer.sendNoContent(exchange);
            return;
        }

        logger.info("hasJoined: username=" + username + ", serverId=" + serverId + ", ip=" + (ip != null ? ip : "N/A"));

        AuthResult result = authProvider.verifyHasJoined(username, serverId, ip);

        if (!result.success) {
            logger.info("hasJoined: REJECTED " + username + " (offline player)");
            CrSkinHttpServer.sendNoContent(exchange);
            return;
        }

        String prefix = config.getNamePrefix(result.sourceName);
        String mappedName = prefix + result.username;
        if (mappedName.length() > config.getMaxUsernameLength()) {
            int maxOrig = config.getMaxUsernameLength() - prefix.length();
            mappedName = prefix + result.username.substring(0, Math.min(result.username.length(), maxOrig));
        }

        String originalUuid = result.uuid; // 无连字符格式，与 Python 版兼容

        String sourceApiUrl = "Mojang".equals(result.sourceName)
                ? CrSkinConfig.MOJANG_HASJOINED_URL
                : config.getAuthSourcesMap().getOrDefault(result.sourceName,
                    new com.crskin.config.AuthSource("", "", "")).getApiRoot();

        try {
            // 先查数据库：按 original_uuid + source 查找已有映射
            // 兼容 Python 版已创建的映射，避免 UUID 变更
            PlayerMapping existingMapping = db.getMappingByOriginalUuid(originalUuid);

            String crskinUuid;
            if (existingMapping != null && result.sourceName.equals(existingMapping.source)) {
                // 使用已有映射的 crskin UUID，保持一致性
                crskinUuid = existingMapping.crskinUuid;
                logger.info("使用已有映射: " + result.username + " -> " + existingMapping.mappedUsername
                    + " (crskin UUID: " + crskinUuid + ")");
            } else {
                // 首次登录，生成新的 crskin UUID
                crskinUuid = UuidUtil.generateCrskinUuid(originalUuid, result.sourceName);
                logger.info("生成新映射: " + result.username + " -> " + mappedName
                    + " (crskin UUID: " + crskinUuid + ")");
            }

            db.saveMapping(crskinUuid, originalUuid, result.username, mappedName, result.sourceName, sourceApiUrl);
        } catch (SQLException e) {
            logger.severe("Failed to save/update mapping: " + e.getMessage());
        }

        // 重新查询映射（确保拿到最新的 crskin_uuid，包括刚保存的）
        String finalCrskinUuid;
        try {
            PlayerMapping savedMapping = db.getMappingByOriginalUuid(originalUuid);
            if (savedMapping != null) {
                savedMapping = db.followMergeChain(savedMapping);
                finalCrskinUuid = savedMapping.crskinUuid;
            } else {
                finalCrskinUuid = UuidUtil.generateCrskinUuid(originalUuid, result.sourceName);
            }
        } catch (SQLException e) {
            finalCrskinUuid = UuidUtil.generateCrskinUuid(originalUuid, result.sourceName);
        }

        JsonObject response = new JsonObject();
        response.addProperty("id", finalCrskinUuid);
        response.addProperty("name", mappedName);

        JsonArray filteredProps = new JsonArray();
        if (result.profileJson != null && result.profileJson.has("properties")) {
            for (var elem : result.profileJson.get("properties").getAsJsonArray()) {
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
        response.add("properties", filteredProps);

        logger.info("hasJoined: ACCEPTED " + result.username + " -> " + mappedName
            + " (crskin UUID: " + finalCrskinUuid + ", via " + result.sourceName + ")");

        CrSkinHttpServer.sendJsonResponse(exchange, 200, gson.toJson(response));
    }

    private Map<String, String> parseQueryParams(URI uri) {
        Map<String, String> params = new HashMap<>();
        String query = uri.getQuery();
        if (query != null && !query.isEmpty()) {
            for (String pair : query.split("&")) {
                String[] parts = pair.split("=", 2);
                String key = java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String value = parts.length > 1 ? java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
                params.put(key, value);
            }
        }
        return params;
    }
}
