package pt.isec.pd.server;

import java.net.*;
import java.io.*;

public class ClientHandler extends Thread {
    private final Socket clientSocket;
    private final InetAddress directoryAddress;
    private final int directoryPort;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        this.directoryAddress = socket.getInetAddress();
        this.directoryPort = socket.getPort();
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String request;
            while ((request = in.readLine()) != null) {
                String response = processRequest(request);
                out.println(response);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (clientSocket != null && !clientSocket.isClosed())
                    clientSocket.close();
            } catch (IOException ignored) {}
        }
    }

    // temp
    private String processRequest(String request) {
        return "ACK: " + request;
    }
}