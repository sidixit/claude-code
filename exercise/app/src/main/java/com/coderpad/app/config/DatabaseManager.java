package com.coderpad.app.config;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Component
public class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private final String jdbcUrl;
    private final String jdbcUser;
    private final String jdbcPassword;

    private Connection connection;

    public DatabaseManager() {
        this.jdbcUrl = envOrDefault("JDBC_URL", "jdbc:sqlite:interview.db");
        this.jdbcUser = System.getenv("JDBC_USER");
        this.jdbcPassword = System.getenv("JDBC_PASSWORD");
    }

    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            log.info("Opening JDBC connection to {}", jdbcUrl);
            if (jdbcUser != null) {
                connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
            } else {
                connection = DriverManager.getConnection(jdbcUrl);
            }
        }
        return connection;
    }

    public void verifyConnection() throws SQLException {
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            if (!rs.next() || rs.getInt(1) != 1) {
                throw new SQLException("SELECT 1 did not return 1");
            }
            log.info("DB liveness OK (SELECT 1 returned 1) — url={}", jdbcUrl);
        }
    }

    @PreDestroy
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                log.info("Closed JDBC connection");
            } catch (SQLException e) {
                log.warn("Error closing JDBC connection", e);
            }
        }
    }

    private static String envOrDefault(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
