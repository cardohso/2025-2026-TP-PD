// java
package pt.isec.pd.server;

import pt.isec.pd.utils.ConnectDB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UsersRepository {
    private static final Logger LOG = Logger.getLogger(UsersRepository.class.getName());

    public static String registerStudent(String email, String password, String name, String studentNumber) {
        if (email == null || password == null || studentNumber == null) return "INVALID_INPUT";
        String e = email.trim().toLowerCase();
        if (e.isEmpty() || password.isEmpty() || studentNumber.isEmpty()) return "INVALID_INPUT";

        // Check existing by email or student number
        try (Connection conn = ConnectDB.getConnection()) {
            String checkSql = "SELECT email, student_number FROM Student WHERE email = ? OR student_number = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setString(1, e);
                ps.setString(2, studentNumber.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String existingEmail = rs.getString("email");
                        String existingNumber = rs.getString("student_number");
                        if (existingEmail != null && existingEmail.equalsIgnoreCase(e)) {
                            return "EMAIL_ALREADY_EXISTS";
                        }
                        if (existingNumber != null && existingNumber.equals(studentNumber.trim())) {
                            return "STUDENT_NUMBER_ALREADY_EXISTS";
                        }
                        return "ALREADY_EXISTS";
                    }
                }
            }

            String sql = "INSERT INTO Student(student_number, name, email, password_hash) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, studentNumber.trim());
                ps.setString(2, name == null ? "" : name);
                ps.setString(3, e);
                ps.setString(4, password);
                int affected = ps.executeUpdate();
                return affected == 1 ? "OK" : "ERROR_INSERT";
            }
        } catch (SQLException ex) {
            LOG.log(Level.FINE, "Student registration failed for {0}: {1}", new Object[]{email, ex.getMessage()});
            return "SQL_ERROR: " + ex.getMessage();
        }
    }

    public static String registerTeacher(String email, String password, String name, String registrationCode) {
        if (email == null || password == null) return "INVALID_INPUT";
        String e = email.trim().toLowerCase();
        if (e.isEmpty() || password.isEmpty()) return "INVALID_INPUT";

        try (Connection conn = ConnectDB.getConnection()) {
            String checkSql = "SELECT email FROM Docentes WHERE email = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setString(1, e);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return "EMAIL_ALREADY_EXISTS";
                    }
                }
            }

            String sql = "INSERT INTO Docentes(name, email, password_hash, registration_code) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name == null ? "" : name);
                ps.setString(2, e);
                ps.setString(3, password);
                ps.setString(4, registrationCode == null ? "" : registrationCode);
                int affected = ps.executeUpdate();
                return affected == 1 ? "OK" : "ERROR_INSERT";
            }
        } catch (SQLException ex) {
            LOG.log(Level.FINE, "Teacher registration failed for {0}: {1}", new Object[]{email, ex.getMessage()});
            return "SQL_ERROR: " + ex.getMessage();
        }
    }

    public static String authenticate(String role, String email, String password) {
        if (email == null || password == null || role == null) return null;
        String e = email.trim().toLowerCase();
        if (e.isEmpty() || password.isEmpty()) return null;

        String sql;
        if ("DOCENTE".equalsIgnoreCase(role)) {
            sql = "SELECT name FROM Docentes WHERE email = ? AND password_hash = ?";
        } else {
            sql = "SELECT name FROM Student WHERE email = ? AND password_hash = ?";
        }

        try (Connection conn = ConnectDB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, e);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("name");
                    return name == null ? "" : name;
                }
            }
        } catch (SQLException ex) {
            LOG.log(Level.FINE, "Authentication error for {0}: {1}", new Object[]{email, ex.getMessage()});
        }
        return null;
    }
}
