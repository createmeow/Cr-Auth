package com.crskin.handler;

import com.crskin.auth.AuthProvider;
import com.crskin.config.CrSkinConfig;
import com.crskin.db.DatabaseManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * HTTP 服务器封装类
 */
public class CrSkinHttpServer {

    private static final Logger logger = Logger.getLogger(CrSkinHttpServer.class.getName());

    private final HttpServer server;
    private final CrSkinConfig config;
    private final DatabaseManager db;
    private final AuthProvider authProvider;

    public CrSkinHttpServer(CrSkinConfig config, DatabaseManager db, AuthProvider authProvider) throws IOException {
        this.config = config;
        this.db = db;
        this.authProvider = authProvider;

        server = HttpServer.create(new InetSocketAddress(config.getHost(), config.getPort()), 0);

        ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);

        registerRoutes();
    }

    private void registerRoutes() {
        server.createContext("/", new MetadataHandler(config));
        server.createContext("/sessionserver/session/minecraft/hasJoined",
            new HasJoinedHandler(config, db, authProvider));
        server.createContext("/sessionserver/session/minecraft/profile/",
            new ProfileHandler(config, db, authProvider));
        server.createContext("/api/profiles/minecraft",
            new ProfilesMinecraftHandler(config, db, authProvider));
        server.createContext("/sessionserver/session/minecraft/join",
            (exchange) -> {
                logger.warning("Received unexpected join request (should be client-side)");
                sendResponse(exchange, 204, null);
            });
        server.createContext("/minecraftservices/player/certificates",
            (exchange) -> {
                logger.fine("Certificate request received (returning empty)");
                String body = "{\"keyPair\":null,\"expiresAt\":null,\"refreshedAfter\":null}";
                sendJsonResponse(exchange, 200, body);
            });
        server.createContext("/api/mapping/by-original/",
            new ApiMappingByOriginalHandler(db));
        server.createContext("/api/mapping/",
            new ApiMappingHandler(db));
        server.createContext("/api/mapping/name/",
            new ApiMappingByNameHandler(config, db));
        server.createContext("/api/mappings",
            new ApiMappingsHandler(db));
    }

    public void start() {
        server.start();
        logger.info("HTTP server started on " + config.getHost() + ":" + config.getPort());
    }

    public void stop() {
        server.stop(0);
    }

    public static void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        if (body != null) {
            byte[] bytes = body.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
        } else {
            exchange.sendResponseHeaders(statusCode, -1);
        }
        exchange.close();
    }

    public static void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonBody) throws IOException {
        byte[] bytes = jsonBody.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    public static void sendNoContent(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }
}
