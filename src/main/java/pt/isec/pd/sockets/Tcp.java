package pt.isec.pd.sockets;

import java.io.*;
import java.net.*;

public class Tcp implements Closeable {
    private Socket socket;
    private ServerSocket serverSocket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public Tcp(int port) throws IOException {
        serverSocket = new ServerSocket(port);
    }

    public Tcp(String address, int port) throws IOException {
        socket = new Socket(address, port);

        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
    }

    public void accept() throws IOException {
        socket = serverSocket.accept();

        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
    }

    public void setSoTimeout(int timeout) throws SocketException {
        socket.setSoTimeout(timeout);
    }

    public void send(Object obj) throws IOException {
        out.writeObject(obj);
        out.flush();
    }

    public Object receive() throws IOException, ClassNotFoundException {
        return in.readObject();
    }

    @Override
    public void close() throws IOException {
        if (in != null)
            in.close();
        if (out != null)
            out.close();
        if (socket != null && !socket.isClosed())
            socket.close();
        if (serverSocket != null && !serverSocket.isClosed())
            serverSocket.close();
    }

    public int getLocalPort() {
        return serverSocket.getLocalPort();
    }
}