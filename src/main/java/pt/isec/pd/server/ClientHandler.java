package pt.isec.pd.server;

import pt.isec.pd.common.Message;

import java.io.*;
import java.net.Socket;

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

                    // Simple ACK response
                    Message response = new Message("ACK", "ACK: " + msg.getType() + " -> " + msg.getContent());
                    out.writeObject(response);
                    out.flush();
                } else {
                    System.out.println("Received unknown object: " + obj);
                }
            }
        } catch (EOFException eof) {
            // client closed connection
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                if (clientSocket != null && !clientSocket.isClosed())
                    clientSocket.close();
            } catch (IOException ignored) {}
        }
    }
}
