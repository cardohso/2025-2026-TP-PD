package pt.isec.pd.server;

import pt.isec.pd.utils.DBSchema;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final int DEFAULT_PORT = 5000;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        // Ensure DB tables exist before accepting connections
        DBSchema.createTables();

        ExecutorService pool = Executors.newCachedThreadPool();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down server...");
            pool.shutdownNow();
        }));

        System.out.println("Server starting on port " + port);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted connection from " + clientSocket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(clientSocket);
                // Submit handler to thread pool. Original code used handler.start(); keep that behavior:
                pool.submit(() -> {
                    try {
                        handler.start();
                    } catch (NoSuchMethodError | AbstractMethodError e) {
                        // Fallback: if ClientHandler is Runnable (not Thread), run it directly
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
