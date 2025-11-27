package pt.isec.pd.directoryservice;

import pt.isec.pd.common.Message;
import pt.isec.pd.sockets.Udp;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DirectoryService {
    private static final int DS_PORT = 9000;
    private static final List<String> serverList = Collections.synchronizedList(new ArrayList<>());
    private static volatile String principalServer = null;

    public static void main(String[] args) {
        System.out.println("Directory Service starting on UDP port: " + DS_PORT);

        try (Udp dsUdp = new Udp(DS_PORT)) {
            while (true) {
                System.out.println("\n--- Waiting for messages (Clients/Servers)...");
                if (principalServer != null) {
                    System.out.println("Current Principal Server: " + principalServer);
                    System.out.println("Number of Backup Servers: " + (serverList.size() - 1));
                } else {
                    System.out.println("No servers registered yet.");
                }

                Object receivedObject = dsUdp.receive();
                if (!(receivedObject instanceof Message message)) {
                    continue;
                }

                String clientAddress = dsUdp.getLastAddress().getHostAddress();
                int clientPort = dsUdp.getLastPort();
                String source = clientAddress + ":" + clientPort;

                System.out.println("Message received from " + source);
                System.out.println("Content: " + message);

                handleMessage(message, clientAddress, clientPort);
            }
        } catch (SocketException e) {
            System.err.println("Socket error in DS: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("I/O error in DS: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("Serialization error in DS: " + e.getMessage());
        }
    }

    private static void handleMessage(Message msg, String sourceAddress, int sourcePort) {
        switch (msg.getType()) {
            case "CLIENT_REQUEST":
                handleClientRequest(sourceAddress, sourcePort);
                break;
            case "SERVER_REGISTER":
                handleServerRegister(msg.getContent());
                break;
            // Heartbeat and failover logic




            default:
                System.out.println("Unknown message type: " + msg.getType());
                break;
        }
    }

    private static void handleClientRequest(String clientAddress, int clientPort) {
        System.out.println("Client request. Preparing response...");
        if (principalServer == null) {
            System.out.println("No principal server available to serve the client.");
            return;
        }

        Message responseMessage = new Message("DS_RESPONSE", principalServer);
        try (Udp dsResponseUdp = new Udp(clientAddress, clientPort)) {
            dsResponseUdp.send(responseMessage);
            System.out.println("Response sent: " + responseMessage.getContent());
        } catch (IOException e) {
            System.err.println("Error sending UDP response to client: " + e.getMessage());
        }
    }

    private static void handleServerRegister(String serverAddress) {
        synchronized (serverList) {
            if (!serverList.contains(serverAddress)) {
                serverList.add(serverAddress);
                System.out.println("Registered new server: " + serverAddress);
                if (principalServer == null) {
                    principalServer = serverAddress;
                    System.out.println("Promoted to PRINCIPAL: " + serverAddress);
                } else {
                    System.out.println("Added as BACKUP: " + serverAddress);
                }
            } else {
                System.out.println("Server already registered: " + serverAddress);
            }
        }
    }
}
