package com.coderpad.app.db;

import com.coderpad.app.config.DatabaseManager;
import com.coderpad.app.model.DeviceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
public class DeviceEventRepository {

    private static final Logger log = LoggerFactory.getLogger(DeviceEventRepository.class);

    static final String TABLE = "device_events";

    private static final Map<String, String> EXPECTED_COLUMNS = new LinkedHashMap<>();
    static {
        EXPECTED_COLUMNS.put("id",              "INTEGER PRIMARY KEY AUTOINCREMENT");
        EXPECTED_COLUMNS.put("device_id",       "TEXT NOT NULL");
        EXPECTED_COLUMNS.put("user_id",         "INTEGER NOT NULL");
        EXPECTED_COLUMNS.put("event_type",      "TEXT NOT NULL");
        EXPECTED_COLUMNS.put("latitude",        "REAL NOT NULL");
        EXPECTED_COLUMNS.put("longitude",       "REAL NOT NULL");
        EXPECTED_COLUMNS.put("accuracy_meters", "REAL");
        EXPECTED_COLUMNS.put("altitude_meters", "REAL");
        EXPECTED_COLUMNS.put("speed_mps",       "REAL");
        EXPECTED_COLUMNS.put("heading_degrees", "REAL");
        EXPECTED_COLUMNS.put("tags",            "TEXT");
        EXPECTED_COLUMNS.put("metadata",        "TEXT");
        EXPECTED_COLUMNS.put("is_foreground",   "INTEGER");
        EXPECTED_COLUMNS.put("battery_pct",     "INTEGER");
        EXPECTED_COLUMNS.put("timestamp_ms",    "INTEGER NOT NULL");
        EXPECTED_COLUMNS.put("session_id",      "TEXT");
    }

    private final DatabaseManager db;

    public DeviceEventRepository(DatabaseManager db) {
        this.db = db;
    }

    public void ensureSchema() throws SQLException {
        Connection conn = db.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(buildCreateTableSql());
            log.info("Ensured table '{}' exists", TABLE);

            Set<String> existing = readColumns(conn);
            for (Map.Entry<String, String> entry : EXPECTED_COLUMNS.entrySet()) {
                String col = entry.getKey();
                if ("id".equals(col)) continue;
                if (!existing.contains(col)) {
                    String addColType = stripNotNull(entry.getValue());
                    String alter = "ALTER TABLE " + TABLE + " ADD COLUMN " + col + " " + addColType;
                    stmt.executeUpdate(alter);
                    log.info("Added missing column: {}", col);
                }
            }
        }
    }

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " (" +
            "device_id, user_id, event_type, latitude, longitude, accuracy_meters, altitude_meters, " +
            "speed_mps, heading_degrees, battery_pct, timestamp_ms, session_id, tags, metadata, is_foreground" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public long insert(DeviceEvent e) throws SQLException {
        Connection conn = db.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, e.getDeviceId());
            ps.setLong(2, e.getUserId());
            ps.setString(3, e.getEventType());
            ps.setDouble(4, e.getLat());
            ps.setDouble(5, e.getLon());
            ps.setDouble(6, e.getAccuracyMeters());
            ps.setDouble(7, e.getAltitudeMeters());
            ps.setDouble(8, e.getSpeedMps());
            ps.setDouble(9, e.getHeadingDegrees());
            ps.setInt(10, e.getBatteryPct());
            ps.setLong(11, e.getTimestampMs());
            if (e.getSessionId().isEmpty()) ps.setNull(12, Types.VARCHAR); else ps.setString(12, e.getSessionId());
            ps.setString(13, jsonArray(e.getTagsList()));
            ps.setString(14, jsonObject(e.getMetadataMap()));
            ps.setInt(15, e.getIsForeground() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    public void describeTable() throws SQLException {
        Connection conn = db.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + TABLE + ")")) {
            log.info("Schema for {}:", TABLE);
            while (rs.next()) {
                log.info("  {} {}{}{}",
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getInt("notnull") == 1 ? " NOT NULL" : "",
                        rs.getInt("pk") == 1 ? " PRIMARY KEY" : "");
            }
        }
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TABLE)) {
            if (rs.next()) {
                log.info("{} row count: {}", TABLE, rs.getLong(1));
            }
        }
    }

    private static String buildCreateTableSql() {
        StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(TABLE).append(" (\n");
        int i = 0;
        for (Map.Entry<String, String> e : EXPECTED_COLUMNS.entrySet()) {
            sb.append("    ").append(e.getKey()).append(" ").append(e.getValue());
            if (++i < EXPECTED_COLUMNS.size()) sb.append(",");
            sb.append("\n");
        }
        sb.append(")");
        return sb.toString();
    }

    private static Set<String> readColumns(Connection conn) throws SQLException {
        Set<String> cols = new LinkedHashSet<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + TABLE + ")")) {
            while (rs.next()) {
                cols.add(rs.getString("name"));
            }
        }
        return cols;
    }

    private static String stripNotNull(String type) {
        return type.replace(" NOT NULL", "").trim();
    }

    private static String jsonArray(java.util.List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(values.get(i))).append("\"");
        }
        return sb.append("]").toString();
    }

    private static String jsonObject(Map<String, String> kv) {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, String> e : kv.entrySet()) {
            if (i++ > 0) sb.append(",");
            sb.append("\"").append(escapeJson(e.getKey())).append("\":\"")
              .append(escapeJson(e.getValue())).append("\"");
        }
        return sb.append("}").toString();
    }

    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
