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
    // Map<CopyAddress, ClientAddress>
    private static final ConcurrentHashMap<String, String> serverMap = new ConcurrentHashMap<>();
    private static final AtomicReference<String> principalServerCopyAddr = new AtomicReference<>(null);
    // track last heartbeat time (ms) per server copyAddress
    private static final ConcurrentHashMap<String, Long> lastSeen = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("Directory Service starting on UDP port: " + DS_PORT);

        // start background thread to remove stale servers
        Thread t = new Thread(new TimeCheckThread(serverMap, lastSeen, principalServerCopyAddr), "ds-timecheck");
        t.setDaemon(true);
        t.start();

        try (Udp dsUdp = new Udp(DS_PORT)) {
            while (true) {
                System.out.println("\n--- Waiting for messages (Clients/Servers)...");
                String curr = principalServerCopyAddr.get();
                if (curr != null) {
                    System.out.println("Current Principal Server (Copy Addr): " + curr);
                    System.out.println("Current Principal Server (Client Addr): " + serverMap.get(curr));
                    int backupCount = serverMap.size() - 1;
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
                handleServerDeregister(msg.getContent());
                break;
            default:
                System.out.println("Unknown message type: " + msg.getType());
                break;
        }
    }

    private static void handleClientRequest(String clientAddress, int clientPort) {
        System.out.println("Client request. Preparing response...");
        String principalCopyAddr = principalServerCopyAddr.get();
        if (principalCopyAddr == null) {
            System.out.println("No principal server available to serve the client.");
            // Optionally send a "SERVER_UNAVAILABLE" message
            return;
        }

        String principalClientAddr = serverMap.get(principalCopyAddr);
        if (principalClientAddr == null) {
            System.out.println("Principal server address mapping is inconsistent. No address for client.");
            return;
        }

        Message responseMessage = new Message("DS_RESPONSE", principalClientAddr);
        try (Udp dsResponseUdp = new Udp(clientAddress, clientPort)) {
            dsResponseUdp.send(responseMessage);
            System.out.println("Response sent to client " + clientAddress + ":" + clientPort + ": " + responseMessage.getContent());
        } catch (IOException e) {
            System.err.println("Error sending UDP response to client: " + e.getMessage());
        }
    }

    private static void handleServerRegister(String content, String sourceAddress, int sourcePort) {
        // content: clientAddress|copyAddress
        String[] addresses = content.split("\\|");
        if (addresses.length != 2) {
            System.err.println("Invalid SERVER_REGISTER format: " + content);
            return;
        }
        String clientAddr = addresses[0];
        String copyAddr = addresses[1];

        synchronized (serverMap) {
            if (!serverMap.containsKey(copyAddr)) {
                serverMap.put(copyAddr, clientAddr);
                lastSeen.put(copyAddr, System.currentTimeMillis());
                System.out.println("Registered new server. Client: " + clientAddr + ", Copy: " + copyAddr);

                if (principalServerCopyAddr.get() == null) {
                    principalServerCopyAddr.set(copyAddr);
                    System.out.println("Promoted to PRINCIPAL: " + copyAddr);
                    notifyBackupsOfNewPrincipal(copyAddr);
                }
            } else {
                // Refresh registration
                lastSeen.put(copyAddr, System.currentTimeMillis());
                serverMap.put(copyAddr, clientAddr); // Update client address in case it changed
                System.out.println("Server re-registered (refresh): " + copyAddr);
            }
        }
    }

    private static void handleHeartbeat(String content, String sourceAddress, int sourcePort) {
        // heartbeat content expected: version|clientPort|copyPort
        String[] parts = content.split("\\|", 3);
        if (parts.length < 3) {
            System.err.println("Invalid HEARTBEAT format from " + sourceAddress + ":" + sourcePort);
            return;
        }
        String clientPort = parts[1];
        String copyPort = parts[2];
        String clientAddr = sourceAddress + ":" + clientPort;
        String copyAddr = sourceAddress + ":" + copyPort;

        synchronized (serverMap) {
            lastSeen.put(copyAddr, System.currentTimeMillis());
            if (!serverMap.containsKey(copyAddr)) {
                System.out.println("Heartbeat from unknown server, registering it: " + copyAddr);
                handleServerRegister(clientAddr + "|" + copyAddr, sourceAddress, sourcePort);
            }
        }

        // Reply to server with current principal's copy address
        String principalCopy = principalServerCopyAddr.get();
        Message dsResponse = new Message("DS_RESPONSE", principalCopy == null ? "" : principalCopy);

        try (Udp dsResponseUdp = new Udp(sourceAddress, sourcePort)) {
            dsResponseUdp.send(dsResponse);
        } catch (IOException e) {
            System.err.println("Error sending heartbeat reply to server " + copyAddr + ": " + e.getMessage());
        }
    }

    private static void handleServerDeregister(String copyAddr) {
        synchronized (serverMap) {
            if (serverMap.remove(copyAddr) == null) {
                System.out.println("Deregister request for unknown server: " + copyAddr);
                return;
            }

            lastSeen.remove(copyAddr);
            System.out.println("Server deregistered: " + copyAddr);

            String currentPrincipal = principalServerCopyAddr.get();
            if (copyAddr.equals(currentPrincipal)) {
                // Promote a new principal if available
                String newPrincipal = serverMap.keys().asIterator().hasNext() ? serverMap.keys().asIterator().next() : null;
                principalServerCopyAddr.set(newPrincipal);
                if (newPrincipal != null) {
                    System.out.println("Principal changed due to deregister. New principal: " + newPrincipal);
                    notifyBackupsOfNewPrincipal(newPrincipal);
                } else {
                    System.out.println("No principal available after deregister.");
                }
            }
        }
    }

    public static void notifyBackupsOfNewPrincipal(String newPrincipalCopyAddr) {
        List<String> backupCopyAddrs = new ArrayList<>(serverMap.keySet());
        backupCopyAddrs.remove(newPrincipalCopyAddr);

        if (backupCopyAddrs.isEmpty()) {
            System.out.println("No backup servers to notify.");
            return;
        }

        System.out.println("Notifying " + backupCopyAddrs.size() + " backup server(s) of new principal: " + newPrincipalCopyAddr);
        Message updateMsg = new Message("UPDATE_PRINCIPAL", newPrincipalCopyAddr);

        for (String backupCopyAddr : backupCopyAddrs) {
            try {
                String[] parts = backupCopyAddr.split(":");
                String host = parts[0];
                int port = Integer.parseInt(parts[1]);
                try (Udp udp = new Udp(host, port)) {
                    udp.send(updateMsg);
                }
            } catch (IOException | NumberFormatException e) {
                System.err.println("Failed to notify backup server " + backupCopyAddr + ": " + e.getMessage());
            }
        }
    }

    static class TimeCheckThread implements Runnable {
        private final ConcurrentHashMap<String, String> serverMap;
        private final ConcurrentHashMap<String, Long> lastSeen;
        private final AtomicReference<String> principalServerCopyAddr;
        private static final long STALE_THRESHOLD = 15000; // 15 seconds

        public TimeCheckThread(ConcurrentHashMap<String, String> serverMap, ConcurrentHashMap<String, Long> lastSeen, AtomicReference<String> principalServerCopyAddr) {
            this.serverMap = serverMap;
            this.lastSeen = lastSeen;
            this.principalServerCopyAddr = principalServerCopyAddr;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(STALE_THRESHOLD / 2);
                    long now = System.currentTimeMillis();
                    lastSeen.forEach((copyAddr, time) -> {
                        if (now - time > STALE_THRESHOLD) {
                            System.out.println("Server " + copyAddr + " is stale. Removing.");
                            handleServerDeregister(copyAddr);
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
