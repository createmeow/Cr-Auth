package com.crskin.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

/**
 * crskin 配置管理器
 * 从 crauth-config.json 加载配置，支持从 JAR 同目录读取，支持 -D 系统属性覆盖
 * 如果 crauth-config.json 不存在，会自动创建默认配置文件
 */
public class CrSkinConfig {
    private static final Logger logger = Logger.getLogger(CrSkinConfig.class.getName());

    private String host = "0.0.0.0";
    private int port = 25578;
    private boolean rejectOfflinePlayers = true;
    private int maxUsernameLength = 16;
    private String dbPath; // null 表示自动检测
    private final Map<String, AuthSource> authSources = new LinkedHashMap<>();
    private final Map<String, String> namePrefixes = new HashMap<>();

    public static final String MOJANG_HASJOINED_URL = "https://sessionserver.mojang.com/session/minecraft/hasJoined";
    public static final String MOJANG_PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile";

    private static final Map<String, String> DEFAULT_PREFIXES = Map.of("Mojang", "Mo-");

    public CrSkinConfig() {}

    /**
     * 加载配置，优先级：
     *   1. JAR 同目录 crauth-config.json
     *   2. 当前工作目录 crauth-config.json
     *   3. 自动创建默认 crauth-config.json
     *   4. -D 系统属性覆盖
     */
    public void load() {
        File configFile = findConfigFile();

        if (configFile != null && configFile.exists()) {
            loadFromFile(configFile);
            logger.info("已加载配置文件: " + configFile.getAbsolutePath());
        } else {
            // 自动创建默认配置文件
            configFile = createDefaultConfig();
            if (configFile != null) {
                logger.info("已自动创建默认配置文件: " + configFile.getAbsolutePath());
                // 创建后立即加载
                loadFromFile(configFile);
            }
        }

        applySystemProperties();

        // 打印最终配置摘要
        logger.info("配置摘要: host=" + host + ", port=" + port
            + ", 认证源=" + getAuthSourcesSummary());
    }

    /**
     * 查找配置文件（兼容旧版调用）
     */
    public void loadFromJarDirectory() {
        load();
    }

    /**
     * 获取数据库路径，优先级：
     *   1. -Dcrskin.db 系统属性
     *   2. crauth-config.json 中的 db_path
     *   3. 当前工作目录下的 crskin.db（Python 版兼容）
     *   4. JAR 同目录下的 crskin.db
     */
    public String getDatabasePath() {
        // 1. -Dcrskin.db 系统属性（最高优先级）
        String sysDb = System.getProperty("crskin.db");
        if (sysDb != null && !sysDb.isEmpty()) {
            logger.info("数据库路径（来自 -Dcrskin.db）: " + sysDb);
            return sysDb;
        }

        // 2. crauth-config.json 中的 db_path
        if (dbPath != null && !dbPath.isEmpty()) {
            logger.info("数据库路径（来自 crauth-config.json）: " + dbPath);
            return dbPath;
        }

        // 3. 当前工作目录下的 crskin.db（Python 版兼容）
        File cwdDb = new File("crskin.db");
        if (cwdDb.exists()) {
            logger.info("数据库路径（当前工作目录）: " + cwdDb.getAbsolutePath());
            return cwdDb.getAbsolutePath();
        }

        // 4. JAR 同目录下的 crskin.db
        File jarDir = getJarDirectory();
        if (jarDir != null) {
            File jarDb = new File(jarDir, "crskin.db");
            if (jarDb.exists()) {
                logger.info("数据库路径（JAR 同目录）: " + jarDb.getAbsolutePath());
                return jarDb.getAbsolutePath();
            }
            // 目录存在，使用 JAR 目录
            logger.info("数据库路径（JAR 同目录，新建）: " + jarDb.getAbsolutePath());
            return jarDb.getAbsolutePath();
        }

        // 5. 默认：当前工作目录
        String defaultDb = new File("crskin.db").getAbsolutePath();
        logger.info("数据库路径（默认）: " + defaultDb);
        return defaultDb;
    }

    /**
     * 查找 crauth-config.json 的位置
     * 优先 JAR 同目录，其次当前工作目录
     */
    private File findConfigFile() {
        // 1. JAR 同目录
        File jarDir = getJarDirectory();
        if (jarDir != null) {
            File configFile = new File(jarDir, "crauth-config.json");
            if (configFile.exists()) {
                return configFile;
            }
        }

        // 2. 当前工作目录
        File configFile = new File("crauth-config.json");
        if (configFile.exists()) {
            return configFile;
        }

        // 3. JAR 同目录（即使不存在也返回，用于自动创建）
        if (jarDir != null) {
            return new File(jarDir, "crauth-config.json");
        }

        return new File("crauth-config.json");
    }

    /**
     * 获取 JAR 所在目录
     */
    private File getJarDirectory() {
        try {
            String jarPath = CrSkinConfig.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath();
            File jarFile = new File(jarPath);
            if (jarFile.isFile() && jarFile.getName().endsWith(".jar")) {
                File dir = jarFile.getParentFile();
                if (dir != null) return dir;
            }
        } catch (Exception e) {
            logger.fine("无法获取 JAR 目录: " + e.getMessage());
        }
        return null;
    }

