package pt.isec.pd.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class ConnectDB {
    // Default URL
    private static volatile String url = initDefaultUrl();
    // Indicates whether any connection has been opened (prevents changing DB file afterwards)
    private static volatile boolean opened = false;

    private ConnectDB() { }

    private static String initDefaultUrl() {
        String file = System.getProperty("pd.data.db");
        if (file == null || file.isBlank()) {
            file = "data/db/test.db";
        }
        return "jdbc:sqlite:" + file;
    }

    public static synchronized void setDatabaseFile(String file) {
        if (file == null || file.isBlank()) return;
        if (opened) throw new IllegalStateException("Cannot change database file after a connection was opened");
        url = "jdbc:sqlite:" + file;
        System.out.println("Database configuration set to: " + file);
    }

    public static Connection getConnection() throws SQLException {
        // Ensure parent directories exist for file-based DBs
        try {
            String pathPart = url.replaceFirst("^jdbc:sqlite:", "");
            if (!":memory:".equals(pathPart)) {
                Path dbPath = Path.of(pathPart).toAbsolutePath();
                Path parent = dbPath.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                    System.out.println("Data directories created at: " + parent);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed preparing database file: " + ex.getMessage(), ex);
        }

        Connection conn = DriverManager.getConnection(url);
        // Enable foreign keys for this connection; if it fails, close the connection and propagate
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException ex) {
            try { conn.close(); } catch (Exception ignored) { }
            throw ex;
        }

        opened = true;
        System.out.println("Opened DB connection. URL: " + url);
        return conn;
    }
}
