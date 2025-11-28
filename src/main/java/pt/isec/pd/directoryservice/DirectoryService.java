// java
package pt.isec.pd.directoryservice;

import pt.isec.pd.common.Message;
import pt.isec.pd.sockets.Udp;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class DirectoryService {
    private static final int DS_PORT = 9000;
    private static final List<String> serverList = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicReference<String> principalServer = new AtomicReference<>(null);
    // track last heartbeat time (ms) per serverAddress
    private static final ConcurrentHashMap<String, Long> lastSeen = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("Directory Service starting on UDP port: " + DS_PORT);

        // start background thread to remove stale servers
        Thread t = new Thread(new TimeCheckThread(serverList, lastSeen, principalServer), "ds-timecheck");
        t.setDaemon(true);
        t.start();

        try (Udp dsUdp = new Udp(DS_PORT)) {
            while (true) {
                System.out.println("\n--- Waiting for messages (Clients/Servers)...");
                String curr = principalServer.get();
                if (curr != null) {
                    System.out.println("Current Principal Server: " + curr);
                    int backupCount = serverList.size() > 0 ? serverList.size() - 1 : 0;
                    System.out.println("Number of Backup Servers: " + backupCount);
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
                handleServerRegister(msg.getContent(), sourceAddress, sourcePort);
                break;
            case "HEARTBEAT":
                handleHeartbeat(msg.getContent(), sourceAddress, sourcePort);
                break;
            case "SERVER_DEREGISTER":
                handleServerDeregister(msg.getContent(), sourceAddress, sourcePort);
                break;
            default:
                System.out.println("Unknown message type: " + msg.getType());
                break;
        }
    }

    private static void handleClientRequest(String clientAddress, int clientPort) {
        System.out.println("Client request. Preparing response...");
        String curr = principalServer.get();
        if (curr == null) {
            System.out.println("No principal server available to serve the client.");
            // Optionally send a "SERVER_UNAVAILABLE" message
            return;
        }

        Message responseMessage = new Message("DS_RESPONSE", curr);
        try (Udp dsResponseUdp = new Udp(clientAddress, clientPort)) {
            dsResponseUdp.send(responseMessage);
            System.out.println("Response sent to client " + clientAddress + ":" + clientPort + ": " + responseMessage.getContent());
        } catch (IOException e) {
            System.err.println("Error sending UDP response to client: " + e.getMessage());
        }
    }

    private static void handleServerRegister(String serverAddress, String sourceAddress, int sourcePort) {
        Message response;
        synchronized (serverList) {
            if (!serverList.contains(serverAddress)) {
                serverList.add(serverAddress);
                lastSeen.put(serverAddress, System.currentTimeMillis());
                System.out.println("Registered new server: " + serverAddress);
                if (principalServer.get() == null) {
                    principalServer.set(serverAddress);
                    System.out.println("Promoted to PRINCIPAL: " + serverAddress);
                    response = new Message("PROMOTED_TO_PRINCIPAL", "You are the principal server.");
                    notifyBackupsOfNewPrincipal(serverAddress);
                } else {
                    System.out.println("Added as BACKUP: " + serverAddress);
                    response = new Message("REGISTERED_AS_BACKUP", principalServer.get());
                }
            } else {
                // update last seen
                lastSeen.put(serverAddress, System.currentTimeMillis());
                System.out.println("Server already registered (refresh): " + serverAddress);
                if (serverAddress.equals(principalServer.get())) {
                    response = new Message("PROMOTED_TO_PRINCIPAL", "You are the principal server.");
                } else {
                    response = new Message("REGISTERED_AS_BACKUP", principalServer.get());
                }
            }
        }

        try (Udp dsResponseUdp = new Udp(sourceAddress, sourcePort)) {
            dsResponseUdp.send(response);
            System.out.println("Sent registration confirmation to " + serverAddress + ": " + response.getType());
        } catch (IOException e) {
            System.err.println("Error sending UDP response to server " + serverAddress + ": " + e.getMessage());
        }
    }

    private static void handleHeartbeat(String content, String sourceAddress, int sourcePort) {
        // heartbeat content expected: version|clientPort|copyPort
        String[] parts = content.split("\\|", 3);
        String copyPortStr = parts.length >= 3 ? parts[2] : "";
        int copyPort = -1;
        try { copyPort = Integer.parseInt(copyPortStr); } catch (NumberFormatException ignored) {}

        if (copyPort <= 0) {
            System.out.println("Invalid heartbeat copy port from " + sourceAddress);
            // still reply with current principal if any ...
        }

        String serverAddress = sourceAddress + ":" + (copyPort > 0 ? copyPort : sourcePort);

        Message response;
        synchronized (serverList) {
            lastSeen.put(serverAddress, System.currentTimeMillis());
            if (!serverList.contains(serverAddress)) {
                serverList.add(serverAddress);
                System.out.println("Heartbeat caused registration of new server: " + serverAddress);
                if (principalServer.get() == null) {
                    principalServer.set(serverAddress);
                    System.out.println("Promoted to PRINCIPAL via heartbeat: " + serverAddress);
                    response = new Message("PROMOTED_TO_PRINCIPAL", "You are the principal server.");
                    notifyBackupsOfNewPrincipal(serverAddress);
                } else {
                    System.out.println("Added as BACKUP via heartbeat: " + serverAddress);
                    response = new Message("REGISTERED_AS_BACKUP", principalServer.get());
                }
            } else {
                // already known - reply with current principal
                if (serverAddress.equals(principalServer.get())) {
                    response = new Message("PROMOTED_TO_PRINCIPAL", "You are the principal server.");
                } else if (principalServer.get() != null) {
                    response = new Message("REGISTERED_AS_BACKUP", principalServer.get());
                } else {
                    response = new Message("NO_PRINCIPAL", "");
                }
            }
        }

        // Also send principal address as convenience
        Message dsResponse = new Message("DS_RESPONSE", principalServer.get() == null ? "" : principalServer.get());

        try (Udp dsResponseUdp = new Udp(sourceAddress, sourcePort)) {
            dsResponseUdp.send(dsResponse);
            System.out.println("Sent heartbeat reply to " + serverAddress + ": " + dsResponse.getContent());
        } catch (IOException e) {
            System.err.println("Error sending heartbeat reply to server " + serverAddress + ": " + e.getMessage());
        }
    }

    private static void handleServerDeregister(String serverAddress, String sourceAddress, int sourcePort) {
        synchronized (serverList) {
            if (!serverList.contains(serverAddress)) {
                System.out.println("Deregister request for unknown server: " + serverAddress);
            } else {
                serverList.remove(serverAddress);
                lastSeen.remove(serverAddress);
                System.out.println("Server deregistered: " + serverAddress);
                // if it was the principal, promote another
                String curr = principalServer.get();
                if (serverAddress.equals(curr)) {
                    String newPrincipal = serverList.isEmpty() ? null : serverList.get(0);
                    principalServer.set(newPrincipal);
                    System.out.println("Principal changed due to deregister: " + newPrincipal);
                    if (newPrincipal != null) {
                        notifyBackupsOfNewPrincipal(newPrincipal);
                    } else {
                        System.out.println("No principal available after deregister.");
                    }
                }
            }
        }

        // confirm deregistration to sender
        Message resp = new Message("DEREGISTERED", "OK");
        try (Udp dsResponseUdp = new Udp(sourceAddress, sourcePort)) {
            dsResponseUdp.send(resp);
        } catch (IOException e) {
            System.err.println("Error sending deregister confirmation to " + serverAddress + ": " + e.getMessage());
        }
    }

    public static void notifyBackupsOfNewPrincipal(String newPrincipal) {
        List<String> backups;
        synchronized (serverList) {
            backups = new ArrayList<>(serverList);
            backups.remove(newPrincipal);
        }

        if (backups.isEmpty()) {
            System.out.println("No backup servers to notify.");
            return;
        }

        System.out.println("Notifying " + backups.size() + " backup server(s) of new principal: " + newPrincipal);
        Message updateMsg = new Message("UPDATE_PRINCIPAL", newPrincipal);

        for (String backupAddress : backups) {
            try {
                String[] parts = backupAddress.split(":");
                String host = parts[0];
                int port = Integer.parseInt(parts[1]);
                try (Udp udp = new Udp(host, port)) {
                    udp.send(updateMsg);
                }
            } catch (IOException | NumberFormatException e) {
                System.err.println("Failed to notify backup server " + backupAddress + ": " + e.getMessage());
            }
        }
    }

}
