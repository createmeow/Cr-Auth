package com.crskin.handler;

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
 * 按原始 UUID 查询映射端点处理器
 */
public class ApiMappingByOriginalHandler implements HttpHandler {

    private static final Logger logger = Logger.getLogger(ApiMappingByOriginalHandler.class.getName());
    private static final Gson gson = new GsonBuilder().create();

    private final DatabaseManager db;

    public ApiMappingByOriginalHandler(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            CrSkinHttpServer.sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String uuidStr = path.replace("/api/mapping/by-original/", "");
        String uuidClean = uuidStr.replace("-", "");

        try {
            PlayerMapping mapping = db.getMappingByOriginalUuid(uuidClean);
            if (mapping == null) {
                CrSkinHttpServer.sendNoContent(exchange);
                return;
            }

            JsonObject result = new JsonObject();
            result.addProperty("crskin_uuid", mapping.crskinUuid);
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
