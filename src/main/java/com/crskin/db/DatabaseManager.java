package com.crskin.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite 数据库管理器
 */
public class DatabaseManager {

    private final String dbPath;
    private final ThreadLocal<Connection> connLocal = ThreadLocal.withInitial(this::createConnection);

    public DatabaseManager(String dbPath) {
        this.dbPath = dbPath;
    }

    private Connection createConnection() {
        try {
            String absPath = new java.io.File(dbPath).getAbsolutePath();
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + absPath);
            conn.setAutoCommit(true);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA foreign_keys=ON");
            }
            return conn;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create DB connection: " + e.getMessage(), e);
        }
    }

    private Connection getConn() { return connLocal.get(); }

    public void initDb() throws SQLException {
        Connection conn = getConn();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='player_mappings'");
            if (rs.next()) {
                ResultSet colRs = stmt.executeQuery("PRAGMA table_info(player_mappings)");
                boolean hasCrskinUuid = false;
                while (colRs.next()) {
                    if ("crskin_uuid".equals(colRs.getString("name"))) { hasCrskinUuid = true; break; }
                }
                if (!hasCrskinUuid) {
                    stmt.execute("DROP TABLE player_mappings");
                    stmt.execute("DROP TABLE IF EXISTS join_sessions");
                }
            }

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_mappings (
                    crskin_uuid TEXT PRIMARY KEY,
                    original_uuid TEXT NOT NULL,
                    source TEXT NOT NULL,
                    source_api_url TEXT NOT NULL,
                    original_username TEXT NOT NULL,
                    mapped_username TEXT NOT NULL,
                    merged_into_uuid TEXT,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS join_sessions (
                    server_id TEXT PRIMARY KEY,
                    crskin_uuid TEXT NOT NULL,
                    original_uuid TEXT NOT NULL,
                    mapped_username TEXT NOT NULL,
                    source TEXT NOT NULL,
                    source_api_url TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
            """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mappings_original_uuid ON player_mappings(original_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mappings_mapped_username ON player_mappings(mapped_username)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_join_sessions_expiry ON join_sessions(created_at)");

            try { stmt.execute("ALTER TABLE player_mappings ADD COLUMN merged_into_uuid TEXT"); }
            catch (SQLException e) { /* 列已存在 */ }
        }
    }

    public void saveMapping(String crskinUuid, String originalUuid,
                            String originalUsername, String mappedUsername,
                            String source, String sourceApiUrl) throws SQLException {
        Connection conn = getConn();
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO player_mappings (crskin_uuid, original_uuid, original_username,
                                         mapped_username, source, source_api_url, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, datetime('now'))
            ON CONFLICT(crskin_uuid) DO UPDATE SET
                original_uuid = excluded.original_uuid,
                original_username = excluded.original_username,
                mapped_username = excluded.mapped_username,
                source = excluded.source,
                source_api_url = excluded.source_api_url,
                updated_at = datetime('now')
        """)) {
            ps.setString(1, crskinUuid);
            ps.setString(2, originalUuid);
            ps.setString(3, originalUsername);
            ps.setString(4, mappedUsername);
            ps.setString(5, source);
            ps.setString(6, sourceApiUrl);
            ps.executeUpdate();
        }
    }

    public PlayerMapping getMappingByCrskinUuid(String crskinUuid) throws SQLException {
        Connection conn = getConn();
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT * FROM player_mappings WHERE crskin_uuid = ?")) {
            ps.setString(1, crskinUuid);
            return mapRow(ps.executeQuery());
        }
    }

    public PlayerMapping getMappingByOriginalUuid(String originalUuid) throws SQLException {
        Connection conn = getConn();
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT * FROM player_mappings WHERE original_uuid = ?")) {
            ps.setString(1, originalUuid);
            return mapRow(ps.executeQuery());
        }
    }

    public PlayerMapping getMappingByMappedUsername(String mappedUsername) throws SQLException {
        Connection conn = getConn();
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT * FROM player_mappings WHERE mapped_username = ?")) {
            ps.setString(1, mappedUsername);
            return mapRow(ps.executeQuery());
        }
    }

    public List<PlayerMapping> getAllMappings() throws SQLException {
        Connection conn = getConn();
        List<PlayerMapping> result = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM player_mappings ORDER BY updated_at DESC")) {
            while (rs.next()) result.add(rowToMapping(rs));
        }
        return result;
    }

    public void mergeMappings(String primaryUuid, String secondaryUuid,
                              String mergedName) throws SQLException {
        Connection conn = getConn();
        try (PreparedStatement ps1 = conn.prepareStatement(
            "UPDATE player_mappings SET mapped_username = ?, updated_at = datetime('now') WHERE crskin_uuid = ?");
             PreparedStatement ps2 = conn.prepareStatement(
            "UPDATE player_mappings SET mapped_username = ?, merged_into_uuid = ?, updated_at = datetime('now') WHERE crskin_uuid = ?")) {
            ps1.setString(1, mergedName); ps1.setString(2, primaryUuid); ps1.executeUpdate();
            ps2.setString(1, mergedName); ps2.setString(2, primaryUuid); ps2.setString(3, secondaryUuid); ps2.executeUpdate();
        }
    }

    public PlayerMapping followMergeChain(PlayerMapping mapping) throws SQLException {
        java.util.Set<String> seen = new java.util.HashSet<>();
        while (mapping != null && mapping.mergedIntoUuid != null && !mapping.mergedIntoUuid.isEmpty()) {
            String targetUuid = mapping.mergedIntoUuid;
            if (seen.contains(targetUuid)) break;
            seen.add(targetUuid);
            PlayerMapping target = getMappingByCrskinUuid(targetUuid);
            if (target == null) break;
            mapping = target;
        }
        return mapping;
    }

    private PlayerMapping mapRow(ResultSet rs) throws SQLException {
        if (rs.next()) return rowToMapping(rs);
        return null;
    }

    private PlayerMapping rowToMapping(ResultSet rs) throws SQLException {
        PlayerMapping m = new PlayerMapping();
        m.crskinUuid = rs.getString("crskin_uuid");
        m.originalUuid = rs.getString("original_uuid");
        m.source = rs.getString("source");
        m.sourceApiUrl = rs.getString("source_api_url");
        m.originalUsername = rs.getString("original_username");
        m.mappedUsername = rs.getString("mapped_username");
        m.mergedIntoUuid = rs.getString("merged_into_uuid");
        m.createdAt = rs.getString("created_at");
        m.updatedAt = rs.getString("updated_at");
        return m;
    }
}
