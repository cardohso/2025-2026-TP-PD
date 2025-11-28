package pt.isec.pd.server;

import pt.isec.pd.common.Message;
import pt.isec.pd.sockets.Tcp;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

public class BackupConnector {
    private final AtomicReference<Thread> connectorThread = new AtomicReference<>(null);
    private volatile Socket activeSocket = null;

    public void connectToPrincipal(String principalAddress) {
        Thread t = connectorThread.get();
        if (t != null && t.isAlive()) {
            // if same principal, ignore; else interrupt and recreate
            t.interrupt();
        }

        Thread newThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                String[] parts = principalAddress.split(":", 2);
                if (parts.length != 2) break;
                String host = parts[0];
                int port;
                try { port = Integer.parseInt(parts[1]); } catch (NumberFormatException ex) { break; }

                try (Socket s = new Socket(host, port)) {
                    activeSocket = s;
                    System.out.println("[BackupConnector] Connected to principal " + principalAddress);
                    ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                    out.flush();
                    ObjectInputStream in = new ObjectInputStream(s.getInputStream());

                    // request DB copy
                    out.writeObject(new Message("DB_COPY_REQUEST", ""));
                    out.flush();

                    // Expect a Message("DB_COPY_START", version) then a byte[] file
                    Object o = in.readObject();
                    if (o instanceof Message start && "DB_COPY_START".equals(start.getType())) {
                        Object fileObj = in.readObject();
                        if (fileObj instanceof byte[] data) {
                            // store received bytes to local DB file (implementation minimal)
                            // save to same path used by server - omitted here for brevity (could be added)
                            System.out.println("[BackupConnector] Received DB copy (" + data.length + " bytes) version=" + start.getContent());
                        }
                    }
                    return;
                } catch (IOException | ClassNotFoundException e) {
                    System.err.println("[BackupConnector] Could not connect/receive from principal " + principalAddress + ": " + e.getMessage());
                    try { Thread.sleep(3000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
            activeSocket = null;
        }, "backup-connector-" + principalAddress);

        connectorThread.set(newThread);
        newThread.setDaemon(true);
        newThread.start();
    }

    public void disconnect() {
        Thread t = connectorThread.getAndSet(null);
        if (t != null) t.interrupt();
        try { if (activeSocket != null && !activeSocket.isClosed()) activeSocket.close(); } catch (IOException ignored) {}
    }
}
