package pt.isec.pd.directoryservice;

import pt.isec.pd.sockets.Udp; // Import the Udp socket
import pt.isec.pd.common.Message; // Import the message model
import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;

public class DirectoryService {
    private static final int DS_PORT = 9000;

    public static void main(String[] args) {
        System.out.println("Directory Service starting on UDP port: " + DS_PORT);

        try (Udp dsUdp = new Udp(DS_PORT)) {

            while (true) {
                System.out.println("\n--- Waiting for messages (Clients/Servers)...");

                Object receivedObject = dsUdp.receive();
                Message clientMessage = (Message) receivedObject;

                String clientAddress = dsUdp.getLastAddress().getHostAddress();
                int clientPort = dsUdp.getLastPort();

                System.out.println("Message received from " + clientAddress + ":" + clientPort);
                System.out.println("Content: " + clientMessage);

                if ("CLIENT_REQUEST".equals(clientMessage.getType())) {
                    System.out.println("  -> Client request. Preparing response...");

                    String principalServerAddress = "127.0.0.1:5000";
                    Message responseMessage = new Message("DS_RESPONSE", principalServerAddress);

                    try (Udp dsResponseUdp = new Udp(clientAddress, clientPort)) {
                        dsResponseUdp.send(responseMessage);
                        System.out.println("  -> Response sent: " + responseMessage.getContent());
                    } catch (IOException e) {
                        System.err.println("Error sending UDP response: " + e.getMessage());
                    }

                }
            }
        } catch (SocketException e) {
            System.err.println("Socket error in DS: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("I/O error in DS: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("Serialization error in DS: " + e.getMessage());
        }
    }
}
