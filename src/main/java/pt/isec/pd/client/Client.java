package pt.isec.pd.client;

import pt.isec.pd.sockets.Udp;
import pt.isec.pd.sockets.Tcp;
import pt.isec.pd.common.Message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java pt.isec.pd.client.Client <DirectoryServiceIP> <DirectoryServicePort>");
            System.exit(1);
        }

        String dsAddress = args[0];
        int dsPort;
        try {
            dsPort = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Error: Directory service port must be an integer.");
            System.exit(1);
            return;
        }

        System.out.println("Client starting...");
        System.out.println("Attempting to contact Directory Service at " + dsAddress + ":" + dsPort);
        String principalServer = null;

        try (Udp clientUdp = new Udp(dsAddress, dsPort)) {
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

        if (principalServer == null) return;

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
        AtomicBoolean loggedIn = new AtomicBoolean(false);

        try {
            clientTcp = new Tcp(host, port);
            System.out.println("TCP connection established successfully!");

            Tcp finalClientTcp = clientTcp;
            listener = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Object resp = finalClientTcp.receive();
                        if (resp instanceof Message) {
                            Message serverMsg = (Message) resp;
                            System.out.println("\n[Server] " + serverMsg);
                            // mark as logged in when server sends AUTH_SUCCESS
                            if ("AUTH_SUCCESS".equals(serverMsg.getType())) {
                                loggedIn.set(true);
                            } else if ("LOGOUT_SUCCESS".equals(serverMsg.getType())) {
                                loggedIn.set(false);
                            }
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

            try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
                // initial menu loop: Register / Login / Exit
                while (true) {
                    while (!loggedIn.get()) {
                        System.out.println("\n--- Initial Menu ---");
                        System.out.println("1) Register");
                        System.out.println("2) Login");
                        System.out.println("3) Exit");
                        System.out.print("Choose an option: ");
                        String choice = console.readLine();
                        if (choice == null) {
                            break;
                        }
                        choice = choice.trim();
                        if ("1".equals(choice)) {
                            System.out.println("Register as: 1) Student  2) Docente");
                            System.out.print("Choose role: ");
                            String roleChoice = console.readLine();
                            String role = "STUDENT";
                            if ("2".equals(roleChoice)) role = "DOCENTE";

                            System.out.print("Name: ");
                            String name = console.readLine();
                            System.out.print("Email: ");
                            String email = console.readLine();
                            System.out.print("Password: ");
                            String password = console.readLine();
                            String extra = "";
                            if ("STUDENT".equals(role)) {
                                System.out.print("Student Number: ");
                                extra = console.readLine();
                            } else {
                                System.out.print("Registration Code: ");
                                extra = console.readLine();
                            }
                            if (email == null || password == null) continue;
                            String payload = role + "|" + email + "|" + password + "|" + (name == null ? "" : name) + "|" + (extra == null ? "" : extra);
                            clientTcp.send(new Message("REGISTER_REQUEST", payload));
                            System.out.println("Registration request sent. Waiting for server response...");
                        } else if ("2".equals(choice)) {
                            // choose role
                            System.out.println("Login as: 1) Student  2) Docente");
                            System.out.print("Choose role: ");
                            String roleChoice = console.readLine();
                            String role = "STUDENT";
                            if ("2".equals(roleChoice)) role = "DOCENTE";

                            System.out.print("Email: ");
                            String email = console.readLine();
                            System.out.print("Password: ");
                            String password = console.readLine();
                            if (email == null || password == null) continue;
                            String payload = role + "|" + email + "|" + password;
                            clientTcp.send(new Message("AUTH_REQUEST", payload));
                            System.out.println("Login request sent. Waiting for server response...");
                            // wait short time for AUTH_SUCCESS
                            long start = System.currentTimeMillis();
                            while (!loggedIn.get() && System.currentTimeMillis() - start < 10000) {
                                Thread.sleep(100);
                            }
                            if (!loggedIn.get()) {
                                System.out.println("No successful login detected yet. Check server response.");
                            }
                        } else if ("3".equals(choice) || "exit".equalsIgnoreCase(choice) || "logout".equalsIgnoreCase(choice)) {
                            System.out.println("Exiting by user request...");
                            return;
                        } else {
                            System.out.println("Invalid option.");
                        }
                    }

                    // logged in -> interactive loop
                    if (loggedIn.get()) {
                        System.out.println("\nEnter messages to send or type `logout` to return to menu, `exit` to quit.");
                        String line;
                        System.out.print("> ");
                        while ((line = console.readLine()) != null) {
                            if ("exit".equalsIgnoreCase(line.trim())) {
                                System.out.println("Exiting by user request...");
                                return;
                            }
                            if ("logout".equalsIgnoreCase(line.trim())) {
                                clientTcp.send(new Message("LOGOUT_REQUEST", ""));
                                // listener will set loggedIn = false on LOGOUT_SUCCESS
                                System.out.println("Logout requested. Returning to initial menu...");
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
                }
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("Error: " + e.getMessage());
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

        System.out.println("\nClient closed.");
    }
}
