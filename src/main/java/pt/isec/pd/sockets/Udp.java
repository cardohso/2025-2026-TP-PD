package pt.isec.pd.sockets;

import java.io.*;
import java.net.*;

public class Udp implements Closeable {
    private static final int SIZE = 4096;

    private DatagramPacket packet;
    private DatagramSocket socket;
    private ByteArrayOutputStream bOut;
    private ObjectOutputStream out;

    public Udp(int port) throws IOException, SocketException {
        bOut = new ByteArrayOutputStream();
        out = new ObjectOutputStream(bOut);

        packet = new DatagramPacket(bOut.toByteArray(), bOut.size());
        socket = new DatagramSocket(port);
    }

    public Udp(String address, int port) throws IOException, UnknownHostException, SocketException {
        bOut = new ByteArrayOutputStream();
        out = new ObjectOutputStream(bOut);

        packet = new DatagramPacket(bOut.toByteArray(), bOut.size(), InetAddress.getByName(address), port);
        socket = new DatagramSocket();
    }

    public void setSoTimeout(int timeout) throws SocketException {
        socket.setSoTimeout(timeout);
    }

    public void send(Object obj) throws IOException {
        out.writeObject(obj);
        out.flush();

        packet.setData(bOut.toByteArray());
        packet.setLength(bOut.size());

        socket.send(packet);
    }

    public Object receive() throws IOException, ClassNotFoundException {
        packet = new DatagramPacket(new byte[SIZE], SIZE);

        socket.receive(packet);

        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(packet.getData(), 0, packet.getLength()));
        return in.readObject();
    }

    public String getAddress() {
        return packet.getAddress().getHostAddress();
    }

    public int getLocalPort() { return socket.getLocalPort(); }

    public void setLocalPort(int localPort) throws SocketException {
        socket = new DatagramSocket(localPort);
    }

    public int getPort() {
        return packet.getPort();
    }

    public int getLength() {
        return packet.getLength();
    }

    public InetAddress getLastAddress() {
        return packet == null ? null : packet.getAddress();
    }

    public int getLastPort() {
        return packet == null ? -1 : packet.getPort();
    }

    @Override
    public void close() {
        if (socket != null && !socket.isClosed())
            socket.close();
    }
}