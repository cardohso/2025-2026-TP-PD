package pt.isec.pd.server;

import pt.isec.pd.common.Message;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

public class SendDataBaseCopy implements Runnable {
    private final Socket socket;
    private final String dbFilePath;

    public SendDataBaseCopy(Socket socket, String dbFilePath) {
        this.socket = socket;
        this.dbFilePath = dbFilePath;
    }

    @Override
    public void run() {
        try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            // Wait for request
            Object req = in.readObject();
            if (!(req instanceof Message) || !"DB_COPY_REQUEST".equals(((Message) req).getType())) {
                System.out.println("[SendDataBaseCopy] Unexpected request, closing.");
                return;
            }

            // Prepare DB file bytes
            Path p = Path.of(dbFilePath);
            if (!Files.exists(p)) {
                out.writeObject(new Message("DB_COPY_START", "0"));
                out.flush();
                System.out.println("[SendDataBaseCopy] DB file not found: " + dbFilePath);
                return;
            }

            byte[] data = Files.readAllBytes(p);
            long version = p.toFile().lastModified();

            // inform start and send bytes
            out.writeObject(new Message("DB_COPY_START", String.valueOf(version)));
            out.flush();

            out.writeObject(data);
            out.flush();

            System.out.println("[SendDataBaseCopy] Sent DB copy (" + data.length + " bytes) to " + socket.getRemoteSocketAddress());
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[SendDataBaseCopy] Error handling copy request: " + e.getMessage());
        } finally {
            try { if (!socket.isClosed()) socket.close(); } catch (IOException ignored) {}
        }
    }
}
