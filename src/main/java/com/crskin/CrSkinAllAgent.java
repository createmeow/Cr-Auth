package com.crskin;

import com.crskin.config.CrSkinConfig;
import com.crskin.db.DatabaseManager;
import com.crskin.auth.AuthProvider;
import com.crskin.handler.CrSkinHttpServer;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.util.logging.Logger;

/**
 * crskin-all: 全功能一体化 JAR
 *
 * 作为 Java Agent 使用（-javaagent:crskin-all.jar=http://127.0.0.1:25578/）:
 *   1. 启动 crskin HTTP 认证服务器（后台线程）
 *   2. 自动检测 plugins/ 目录并部署 CrSkinRename 插件
 *   3. 委托给 authlib-injector，劫持 Minecraft 认证请求
 *   一行命令搞定所有功能，不需要单独启动认证服务器
 *
 * 作为独立应用运行（java -jar crskin-all.jar）:
 *   启动 HTTP 认证服务器 + 部署 CrSkinRename 插件（前台）
 */
public class CrSkinAllAgent {

    private static final Logger logger = Logger.getLogger(CrSkinAllAgent.class.getName());

    // 保存服务器实例，用于关闭
    private static CrSkinHttpServer serverInstance;

    /**
     * Java Agent 入口
     * - 先启动 HTTP 服务器
     * - 再委托给 authlib-injector（此时 URL 已可用，authlib-injector 不会报 Connection refused）
     * - 一行命令完成全部功能
     */
    public static void premain(String agentArgs, Instrumentation inst) throws Exception {
        logger.info("========================================");
        logger.info("crskin-all Agent 启动 (一体化模式)");
        logger.info("========================================");

        try {
            // 1. 解析配置
            CrSkinConfig config = new CrSkinConfig();
            config.load();

            // 2. 解析 agentArgs URL，覆盖配置
            if (agentArgs != null && !agentArgs.isEmpty()) {
                parseAgentUrl(agentArgs, config);
            }

            // 3. 初始化数据库
            String dbPath = config.getDatabasePath();
            DatabaseManager db = new DatabaseManager(dbPath);
            db.initDb();
            logger.info("数据库: " + dbPath);

            // 4. 启动 HTTP 服务器（后台线程）
            AuthProvider authProvider = new AuthProvider(config);
            serverInstance = new CrSkinHttpServer(config, db, authProvider);
            serverInstance.start();
            logger.info("HTTP 服务器已就绪: http://" + config.getHost() + ":" + config.getPort());

            // 5. 部署 CrSkinRename 插件
            deployPlugin();

            // 6. 委托给 authlib-injector（此时 HTTP 服务器已运行，元数据查询会成功）
            logger.info("正在启动 authlib-injector...");
            try {
                moe.yushi.authlibinjector.Premain.premain(agentArgs, inst);
            } catch (moe.yushi.authlibinjector.InitializationException e) {
                logger.severe("authlib-injector 初始化失败: " + e.getMessage());
                stopServer();
                throw e;
            }
            logger.info("crskin-all 一体化模式启动完成");

        } catch (Exception e) {
            logger.severe("crskin-all 启动失败: " + e.getMessage());
            e.printStackTrace();
            stopServer();
            throw e;
        }
    }

    /**
     * 独立应用入口
     * java -jar crskin-all.jar （前台运行，按 Ctrl+C 停止）
     */
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("crskin-all 独立服务器模式启动");
        logger.info("========================================");

        try {
            // 1. 加载配置
            CrSkinConfig config = new CrSkinConfig();
            config.load();

            // 2. 初始化数据库
            String dbPath = config.getDatabasePath();
            DatabaseManager db = new DatabaseManager(dbPath);
            db.initDb();
            logger.info("数据库: " + dbPath);

            // 3. 启动 HTTP 服务器
            AuthProvider authProvider = new AuthProvider(config);
            serverInstance = new CrSkinHttpServer(config, db, authProvider);
            serverInstance.start();

            // 4. 部署 CrSkinRename 插件
            deployPlugin();

            // 5. 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("正在关闭 crskin-all...");
                stopServer();
            }));

            // 保持主线程运行
            logger.info("crskin-all 运行中，按 Ctrl+C 停止");
            Thread.currentThread().join();

        } catch (Exception e) {
            logger.severe("启动失败: " + e.getMessage());
            e.printStackTrace();
            stopServer();
            System.exit(1);
        }
    }

    /**
     * 解析 agent 参数中的 URL，覆盖 host 和 port
     */
    private static void parseAgentUrl(String agentArgs, CrSkinConfig config) {
        try {
            String url = agentArgs;
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
            }
            if (!url.endsWith("/")) {
                url += "/";
            }
            java.net.URI uri = java.net.URI.create(url);
            if (uri.getHost() != null) {
                config.setHost(uri.getHost());
            }
            if (uri.getPort() > 0) {
                config.setPort(uri.getPort());
            }
            logger.info("从 agent 参数解析: host=" + config.getHost() + ", port=" + config.getPort());
        } catch (Exception e) {
            logger.warning("无法解析 agent 参数: " + agentArgs);
        }
    }

    /**
     * 停止 HTTP 服务器
     */
    private static void stopServer() {
        if (serverInstance != null) {
            try {
                serverInstance.stop();
            } catch (Exception e) {
                // ignore
            }
            serverInstance = null;
        }
    }

    /**
     * 自动部署 CrSkinRename 插件
     */
    private static void deployPlugin() {
        File pluginsDir = new File("plugins");
        if (!pluginsDir.exists() || !pluginsDir.isDirectory()) {
            logger.info("未检测到 plugins/ 目录，跳过 CrSkinRename 部署");
            return;
        }

        File targetFile = new File(pluginsDir, "CrSkinRename.jar");
        if (targetFile.exists()) {
            long embeddedSize = getEmbeddedPluginSize();
            if (embeddedSize > 0 && targetFile.length() == embeddedSize) {
                logger.info("CrSkinRename.jar 已存在，跳过部署");
                return;
            }
        }

        try (InputStream in = CrSkinAllAgent.class.getClassLoader()
                .getResourceAsStream("CrSkinRename.jar")) {
            if (in == null) {
                logger.warning("CrSkinRename.jar 未嵌入资源中，跳过部署");
                return;
            }
            try (FileOutputStream out = new FileOutputStream(targetFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
            }
            logger.info("已部署 CrSkinRename.jar 到 plugins/ 目录");
        } catch (IOException e) {
            logger.warning("部署 CrSkinRename.jar 失败: " + e.getMessage());
        }
    }

    private static long getEmbeddedPluginSize() {
        try (InputStream in = CrSkinAllAgent.class.getClassLoader()
                .getResourceAsStream("CrSkinRename.jar")) {
            if (in == null) return 0;
            byte[] buf = new byte[8192];
            long total = 0;
            int len;
            while ((len = in.read(buf)) != -1) total += len;
            return total;
        } catch (IOException e) {
            return 0;
        }
    }
}
