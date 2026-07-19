package com.crskin.handler;

import com.crskin.db.DatabaseManager;
import com.crskin.db.PlayerMapping;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

/**
 * 列出所有映射端点处理器
 */
public class ApiMappingsHandler implements HttpHandler {

    private static final Logger logger = Logger.getLogger(ApiMappingsHandler.class.getName());
    private static final Gson gson = new GsonBuilder().create();

    private final DatabaseManager db;

    public ApiMappingsHandler(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            CrSkinHttpServer.sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            List<PlayerMapping> mappings = db.getAllMappings();
            JsonArray arr = new JsonArray();
            for (PlayerMapping m : mappings) {
                JsonObject obj = new JsonObject();
                obj.addProperty("crskin_uuid", m.crskinUuid);
                obj.addProperty("original_uuid", m.originalUuid);
                obj.addProperty("source", m.source);
                obj.addProperty("source_api_url", m.sourceApiUrl);
                obj.addProperty("original_username", m.originalUsername);
                obj.addProperty("mapped_username", m.mappedUsername);
                obj.addProperty("merged_into_uuid", m.mergedIntoUuid);
                obj.addProperty("created_at", m.createdAt);
                obj.addProperty("updated_at", m.updatedAt);
                arr.add(obj);
            }
            CrSkinHttpServer.sendJsonResponse(exchange, 200, gson.toJson(arr));
        } catch (SQLException e) {
            logger.severe("DB error: " + e.getMessage());
            CrSkinHttpServer.sendResponse(exchange, 500, "{\"error\":\"Database error\"}");
        }
    }
}
