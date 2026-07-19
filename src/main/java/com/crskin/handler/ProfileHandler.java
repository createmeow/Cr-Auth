package com.crskin.handler;

import com.crskin.auth.AuthProvider;
import com.crskin.config.AuthSource;
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
 * Profile 端点处理器
 */
public class ProfileHandler implements HttpHandler {

    private static final Logger logger = Logger.getLogger(ProfileHandler.class.getName());
    private static final Gson gson = new GsonBuilder().create();

    private final CrSkinConfig config;
    private final DatabaseManager db;
    private final AuthProvider authProvider;

    public ProfileHandler(CrSkinConfig config, DatabaseManager db, AuthProvider authProvider) {
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

        String path = exchange.getRequestURI().getPath();
        String uuidStr = path.substring(path.lastIndexOf("/") + 1);

        Map<String, String> params = parseQueryParams(exchange.getRequestURI());
        boolean unsigned = !"false".equals(params.get("unsigned"));

        String uuidClean = uuidStr.replace("-", "");
        String uuidHyphenated = UuidUtil.toHyphenatedUuid(uuidStr);

        try {
            PlayerMapping mapping = db.getMappingByCrskinUuid(uuidHyphenated);
            if (mapping != null) {
                mapping = db.followMergeChain(mapping);
                JsonObject profile = getProfileFromSource(mapping, unsigned);
                if (profile != null) {
                    profile.addProperty("id", mapping.crskinUuid);
                    logger.fine("Profile found for " + mapping.mappedUsername + " (via " + mapping.source + ", crskin UUID: " + mapping.crskinUuid + ")");
                    CrSkinHttpServer.sendJsonResponse(exchange, 200, gson.toJson(profile));
                    return;
                }

                logger.warning("Profile source unavailable for " + uuidHyphenated);
                JsonObject cached = new JsonObject();
                cached.addProperty("id", mapping.crskinUuid);
                cached.addProperty("name", mapping.mappedUsername);
                cached.add("properties", new JsonArray());
                CrSkinHttpServer.sendJsonResponse(exchange, 200, gson.toJson(cached));
                return;
            }

            mapping = db.getMappingByOriginalUuid(uuidClean);
            if (mapping != null) {
                JsonObject profile = getProfileFromSource(mapping, unsigned);
                if (profile != null) {
                    profile.addProperty("id", mapping.crskinUuid);
                    CrSkinHttpServer.sendJsonResponse(exchange, 200, gson.toJson(profile));
                    return;
                }

                JsonObject cached = new JsonObject();
                cached.addProperty("id", mapping.crskinUuid);
                cached.addProperty("name", mapping.mappedUsername);
                cached.add("properties", new JsonArray());
                CrSkinHttpServer.sendJsonResponse(exchange, 200, gson.toJson(cached));
                return;
            }

            JsonObject profile = authProvider.queryMojangProfile(uuidClean, unsigned);
            if (profile != null) {
                logger.fine("Profile found for UUID " + uuidClean + " via Mojang (no mapping)");
                CrSkinHttpServer.sendJsonResponse(exchange, 200, gson.toJson(profile));
                return;
            }

            for (AuthSource source : config.getAuthSources()) {
                profile = authProvider.queryProfileFromSource(source, uuidClean, unsigned);
                if (profile != null) {
                    logger.fine("Profile found for UUID " + uuidClean + " via " + source.getName() + " (no mapping)");
                    CrSkinHttpServer.sendJsonResponse(exchange, 200, gson.toJson(profile));
                    return;
                }
            }

            logger.info("Profile not found for UUID " + uuidClean);
            CrSkinHttpServer.sendNoContent(exchange);

        } catch (SQLException e) {
            logger.severe("Database error in handleProfile: " + e.getMessage());
            CrSkinHttpServer.sendResponse(exchange, 500, "{\"error\":\"Database error\"}");
        }
    }

    private JsonObject getProfileFromSource(PlayerMapping mapping, boolean unsigned) throws SQLException {
        String prefix = config.getNamePrefix(mapping.source);
        JsonObject profile = authProvider.getProfileWithMapping(
                mapping.originalUuid, mapping.source, mapping.sourceApiUrl,
                unsigned, prefix, config.getMaxUsernameLength());

        if (profile == null) return null;

        return AuthProvider.filterProperties(profile);
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
