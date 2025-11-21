package pt.isec.pd.client;

import pt.isec.pd.sockets.Udp;
import pt.isec.pd.sockets.Tcp;
import pt.isec.pd.common.Message;

import java.io.IOException;

public class Client {
    private static final String DS_ADDRESS = "127.0.0.1";
    private static final int DS_PORT = 9000;

    public static void main(String[] args) {
        System.out.println("Client starting...");
        String principalServer = null;

        try (Udp clientUdp = new Udp(DS_ADDRESS, DS_PORT)) {
            Message requestMessage = new Message("CLIENT_REQUEST", "GET_PRINCIPAL_SERVER");
            clientUdp.send(requestMessage);
            System.out.println("UDP request sent to DS.");

            clientUdp.setSoTimeout(5000);
            System.out.println("\n--- Waiting for response from DS...");
            Object receivedObject = clientUdp.receive();
            Message responseMessage = (Message) receivedObject;

            if ("DS_RESPONSE".equals(responseMessage.getType())) {
                principalServer = responseMessage.getContent();
                System.out.println("Response received. Principal Server: **" + principalServer + "**");
            } else {
                System.out.println("Unexpected response from DS. Exiting.");
                return;
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Failed UDP communication with DS. Exiting. (Reason: " + e.getMessage() + ")");
            return;
        }

        if (principalServer != null) {
            String[] parts = principalServer.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            System.out.println("\n--- Establishing TCP connection to principal server: " + ip + ":" + port);

            // Keep the TCP connection open and wait for server response before closing
            try (Tcp clientTcp = new Tcp(ip, port)) {
                System.out.println("TCP connection established successfully!");

                clientTcp.send(new Message("AUTH_REQUEST", "user@isec.pt:password123"));
                System.out.println("Credentials sent to server.");

                // Wait for server (avoid closing immediately)
                try {
                    Object resp = clientTcp.receive(); // may throw IOException or ClassNotFoundException
                    if (resp instanceof Message) {
                        Message serverMsg = (Message) resp;
                        System.out.println("Server response: " + serverMsg);
                    } else {
                        System.out.println("Unexpected TCP response.");
                    }
                } catch (ClassNotFoundException | IOException e) {
                    System.err.println("Error receiving TCP response: " + e.getMessage());
                }

            } catch (IOException e) {
                System.err.println("Error establishing or using TCP connection to principal server: " + e.getMessage());
            }
        }

        System.out.println("\n Client closed.");
    }
}
