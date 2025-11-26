package pt.isec.pd.server;

import pt.isec.pd.utils.DBSchema;
import pt.isec.pd.utils.ConnectDB;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final int DEFAULT_PORT = 5000;

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java pt.isec.pd.server.Server <DirectoryServiceIP> <DirectoryServiceUDPPort> <DBDirectoryPath> [TCPServerPort]");
            System.exit(1);
        }

        // Directory service address and UDP port
        String directoryServiceIP = args[0];

        int directoryServiceUDPPort;
        try {
            directoryServiceUDPPort = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Error: Directory service UDP port must be an integer.");
            System.exit(1);
            return;
        }

        // Directory Path
        String dbDirectoryPath = args[2];

        // TCP server port
        int serverPort = DEFAULT_PORT;
        if (args.length > 3) {
            try {
                serverPort = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {
                System.err.println("Warning: Invalid TCP server port. Using default: " + DEFAULT_PORT);
                serverPort = DEFAULT_PORT;
            }
        }

        System.out.println("Server Args:");
        System.out.println("  Directory Service IP: " + directoryServiceIP);
        System.out.println("  UDP Directory Port: " + directoryServiceUDPPort);
        System.out.println("  DB Path: " + dbDirectoryPath);
        System.out.println("  TCP Server Port: " + serverPort);
        System.out.println("------------------------------------");

        // Configure DB file before creating tables
        try {
            ConnectDB.setDatabaseFile(dbDirectoryPath);
        } catch (IllegalStateException ise) {
            System.err.println("Database already opened and cannot be changed: " + ise.getMessage());
            return;
        }

        // Ensure DB tables exist before accepting connections
        DBSchema.createTables();

        ExecutorService pool = Executors.newCachedThreadPool();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down server...");
            pool.shutdownNow();
        }));

        System.out.println("Server starting on port " + serverPort);
        try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted connection from " + clientSocket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(clientSocket);
                // Submit handler to thread pool; keep existing behavior of calling start();
                // Allows multiple clients simultaneously
                pool.submit(() -> {
                    try {
                        handler.start();
                    } catch (NoSuchMethodError | AbstractMethodError e) {
                        if (handler instanceof Runnable) {
                            ((Runnable) handler).run();
                        } else {
                            System.err.println("ClientHandler cannot be executed: " + e.getMessage());
                        }
                    }
                });
            }
        } catch (IOException e) {
            System.err.println("Server I/O error: " + e.getMessage());
        }
    }
}
