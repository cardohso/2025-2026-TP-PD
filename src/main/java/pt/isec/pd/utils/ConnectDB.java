package pt.isec.pd.utils;

import pt.isec.pd.utils.DBSchema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectDB {
    private static volatile Connection conn;
    // Default URL
    private static volatile String url = initDefaultUrl();

    private ConnectDB() { }

    private static String initDefaultUrl() {
        // Checks system property 'pd.data.db' for custom file path
        String file = System.getProperty("pd.data.db");
        if (file == null || file.isBlank()) {
            // Default path
            file = "data/db/test.db";
        }
        return "jdbc:sqlite:" + file;
    }

    public static void setDatabaseFile(String file) {
        if (file == null || file.isBlank()){
            return;
        }
        synchronized (ConnectDB.class) {
            if (conn != null) throw new IllegalStateException("Cannot change database file after connection is opened");
            url = "jdbc:sqlite:" + file;
        }
        System.out.println("Database configuration set to: " + file);
    }

    public static Connection getConnection() {
        if (conn == null) {
            synchronized (ConnectDB.class) {
                if (conn == null) {
                    try {
                        String pathPart = url.replaceFirst("^jdbc:sqlite:", "");
                        boolean needsInit = false;

                        // Check if it's an in-memory DB
                        if (":memory:".equals(pathPart)) {
                            needsInit = true;
                        } else {
                            Path dbPath = Path.of(pathPart).toAbsolutePath();
                            Path parent = dbPath.getParent();

                            // Create parent folders in case they don't exist
                            if (parent != null && !Files.exists(parent)) {
                                Files.createDirectories(parent);
                                System.out.println("Data directories created at: " + parent);
                            }

                            boolean existed = Files.exists(dbPath);
                            boolean isEmpty = true;
                            if (existed) {
                                try {
                                    isEmpty = Files.size(dbPath) == 0;
                                } catch (IOException e) {
                                    // Treat I/O error as empty file (will be initialized afterwards)
                                    isEmpty = true;
                                }
                            } else {
                                // File does not exist then it will be created by SQLite connection
                                isEmpty = true;
                            }
                            needsInit = isEmpty;
                        }

                        // Establish the connection (creates the file if it doesn't exist)
                        conn = DriverManager.getConnection(url);
                        System.out.println("Database connection established. URL: " + url);

                        // If DB was new or empty, initialize schema
                        if (needsInit) {
                            System.out.println("Database is new/empty. Initializing schema...");
                            DBSchema.createTables();
                            System.out.println("DBSchema.createTables() executed.");
                        }

                        // Register shutdown to close connection
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try {
                                closeConnection();
                                System.out.println("Database connection closed via Shutdown Hook.");
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

    // Closes the connection safely if open
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