package pt.isec.pd.server;

import pt.isec.pd.common.Message;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class ClientHandler extends Thread {
    private final Socket clientSocket;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try (ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {

            Object obj;
            while ((obj = in.readObject()) != null) {
                if (obj instanceof Message) {
                    Message msg = (Message) obj;
                    System.out.println("Received from client: " + msg);

                    Message response = new Message("ACK", "ACK: " + msg.getType() + " -> " + msg.getContent());
                    try {
                        out.writeObject(response);
                        out.flush();
                    } catch (SocketException se) {
                        System.err.println("Client socket closed before response could be sent: " + se.getMessage());
                        break;
                    }
                } else {
                    System.out.println("Received unknown object: " + obj);
                }
            }
        } catch (EOFException eof) {
            // client closed connection normally
        } catch (SocketException se) {
            System.err.println("Socket error in client handler: " + se.getMessage());
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("I/O or serialization error in client handler: " + e.getMessage());
        } finally {
            try {
                if (clientSocket != null && !clientSocket.isClosed())
                    clientSocket.close();
            } catch (IOException ignored) {}
        }
    }
}
