package com.crskin.handler;

import com.crskin.config.CrSkinConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * 元数据端点处理器
 */
public class MetadataHandler implements HttpHandler {

    private static final Logger logger = Logger.getLogger(MetadataHandler.class.getName());
    private static final Gson gson = new GsonBuilder().create();

    private final CrSkinConfig config;

    public MetadataHandler(CrSkinConfig config) {
        this.config = config;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            CrSkinHttpServer.sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        logger.fine("INCOMING: GET / (headers: " + exchange.getRequestHeaders() + ")");

        JsonObject meta = new JsonObject();
        meta.addProperty("serverName", "crskin");
        meta.addProperty("implementationName", "crskin");
        meta.addProperty("implementationVersion", "1.0.0");
        meta.addProperty("feature.no_mojang_namespace", true);
        meta.addProperty("feature.enable_mojang_anti_features", false);
        meta.addProperty("feature.enable_profile_key", false);
        meta.addProperty("feature.username_check", false);
        meta.addProperty("feature.legacy_skin_api", false);

        JsonObject result = new JsonObject();
        result.add("meta", meta);
        result.add("skinDomains", new com.google.gson.JsonArray());

        CrSkinHttpServer.sendJsonResponse(exchange, 200, gson.toJson(result));
    }
}
