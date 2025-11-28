// java
package pt.isec.pd.server;

import pt.isec.pd.utils.DBSchema;
import pt.isec.pd.utils.ConnectDB;
import pt.isec.pd.common.Message;
import pt.isec.pd.sockets.Udp;

import java.io.IOException;
import java.net.InetAddress;
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

        String directoryServiceIP = args[0];

        int directoryServiceUDPPort;
        try {
            directoryServiceUDPPort = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Error: Directory service UDP port must be an integer.");
            System.exit(1);
            return;
        }

        String dbDirectoryPath = args[2];

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

        try {
            ConnectDB.setDatabaseFile(dbDirectoryPath);
        } catch (IllegalStateException ise) {
            System.err.println("Database already opened and cannot be changed: " + ise.getMessage());
            return;
        }

        DBSchema.createTables();

        ExecutorService pool = Executors.newCachedThreadPool();

        System.out.println("Server starting on port " + serverPort);
        try (ServerSocket clientSocket = new ServerSocket(serverPort);
             ServerSocket serverCopySocket = new ServerSocket(0)) {

            int copyPort = serverCopySocket.getLocalPort();
            System.out.println("Client listen port: " + clientSocket.getLocalPort());
            System.out.println("Server-copy listen port (auto): " + copyPort);

            // register shutdown hook after copyPort is known so it can notify DS immediately
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down server... sending DEREGISTER to DS");
                try {
                    String host = InetAddress.getLocalHost().getHostAddress();
                    String serverAddr = host + ":" + copyPort;
                    try (Udp u = new Udp(directoryServiceIP, directoryServiceUDPPort)) {
                        u.send(new Message("SERVER_DEREGISTER", serverAddr));
                        System.out.println("DEREGISTER sent: " + serverAddr);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to send DEREGISTER: " + e.getMessage());
                } finally {
                    pool.shutdownNow();
                }
            }, "server-shutdown"));

            // start heartbeat sender
            Thread hb = new Thread(new HeartbeatSender(directoryServiceIP, directoryServiceUDPPort, clientSocket.getLocalPort(), copyPort, dbDirectoryPath), "heartbeat-sender");
            hb.setDaemon(true);
            hb.start();

            // accept server-copy connections in background
            pool.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Socket s = serverCopySocket.accept();
                        System.out.println("Accepted server-copy connection from " + s.getRemoteSocketAddress());
                        pool.submit(new SendDataBaseCopy(s, dbDirectoryPath));
                    } catch (IOException e) {
                        System.err.println("Error accepting server-copy connection: " + e.getMessage());
                        break;
                    }
                }
            });

            // accept clients (existing behavior)
            while (true) {
                Socket client = clientSocket.accept();
                System.out.println("Accepted connection from " + client.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(client);
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
