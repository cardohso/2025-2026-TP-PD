package pt.isec.pd.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectDB {
    private static volatile Connection conn;
    private static volatile String url = initDefaultUrl();

    private ConnectDB() { }

    private static String initDefaultUrl() {
        String file = System.getProperty("pd.data.db");
        if (file == null || file.isBlank()) {
            file = "test.db";
        }
        return "jdbc:sqlite:" + file;
    }

    public static void setDatabaseFile(String file) {
        if (file == null || file.isBlank()){

        }
        synchronized (ConnectDB.class) {
            if (conn != null) throw new IllegalStateException("Cannot change database file after connection is opened");
            url = "jdbc:sqlite:" + file;
        }
    }

    public static Connection getConnection() {
        if (conn == null) {
            synchronized (ConnectDB.class) {
                if (conn == null) {
                    try {
                        // Ensure parent directory exists if a path was provided
                        String pathPart = url.replaceFirst("^jdbc:sqlite:", "");
                        Path parent = Path.of(pathPart).toAbsolutePath().getParent();
                        if (parent != null && !Files.exists(parent)) {
                            Files.createDirectories(parent);
                        }

                        conn = DriverManager.getConnection(url);

                        // Register shutdown hook to ensure connection is closed on JVM exit
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try {
                                closeConnection();
                            } catch (Exception ignored) { }
                        }));
                    } catch (SQLException ex) {
                        throw new IllegalStateException("Unable to open database connection: " + ex.getMessage(), ex);
                    } catch (Exception ex) {
                        throw new IllegalStateException("Failed preparing database file: " + ex.getMessage(), ex);
                    }
                }
            }
        }
        return conn;
    }

    public static void closeConnection() {
        synchronized (ConnectDB.class) {
            if (conn != null) {
                try {
                    if (!conn.isClosed()) conn.close();
                } catch (SQLException ex) {
                    throw new IllegalStateException("Failed to close DB connection", ex);
                } finally {
                    conn = null;
                }
            }
        }
    }
}
