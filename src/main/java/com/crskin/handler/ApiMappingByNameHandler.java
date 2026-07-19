package com.crskin.handler;

import com.crskin.config.CrSkinConfig;
import com.crskin.db.DatabaseManager;
import com.crskin.db.PlayerMapping;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * 按玩家名查询映射端点处理器
 */
public class ApiMappingByNameHandler implements HttpHandler {

    private static final Logger logger = Logger.getLogger(ApiMappingByNameHandler.class.getName());
    private static final Gson gson = new GsonBuilder().create();

    private final CrSkinConfig config;
    private final DatabaseManager db;

    public ApiMappingByNameHandler(CrSkinConfig config, DatabaseManager db) {
        this.config = config;
        this.db = db;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            CrSkinHttpServer.sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String name = path.replace("/api/mapping/name/", "");

        try {
            PlayerMapping mapping = db.getMappingByMappedUsername(name);
            if (mapping == null) {
                String mojangPrefix = config.getNamePrefix("Mojang");
                if (name.startsWith(mojangPrefix)) {
                    String original = name.substring(mojangPrefix.length());
                    mapping = db.getMappingByMappedUsername(original);
                }
                if (mapping == null) {
                    for (var source : config.getAuthSources()) {
                        String prefix = config.getNamePrefix(source.getName());
                        if (name.startsWith(prefix)) {
                            String original = name.substring(prefix.length());
                            mapping = db.getMappingByMappedUsername(original);
                            if (mapping != null) break;
                        }
                    }
                }
            }
            if (mapping == null) {
                CrSkinHttpServer.sendNoContent(exchange);
                return;
            }

            JsonObject result = new JsonObject();
            result.addProperty("crskin_uuid", mapping.crskinUuid);
            result.addProperty("original_uuid", mapping.originalUuid);
            result.addProperty("original_username", mapping.originalUsername);
            result.addProperty("mapped_username", mapping.mappedUsername);
            result.addProperty("source", mapping.source);
            CrSkinHttpServer.sendJsonResponse(exchange, 200, gson.toJson(result));
        } catch (SQLException e) {
            logger.severe("DB error: " + e.getMessage());
            CrSkinHttpServer.sendResponse(exchange, 500, "{\"error\":\"Database error\"}");
        }
    }
}
