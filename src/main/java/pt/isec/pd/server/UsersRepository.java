// src/main/java/pt/isec/pd/server/UsersRepository.java
package pt.isec.pd.server;

import pt.isec.pd.utils.ConnectDB;
import pt.isec.pd.utils.SecurityUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UsersRepository {
    private static final Logger LOG = Logger.getLogger(UsersRepository.class.getName());

    // Call once (e.g., by an admin) to set the shared teacher registration code.
    public static void setTeacherRegistrationCode(String plainCode) {
        if (plainCode == null || plainCode.isBlank()) throw new IllegalArgumentException("Code required");
        String hashed = SecurityUtils.createHash(plainCode);
        try (Connection c = ConnectDB.getConnection()) {
            try (Statement stmt = c.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS Config (k TEXT PRIMARY KEY, v TEXT);");
            }
            try (PreparedStatement up = c.prepareStatement(
                    "INSERT INTO Config(k,v) VALUES('teacher_registration_code',?) ON CONFLICT(k) DO UPDATE SET v=excluded.v")) {
                up.setString(1, hashed);
                up.executeUpdate();
                LOG.info("Teacher registration code has been set/updated.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed storing registration code: " + e.getMessage(), e);
        }
    }

    private static String getStoredTeacherCodeHash() {
        try (Connection c = ConnectDB.getConnection();
             PreparedStatement q = c.prepareStatement("SELECT v FROM Config WHERE k='teacher_registration_code'")) {
            try (ResultSet rs = q.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException ignored) {}
        return null;
    }

    public static String registerStudent(String email, String password, String name, String studentNumber) {
        if (email == null || password == null || studentNumber == null) return "INVALID_INPUT";
        String e = email.trim().toLowerCase();
        if (e.isEmpty() || password.isEmpty() || studentNumber.trim().isEmpty()) return "INVALID_INPUT";

        try (Connection conn = ConnectDB.getConnection()) {
            String checkSql = "SELECT email, student_number FROM Student WHERE email = ? OR student_number = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setString(1, e);
                ps.setString(2, studentNumber.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        if (e.equalsIgnoreCase(rs.getString("email"))) return "EMAIL_ALREADY_EXISTS";
                        if (studentNumber.trim().equals(rs.getString("student_number"))) return "STUDENT_NUMBER_ALREADY_EXISTS";
                        return "ALREADY_EXISTS";
                    }
                }
            }

            String sql = "INSERT INTO Student(student_number, name, email, password_hash) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, studentNumber.trim());
                ps.setString(2, name == null ? "" : name);
                ps.setString(3, e);
                ps.setString(4, SecurityUtils.createHash(password)); // Hash password
                int affected = ps.executeUpdate();
                return affected == 1 ? "OK" : "ERROR_INSERT";
            }
        } catch (SQLException ex) {
            LOG.log(Level.FINE, "Student registration failed for {0}: {1}", new Object[]{email, ex.getMessage()});
            return "SQL_ERROR: " + ex.getMessage();
        }
    }

    public static String registerTeacher(String email, String password, String name, String registrationCode) {
        if (email == null || password == null || registrationCode == null) return "INVALID_INPUT";
        String e = email.trim().toLowerCase();
        if (e.isEmpty() || password.isEmpty() || registrationCode.isEmpty()) return "INVALID_INPUT";

        String storedHash = getStoredTeacherCodeHash();
        if (storedHash == null || !SecurityUtils.verify(registrationCode, storedHash)) {
            return "INVALID_REGISTRATION_CODE";
        }

        try (Connection conn = ConnectDB.getConnection()) {
            String checkSql = "SELECT email FROM Docentes WHERE email = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setString(1, e);
                if (ps.executeQuery().next()) return "EMAIL_ALREADY_EXISTS";
            }

            String sql = "INSERT INTO Docentes(name, email, password_hash, registration_code_hash) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name == null ? "" : name);
                ps.setString(2, e);
                ps.setString(3, SecurityUtils.createHash(password)); // Hash password
                ps.setString(4, storedHash); // Store the hash of the code for audit
                int affected = ps.executeUpdate();
                return affected == 1 ? "OK" : "ERROR_INSERT";
            }
        } catch (SQLException ex) {
            LOG.log(Level.FINE, "Teacher registration failed for {0}: {1}", new Object[]{email, ex.getMessage()});
            return "SQL_ERROR: " + ex.getMessage();
        }
    }

    public static String updateTeacher(String currentEmail, String newName, String newEmail, String newPassword) {
        boolean hasNewName = newName != null && !newName.isBlank();
        boolean hasNewEmail = newEmail != null && !newEmail.isBlank();
        boolean hasNewPassword = newPassword != null && !newPassword.isBlank();

        if (!hasNewName && !hasNewEmail && !hasNewPassword) {
            return "NO_CHANGES_PROVIDED";
        }

        try (Connection conn = ConnectDB.getConnection()) {
            // If email is changing, check if the new one is already taken
            if (hasNewEmail) {
                String checkSql = "SELECT 1 FROM Docentes WHERE email = ? AND email != ?";
                try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    ps.setString(1, newEmail.trim().toLowerCase());
                    ps.setString(2, currentEmail);
                    if (ps.executeQuery().next()) {
                        return "EMAIL_ALREADY_EXISTS";
                    }
                }
            }

            List<String> setClauses = new ArrayList<>();
            List<Object> params = new ArrayList<>();
            if (hasNewName) {
                setClauses.add("name = ?");
                params.add(newName.trim());
            }
            if (hasNewEmail) {
                setClauses.add("email = ?");
                params.add(newEmail.trim().toLowerCase());
            }
            if (hasNewPassword) {
                setClauses.add("password_hash = ?");
                params.add(SecurityUtils.createHash(newPassword));
            }

            String sql = "UPDATE Docentes SET " + String.join(", ", setClauses) + " WHERE email = ?";
            params.add(currentEmail);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                int affected = ps.executeUpdate();
                return affected == 1 ? "OK" : "USER_NOT_FOUND";
            }
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "Teacher update failed for {0}: {1}", new Object[]{currentEmail, ex.getMessage()});
            return "SQL_ERROR: " + ex.getMessage();
        }
    }

    public static String authenticate(String role, String email, String password) {
        if (email == null || password == null || role == null) return null;
        String e = email.trim().toLowerCase();
        if (e.isEmpty() || password.isEmpty()) return null;

        String table = "DOCENTE".equalsIgnoreCase(role) ? "Docentes" : "Student";
        String sql = "SELECT name, password_hash FROM " + table + " WHERE email = ?";

        try (Connection conn = ConnectDB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, e);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    if (SecurityUtils.verify(password, storedHash)) {
                        String name = rs.getString("name");
                        return name == null ? "" : name;
                    }
                }
            }
        } catch (SQLException ex) {
            LOG.log(Level.FINE, "Authentication error for {0}: {1}", new Object[]{email, ex.getMessage()});
        }
        return null; // Return null if user not found or password incorrect
    }
}
