// src/main/java/pt/isec/pd/server/ClientHandler.java
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
    private volatile String role; // To know if user is DOCENTE or STUDENT
    private final AtomicBoolean authenticated = new AtomicBoolean(false);

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        setName("ClientHandler-" + socket.getRemoteSocketAddress());
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(clientSocket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(clientSocket.getInputStream());

            ClientManager.register(this);
            Object obj;
            while ((obj = in.readObject()) != null) {
                if (!(obj instanceof Message msg)) {
                    send(new Message("ERROR", "Unsupported object received"));
                    continue;
                }
                System.out.println("[Server] Received -> type=" + msg.getType() + " content=" + msg.getContent());
                handleMessage(msg);
            }
        } catch (EOFException eof) {
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
            case "UPDATE_PROFILE_REQUEST":
                handleUpdateProfile(content);
                break;
            case "CLIENT_MESSAGE":
                handleClientMessage(content);
                break;
            default:
                send(new Message("ACK", "Unknown request type: " + type));
        }
    }

    private void handleUpdateProfile(String payload) throws IOException {
        if (!authenticated.get()) {
            send(new Message("UPDATE_FAILURE", "Not authenticated"));
            return;
        }
        // payload: newName|newEmail|newPassword
        String[] parts = payload.split("\\|", 3);
        String newName = parts.length > 0 ? parts[0] : "";
        String newEmail = parts.length > 1 ? parts[1] : "";
        String newPassword = parts.length > 2 ? parts[2] : "";

        String result;
        if ("DOCENTE".equalsIgnoreCase(this.role)) {
            result = UsersRepository.updateTeacher(this.email, newName, newEmail, newPassword);
        } else {
            // Similar logic can be added for students
            send(new Message("UPDATE_FAILURE", "Profile update not supported for this role yet."));
            return;
        }

        if ("OK".equals(result)) {
            // If email was changed, update it in the handler state
            if (newEmail != null && !newEmail.isBlank()) {
                this.email = newEmail.trim().toLowerCase();
            }
            if (newName != null && !newName.isBlank()) {
                this.name = newName.trim();
            }
            send(new Message("UPDATE_SUCCESS", "Profile updated successfully."));
        } else {
            send(new Message("UPDATE_FAILURE", result));
        }
    }

    private void handleRegister(String payload) throws IOException {
        // ROLE|email|password|name|extra
        String[] parts = payload.split("\\|", 5);
        if (parts.length < 5) {
            send(new Message("REGISTER_FAILURE", "Invalid registration format. Expected: ROLE|email|password|name|extra"));
            return;
        }
        String role = parts[0].trim().toUpperCase();
        String email = parts[1];
        String password = parts[2];
        String name = parts[3];
        String extra = parts[4]; // studentNumber or registrationCode

        System.out.println("[Server Debug] Role: " + role + ", Email: " + email + ", Name: " + name + ", Extra: " + extra);

        if (email.isEmpty() || password.isEmpty()) {
            send(new Message("REGISTER_FAILURE", "Email and password are required."));
            return;
        }

        String result;
        if ("DOCENTE".equals(role)) {
            if (extra.isEmpty()) {
                send(new Message("REGISTER_FAILURE", "Registration code is required for Docente."));
                return;
            }
            result = UsersRepository.registerTeacher(email, password, name, extra);
        } else {
            if (extra.isEmpty()) {
                send(new Message("REGISTER_FAILURE", "Student number is required for Student."));
                return;
            }
            result = UsersRepository.registerStudent(email, password, name, extra);
        }

        System.out.println("[Server] Registration result for " + email + ": " + result);
        switch (result) {
            case "OK":
                System.out.println("[Server] Registered " + role + ": " + email);
                send(new Message("REGISTER_SUCCESS", "Registration successful for " + email));
                break;
            case "EMAIL_ALREADY_EXISTS":
                send(new Message("REGISTER_FAILURE", "This email is already registered."));
                break;
            case "STUDENT_NUMBER_ALREADY_EXISTS":
                send(new Message("REGISTER_FAILURE", "This student number is already registered."));
                break;
            case "INVALID_REGISTRATION_CODE":
                send(new Message("REGISTER_FAILURE", "The provided registration code is incorrect."));
                break;
            case "INVALID_INPUT":
                send(new Message("REGISTER_FAILURE", "Invalid input provided (e.g., missing fields)."));
                break;
            case "ERROR_INSERT":
                send(new Message("REGISTER_FAILURE", "An error occurred while saving the new user."));
                break;
            default:
                if (result != null && result.startsWith("SQL_ERROR")) {
                    send(new Message("REGISTER_FAILURE", "A database error occurred on the server."));
                } else {
                    send(new Message("REGISTER_FAILURE", "An unknown error occurred during registration. Details: " + result));
                }
                break;
        }
    }


    private void handleAuth(String payload) throws IOException {
        String[] parts = payload.contains("|") ? payload.split("\\|", 3) : payload.split(":", 2);
        String role;
        String email;
        String password;
        if (parts.length == 3) {
            role = parts[0].trim().toUpperCase();
            email = parts[1];
            password = parts[2];
        } else if (parts.length == 2) {
            role = "STUDENT";
            email = parts[0];
            password = parts[1];
        } else {
            send(new Message("AUTH_FAILURE", "Invalid credentials format"));
            return;
        }

        String userName = UsersRepository.authenticate(role, email, password);
        if (userName != null) {
            this.email = email.toLowerCase();
            this.name = userName.isEmpty() ? this.email : userName;
            this.role = role;
            authenticated.set(true);
            System.out.println("[Server] Authentication success for: " + this.email + " as " + role);
            send(new Message("AUTH_SUCCESS", this.name));
        } else {
            System.out.println("[Server] Authentication failure for: " + email + " as " + role);
            send(new Message("AUTH_FAILURE", "Invalid email or password"));
        }
    }

    private void handleLogout() throws IOException {
        if (authenticated.getAndSet(false)) {
            this.email = null;
            this.name = null;
            this.role = null;
            System.out.println("[Server] Client logged out: " + clientSocket.getRemoteSocketAddress());
            send(new Message("LOGOUT_SUCCESS", ""));
        } else {
            send(new Message("LOGOUT_FAILURE", "Not logged in"));
        }
    }

    private void handleClientMessage(String content) throws IOException {
        if (!authenticated.get()) {
            send(new Message("ERROR", "Not authenticated"));
            return;
        }
        String sender = name != null ? name : email;
        ClientManager.broadcast(sender, content);
    }

    public synchronized void send(Message msg) throws IOException {
        if (out == null) throw new IOException("Output stream not initialized");
        out.writeObject(msg);
        out.flush();
    }

    public synchronized void closeSilently() throws IOException {
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
    }

    private void cleanup() {
        ClientManager.unregister(this);
        try { closeSilently(); } catch (IOException ignored) {}
    }
}
