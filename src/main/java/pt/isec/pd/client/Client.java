package pt.isec.pd.client;

import pt.isec.pd.sockets.Udp; // Importa o seu socket Udp
import pt.isec.pd.sockets.Tcp; // Importa o seu socket Tcp
import pt.isec.pd.common.Message; // Importa o modelo da mensagem
import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;

public class Client {
    private static final String DS_ADDRESS = "127.0.0.1";
    private static final int DS_PORT = 9000;

    public static void main(String[] args) {
        System.out.println("Cliente a iniciar...");
        String principalServer = null;

        // Comunicação UDP com o Serviço de Diretoria
        try (Udp clientUdp = new Udp(DS_ADDRESS, DS_PORT)) {

            Message requestMessage = new Message("CLIENT_REQUEST", "GET_PRINCIPAL_SERVER");
            clientUdp.send(requestMessage);
            System.out.println("Pedido UDP enviado para o DS.");

            clientUdp.setSoTimeout(5000); // Timeout de 5 segundos
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
            return; // Termina, conforme o requisito
        }

        // Ligação TCP ao Servidor Principal
        if (principalServer != null) {
            String[] parts = principalServer.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            System.out.println("\n--- Estabelecendo conexão TCP com o servidor principal: " + ip + ":" + port);

            try (Tcp clientTcp = new Tcp(ip, port)) {
                System.out.println("Conexão TCP estabelecida com sucesso!");

                // lógica de AUTENTICAÇÃO/REGISTO via TCP

                // Exemplo: Simular envio de credenciais
                clientTcp.send(new Message("AUTH_REQUEST", "user@isec.pt:password123"));
                System.out.println("Credenciais enviadas para o servidor.");

                // lógica(esperar por resposta, menus, etc.)

            } catch (IOException e) {
                System.err.println("Erro ao estabelecer ou usar a conexão TCP com o servidor principal: " + e.getMessage());
            }
        }

        System.out.println("\n Cliente encerrado.");
    }
}