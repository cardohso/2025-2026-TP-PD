package pt.isec.pd.directoryservice;

import pt.isec.pd.sockets.Udp; // Importa o seu socket Udp
import pt.isec.pd.common.Message; // Importa o modelo da mensagem
import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;

public class DirectoryService {
    private static final int DS_PORT = 9000;

    public static void main(String[] args) {
        System.out.println("🚀 Serviço de Diretoria a iniciar no porto UDP: " + DS_PORT);

        try (Udp dsUdp = new Udp(DS_PORT)) {

            while (true) {
                System.out.println("\n--- Aguardando por mensagens (Clientes/Servidores)...");

                Object receivedObject = dsUdp.receive();
                Message clientMessage = (Message) receivedObject;

                String clientAddress = dsUdp.getLastAddress().getHostAddress();
                int clientPort = dsUdp.getLastPort();

                System.out.println("📩 Mensagem recebida de " + clientAddress + ":" + clientPort);
                System.out.println("Conteúdo: " + clientMessage);

                if ("CLIENT_REQUEST".equals(clientMessage.getType())) {
                    System.out.println("  -> Pedido de cliente. A preparar resposta...");

                    // Simulação: O Servidor Principal (IP:Porta TCP)
                    String principalServerAddress = "127.0.0.1:5000";
                    Message responseMessage = new Message("DS_RESPONSE", principalServerAddress);

                    // Envia a resposta de volta ao cliente
                    try (Udp dsResponseUdp = new Udp(clientAddress, clientPort)) {
                        dsResponseUdp.send(responseMessage);
                        System.out.println("  -> Resposta enviada: " + responseMessage.getContent());
                    } catch (IOException e) {
                        System.err.println("Erro ao enviar resposta UDP: " + e.getMessage());
                    }

                }
            }
        } catch (SocketException e) {
            System.err.println("Erro de socket no DS: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Erro de I/O no DS: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("Erro de serialização no DS: " + e.getMessage());
        }
    }
}