    /**
     * 自动创建默认 crauth-config.json
     */
    private File createDefaultConfig() {
        File configFile = findConfigFile();
        if (configFile == null) {
            configFile = new File("crauth-config.json");
        }

        // 如果文件已存在则不覆盖
        if (configFile.exists()) {
            return configFile;
        }

        try {
            // 确保父目录存在
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            JsonObject config = new JsonObject();
            config.addProperty("host", "0.0.0.0");
            config.addProperty("port", 25578);
            config.addProperty("reject_offline_players", true);
            config.addProperty("max_username_length", 16);

            JsonObject authlib = new JsonObject();

            JsonObject mojang = new JsonObject();
            mojang.addProperty("serverlink", "https://sessionserver.mojang.com/session/minecraft/hasJoined");
            mojang.addProperty("Abbreviation", "Mo");
            authlib.add("Mojang", mojang);

            JsonObject littleSkin = new JsonObject();
            littleSkin.addProperty("serverlink", "https://littleskin.cn/api/yggdrasil");
            littleSkin.addProperty("Abbreviation", "Li");
            authlib.add("LittleSkin", littleSkin);

            config.add("authlib", authlib);

            String json = new GsonBuilder().setPrettyPrinting().create().toJson(config);
            Files.writeString(configFile.toPath(), json, StandardCharsets.UTF_8);

            logger.info("已创建默认配置文件: " + configFile.getAbsolutePath());
            return configFile;
        } catch (IOException e) {
            logger.warning("创建默认配置文件失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 应用 JVM 系统属性覆盖配置
     */
    public void applySystemProperties() {
        String sysHost = System.getProperty("crskin.host");
        if (sysHost != null && !sysHost.isEmpty()) {
            host = sysHost;
            logger.info("系统属性覆盖 host: " + host);
        }

        String sysPort = System.getProperty("crskin.port");
        if (sysPort != null && !sysPort.isEmpty()) {
            try {
                port = Integer.parseInt(sysPort);
                logger.info("系统属性覆盖 port: " + port);
            } catch (NumberFormatException e) {
                logger.warning("无效的 crskin.port 值: " + sysPort);
            }
        }
    }

    private void loadFromFile(File file) {
        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            JsonObject config = gson.fromJson(reader, JsonObject.class);
            if (config == null) return;

            if (config.has("host")) host = config.get("host").getAsString();
            if (config.has("port")) port = config.get("port").getAsInt();
            if (config.has("reject_offline_players"))
                rejectOfflinePlayers = config.get("reject_offline_players").getAsBoolean();
            if (config.has("max_username_length"))
                maxUsernameLength = config.get("max_username_length").getAsInt();
            if (config.has("db_path"))
                dbPath = config.get("db_path").getAsString();

            if (config.has("authlib")) {
                JsonObject authlib = config.getAsJsonObject("authlib");
                for (Map.Entry<String, com.google.gson.JsonElement> entry : authlib.entrySet()) {
                    String name = entry.getKey();
                    JsonObject info = entry.getValue().getAsJsonObject();
                    String serverlink = info.has("serverlink") ? info.get("serverlink").getAsString() : "";
                    String abbreviation = info.has("Abbreviation") ? info.get("Abbreviation").getAsString() : "";

                    if ("Mojang".equals(name)) {
                        if (!abbreviation.isEmpty()) namePrefixes.put("Mojang", abbreviation + "-");
                        continue;
                    }

                    if (!serverlink.isEmpty()) {
                        AuthSource source = new AuthSource(name, serverlink, abbreviation);
                        authSources.put(name, source);
                        if (!abbreviation.isEmpty()) namePrefixes.put(name, abbreviation + "-");
                    }
                }
            }

            logger.info("已加载 " + authSources.size() + " 个认证源");
        } catch (IOException e) {
            logger.warning("加载配置文件失败: " + e.getMessage());
        }
    }

    private String getAuthSourcesSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(authSources.size() + 1); // +1 for Mojang
        sb.append(" (Mojang");
        for (String name : authSources.keySet()) {
            sb.append(", ").append(name);
        }
        sb.append(")");
        return sb.toString();
    }

    public String getNamePrefix(String sourceName) {
        if (namePrefixes.containsKey(sourceName)) return namePrefixes.get(sourceName);
        if (DEFAULT_PREFIXES.containsKey(sourceName)) return DEFAULT_PREFIXES.get(sourceName);
        return getAutoPrefix(sourceName);
    }

    private String getAutoPrefix(String sourceName) {
        String clean = sourceName.replaceAll("[^a-zA-Z]", "");
        if (clean.length() >= 2) {
            return clean.substring(0, 1).toUpperCase() + clean.substring(1, 2).toLowerCase() + "-";
        }
        return clean.toUpperCase() + "-";
    }

    // Getters & Setters
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public boolean isRejectOfflinePlayers() { return rejectOfflinePlayers; }
    public int getMaxUsernameLength() { return maxUsernameLength; }
    public Collection<AuthSource> getAuthSources() { return authSources.values(); }
    public AuthSource getAuthSource(String name) { return authSources.get(name); }
    public Map<String, AuthSource> getAuthSourcesMap() { return authSources; }
}
