package pt.isec.pd.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UsersRepository {
    private static final Map<String, UserRecord> users = new ConcurrentHashMap<>();

    public static boolean register(String email, String password, String name) {
        if (email == null || password == null || email.isEmpty() || password.isEmpty())
            return false;
        return users.putIfAbsent(email.toLowerCase(),
                new UserRecord(password, name == null ? "" : name)) == null;
    }

    public static String authenticate(String email, String password) {
        if (email == null || password == null) return null;
        UserRecord r = users.get(email.toLowerCase());
        if (r != null && r.password.equals(password)) return r.name;
        return null;
    }

    private static class UserRecord {
        final String password;
        final String name;
        UserRecord(String password, String name) {
            this.password = password;
            this.name = name;
        }
    }
}
