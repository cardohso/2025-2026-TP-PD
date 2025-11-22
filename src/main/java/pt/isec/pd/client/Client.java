package pt.isec.pd.client;

import pt.isec.pd.sockets.Udp;
import pt.isec.pd.sockets.Tcp;
import pt.isec.pd.common.Message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

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
                System.out.println("Response received. Principal Server: " + principalServer);
            } else {
                System.out.println("Unexpected response from DS. Exiting.");
                return;
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Failed UDP communication with DS. Exiting. (Reason: " + e.getMessage() + ")");
            return;
        }

        if (principalServer != null) {

            String[] tokens = principalServer.split(":", 2);
            if (tokens.length != 2) {
                System.err.println("Invalid principal server, expected format host:port");
                return;
            }

            String host = tokens[0];
            int port;
            try {
                port = Integer.parseInt(tokens[1]);
            } catch (NumberFormatException nfe) {
                System.err.println("Invalid port in principal server: " + tokens[1]);
                return;
            }

            System.out.println("Connecting to Principal Server at " + host + ":" + port);
            System.out.println("\n--- Establishing TCP connection to principal server: " + host + ":" + port);

            Tcp clientTcp = null;
            Thread listener = null;
            try {
                clientTcp = new Tcp(host, port);
                System.out.println("TCP connection established successfully!");

                // start background listener to print server messages
                Tcp finalClientTcp = clientTcp;
                listener = new Thread(() -> {
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            Object resp = finalClientTcp.receive();
                            if (resp instanceof Message) {
                                Message serverMsg = (Message) resp;
                                System.out.println("\n[Server] " + serverMsg);
                                System.out.print("> ");
                            } else {
                                System.out.println("\n[Server] Unexpected TCP response.");
                                System.out.print("> ");
                            }
                        }
                    } catch (IOException | ClassNotFoundException e) {
                        System.err.println("\nListener stopped: " + e.getMessage());
                    }
                }, "tcp-listener");
                listener.setDaemon(true);
                listener.start();

                // send initial auth
                clientTcp.send(new Message("AUTH_REQUEST", "user@isec.pt:password123"));
                System.out.println("Credentials sent to server.");

                // interactive console loop; type `exit` to close client
                try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
                    System.out.println("Enter messages to send or type `exit` to quit.");
                    String line;
                    System.out.print("> ");
                    while ((line = console.readLine()) != null) {
                        if ("exit".equalsIgnoreCase(line.trim())) {
                            System.out.println("Exiting by user request...");
                            break;
                        }
                        if (line.trim().isEmpty()) {
                            System.out.print("> ");
                            continue;
                        }
                        clientTcp.send(new Message("CLIENT_MESSAGE", line));
                        System.out.print("> ");
                    }
                }

            } catch (IOException e) {
                System.err.println("Error establishing or using TCP connection to principal server: " + e.getMessage());
            } finally {
                if (listener != null) {
                    listener.interrupt();
                }
                if (clientTcp != null) {
                    try {
                        clientTcp.close();
                    } catch (IOException e) {
                        System.err.println("Error closing TCP connection: " + e.getMessage());
                    }
                }
            }
        }

        System.out.println("\n Client closed.");
    }
}
