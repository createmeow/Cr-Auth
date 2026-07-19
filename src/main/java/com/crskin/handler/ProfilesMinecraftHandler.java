package com.crskin.handler;

import com.crskin.auth.AuthProvider;
import com.crskin.config.CrSkinConfig;
import com.crskin.db.DatabaseManager;
import com.crskin.db.PlayerMapping;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * 批量查询 UUID 端点处理器
 */
public class ProfilesMinecraftHandler implements HttpHandler {

    private static final Logger logger = Logger.getLogger(ProfilesMinecraftHandler.class.getName());
    private static final Gson gson = new GsonBuilder().create();

    private final CrSkinConfig config;
    private final DatabaseManager db;
    private final AuthProvider authProvider;

    public ProfilesMinecraftHandler(CrSkinConfig config, DatabaseManager db, AuthProvider authProvider) {
        this.config = config;
        this.db = db;
        this.authProvider = authProvider;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            CrSkinHttpServer.sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String body = readRequestBody(exchange);
        JsonArray names;
        try {
            names = gson.fromJson(body, JsonArray.class);
            if (names == null) {
                CrSkinHttpServer.sendResponse(exchange, 400, "{\"error\":\"Invalid JSON\"}");
                return;
            }
        } catch (Exception e) {
            CrSkinHttpServer.sendResponse(exchange, 400, "{\"error\":\"Invalid JSON\"}");
            return;
        }

        JsonArray results = new JsonArray();
        for (JsonElement elem : names) {
            String name = elem.getAsString();

            try {
                PlayerMapping mapping = db.getMappingByMappedUsername(name);
                if (mapping != null) {
                    JsonObject r = new JsonObject();
                    r.addProperty("id", mapping.crskinUuid);
                    r.addProperty("name", mapping.mappedUsername);
                    results.add(r);
                    continue;
                }

                String mojangPrefix = config.getNamePrefix("Mojang");
                if (name.startsWith(mojangPrefix)) {
                    String original = name.substring(mojangPrefix.length());
                    mapping = db.getMappingByMappedUsername(original);
                    if (mapping != null) {
                        JsonObject r = new JsonObject();
                        r.addProperty("id", mapping.crskinUuid);
                        r.addProperty("name", mapping.mappedUsername);
                        results.add(r);
                        continue;
                    }
                }

                boolean found = false;
                for (var source : config.getAuthSources()) {
                    String prefix = config.getNamePrefix(source.getName());
                    if (name.startsWith(prefix)) {
                        String original = name.substring(prefix.length());
                        mapping = db.getMappingByMappedUsername(original);
                        if (mapping != null) {
                            JsonObject r = new JsonObject();
                            r.addProperty("id", mapping.crskinUuid);
                            r.addProperty("name", mapping.mappedUsername);
                            results.add(r);
                            found = true;
                            break;
                        }
                    }
                }
                if (found) continue;
            } catch (SQLException e) {
                logger.warning("DB error in queryUuids: " + e.getMessage());
            }

            try {
                JsonObject mojangResult = authProvider.queryMojangProfile(name.replace("-", ""), true);
                if (mojangResult != null) {
                    JsonObject r = new JsonObject();
                    r.addProperty("id", mojangResult.has("id") ? mojangResult.get("id").getAsString() : "");
                    r.addProperty("name", mojangResult.has("name") ? mojangResult.get("name").getAsString() : name);
                    results.add(r);
                }
            } catch (Exception e) {
                // ignore
            }
        }

        CrSkinHttpServer.sendJsonResponse(exchange, 200, gson.toJson(results));
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }
}
