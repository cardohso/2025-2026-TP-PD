package pt.isec.pd.server;

import pt.isec.pd.common.Message;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ClientManager {
    private static final Set<ClientHandler> handlers = new CopyOnWriteArraySet<>();

    public static void register(ClientHandler handler) {
        handlers.add(handler);
    }

    public static void unregister(ClientHandler handler) {
        handlers.remove(handler);
    }

    public static void broadcast(String from, String content) {
        Message m = new Message("MESSAGE", from + ": " + content);
        for (ClientHandler h : handlers) {
            try {
                h.send(m);
            } catch (IOException e) {
                // If sending fails, unregister that handler to avoid repeated errors
                unregister(h);
                try { h.closeSilently(); } catch (IOException ignored) {}
            }
        }
    }
}
