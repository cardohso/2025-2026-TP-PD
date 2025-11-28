package pt.isec.pd.server;

import pt.isec.pd.common.Message;
import pt.isec.pd.sockets.Udp;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class HeartbeatSender implements Runnable {
    private final String dsIp;
    private final int dsPort;
    private final int clientPort;
    private final int copyPort;
    private final String dbFilePath;
    private final AtomicReference<String> currentPrincipal = new AtomicReference<>(null);
    private final BackupConnector backupConnector;

    public HeartbeatSender(String dsIp, int dsPort, int clientPort, int copyPort, String dbFilePath) {
        this.dsIp = dsIp;
        this.dsPort = dsPort;
        this.clientPort = clientPort;
        this.copyPort = copyPort;
        this.dbFilePath = dbFilePath;
        this.backupConnector = new BackupConnector();
    }

    private String computeDbVersion() {
        try {
            File f = new File(dbFilePath);
            if (f.exists()) return String.valueOf(f.lastModified());
        } catch (Exception ignored) {}
        return "0";
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            String version = computeDbVersion();
            String payload = version + "|" + clientPort + "|" + copyPort;
            Message hb = new Message("HEARTBEAT", payload);

            // send multicast heartbeat
            try (Udp mcast = new Udp("230.30.30.30", 3030)) {
                mcast.send(hb);
            } catch (IOException e) {
                System.err.println("Failed sending multicast heartbeat: " + e.getMessage());
            }

            // send heartbeat to Directory Service and wait short reply
            try (Udp dsUdp = new Udp(dsIp, dsPort)) {
                dsUdp.setSoTimeout(2000);
                dsUdp.send(hb);
                try {
                    Object resp = dsUdp.receive();
                    if (resp instanceof Message) {
                        Message m = (Message) resp;
                        String principal = m.getContent();
                        String prev = currentPrincipal.getAndSet(principal);
                        if (principal != null && !principal.isBlank() && !principal.equals(prev) && !principal.endsWith(":" + copyPort)) {
                            // connect to new principal if I'm a backup
                            backupConnector.connectToPrincipal(principal);
                        } else if ((principal == null || principal.isBlank()) && prev != null) {
                            backupConnector.disconnect();
                        }
                    }
                } catch (IOException | ClassNotFoundException ex) {
                    // no response or error - ignore, will retry next heartbeat
                }
            } catch (IOException e) {
                System.err.println("Failed sending heartbeat to DS: " + e.getMessage());
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        backupConnector.disconnect();
    }
}
