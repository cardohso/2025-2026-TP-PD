// java
package pt.isec.pd.client;

import pt.isec.pd.sockets.Udp;
import pt.isec.pd.sockets.Tcp;
import pt.isec.pd.common.Message;

import java.io.IOException;

public class Client {
    private static final String DS_ADDRESS = "127.0.0.1";
    private static final int DS_PORT = 9000;

    public static void main(String[] args) {
        System.out.println("Cliente a iniciar...");
        String principalServer = null;

        try (Udp clientUdp = new Udp(DS_ADDRESS, DS_PORT)) {
            Message requestMessage = new Message("CLIENT_REQUEST", "GET_PRINCIPAL_SERVER");
            clientUdp.send(requestMessage);
            System.out.println("Pedido UDP enviado para o DS.");

            clientUdp.setSoTimeout(5000);
            System.out.println("\n--- Aguardando resposta do DS...");
            Object receivedObject = clientUdp.receive();
            Message responseMessage = (Message) receivedObject;

            if ("DS_RESPONSE".equals(responseMessage.getType())) {
                principalServer = responseMessage.getContent();
                System.out.println("Resposta recebida. Servidor Principal: **" + principalServer + "**");
            } else {
                System.out.println("Resposta inesperada do DS. A terminar.");
                return;
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Falha na comunicação UDP com o DS. A terminar. (Motivo: " + e.getMessage() + ")");
            return;
        }

        if (principalServer != null) {
            String[] parts = principalServer.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            System.out.println("\n--- Estabelecendo conexão TCP com o servidor principal: " + ip + ":" + port);

            // Keep the TCP connection open and wait for server response before closing
            try (Tcp clientTcp = new Tcp(ip, port)) {
                System.out.println("Conexão TCP estabelecida com sucesso!");

                clientTcp.send(new Message("AUTH_REQUEST", "user@isec.pt:password123"));
                System.out.println("Credenciais enviadas para o servidor.");

                // Wait for server ACK (avoid closing immediately)
                try {
                    Object resp = clientTcp.receive(); // may throw IOException or ClassNotFoundException
                    if (resp instanceof Message) {
                        Message serverMsg = (Message) resp;
                        System.out.println("Resposta do servidor: " + serverMsg);
                    } else {
                        System.out.println("Resposta inesperada via TCP.");
                    }
                } catch (ClassNotFoundException | IOException e) {
                    System.err.println("Erro ao receber resposta TCP: " + e.getMessage());
                }

            } catch (IOException e) {
                System.err.println("Erro ao estabelecer ou usar a conexão TCP com o servidor principal: " + e.getMessage());
            }
        }

        System.out.println("\n Cliente encerrado.");
    }
}
