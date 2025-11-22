package pt.isec.pd.server;

import pt.isec.pd.common.Message;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientHandler extends Thread {
    private final Socket clientSocket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private volatile String email;
    private volatile String name;
    private final AtomicBoolean authenticated = new AtomicBoolean(false);

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        setName("ClientHandler-" + socket.getRemoteSocketAddress());
    }

    @Override
    public void run() {
        try {
            // create output stream first to avoid stream header deadlock
            out = new ObjectOutputStream(clientSocket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(clientSocket.getInputStream());

            ClientManager.register(this);
            Object obj;
            while ((obj = in.readObject()) != null) {
                if (!(obj instanceof Message)) {
                    System.out.println("[Server] Received non-Message object from " + clientSocket.getRemoteSocketAddress());
                    send(new Message("ERROR", "Unsupported object received"));
                    continue;
                }
                Message msg = (Message) obj;
                // Debug log: show incoming message
                System.out.println("[Server] Received from " + clientSocket.getRemoteSocketAddress()
                        + " -> type=" + msg.getType() + " content=" + msg.getContent());
                handleMessage(msg);
            }
        } catch (EOFException eof) {
            // client closed connection normally
            System.out.println("[Server] Client disconnected: " + clientSocket.getRemoteSocketAddress());
        } catch (SocketException se) {
            System.err.println("[Server] Socket error in client handler: " + se.getMessage());
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[Server] I/O or serialization error in client handler: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleMessage(Message msg) throws IOException {
        String type = msg.getType();
        String content = Objects.toString(msg.getContent(), "");

        switch (type) {
            case "REGISTER_REQUEST":
                handleRegister(content);
                break;
            case "AUTH_REQUEST":
                handleAuth(content);
                break;
            case "LOGOUT_REQUEST":
                handleLogout();
                break;
            case "CLIENT_MESSAGE":
                handleClientMessage(content);
                break;
            default:
                // generic ACK for unknown messages
                send(new Message("ACK", "Unknown request type: " + type));
        }
    }

    private void handleRegister(String payload) throws IOException {
        String[] parts = payload.split("\\|", 3);
        String email = parts.length > 0 ? parts[0] : "";
        String password = parts.length > 1 ? parts[1] : "";
        String name = parts.length > 2 ? parts[2] : "";

        if (email.isEmpty() || password.isEmpty()) {
            send(new Message("REGISTER_FAILURE", "Email and password required"));
            return;
        }
        boolean ok = UsersRepository.register(email, password, name);
        if (ok) {
            System.out.println("[Server] Registered user: " + email);
            send(new Message("REGISTER_SUCCESS", email));
        } else {
            System.out.println("[Server] Registration failed (exists): " + email);
            send(new Message("REGISTER_FAILURE", "User already exists"));
        }
    }

    private void handleAuth(String payload) throws IOException {
        // accept "email|password" or "email:password"
        String[] parts = payload.contains("|") ? payload.split("\\|", 2) : payload.split(":", 2);
        if (parts.length < 2) {
            send(new Message("AUTH_FAILURE", "Invalid credentials format"));
            return;
        }
        String email = parts[0];
        String password = parts[1];
        String userName = UsersRepository.authenticate(email, password);
        if (userName != null) {
            this.email = email.toLowerCase();
            this.name = userName == null || userName.isEmpty() ? this.email : userName;
            authenticated.set(true);
            System.out.println("[Server] Authentication success for: " + this.email);
            send(new Message("AUTH_SUCCESS", this.name));
        } else {
            System.out.println("[Server] Authentication failure for: " + email);
            send(new Message("AUTH_FAILURE", "Invalid email or password"));
        }
    }

    private void handleLogout() throws IOException {
        if (authenticated.getAndSet(false)) {
            this.email = null;
            this.name = null;
            System.out.println("[Server] Client logged out: " + clientSocket.getRemoteSocketAddress());
            send(new Message("LOGOUT_SUCCESS", ""));
        } else {
            send(new Message("LOGOUT_FAILURE", "Not logged in"));
        }
    }

    private void handleClientMessage(String content) throws IOException {
        if (!authenticated.get()) {
            System.out.println("[Server] Rejected CLIENT_MESSAGE from unauthenticated client: " + clientSocket.getRemoteSocketAddress());
            send(new Message("ERROR", "Not authenticated"));
            return;
        }
        String sender = name != null ? name : email;
        System.out.println("[Server] Broadcasting message from " + sender + " -> " + content);
        // Broadcast to all connected clients
        ClientManager.broadcast(sender, content);
    }

    public synchronized void send(Message msg) throws IOException {
        if (out == null) throw new IOException("Output stream not initialized");
        try {
            out.writeObject(msg);
            out.flush();
        } catch (SocketException se) {
            throw se;
        }
    }

    public synchronized void closeSilently() throws IOException {
        // attempt to close resources without throwing upward except for logging
        try {
            if (in != null) in.close();
        } catch (IOException ignored) {}
        try {
            if (out != null) out.close();
        } catch (IOException ignored) {}
        if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
    }

    private void cleanup() {
        ClientManager.unregister(this);
        try {
            closeSilently();
        } catch (IOException ignored) {}
    }
}
