package pt.isec.pd.server;

import pt.isec.pd.utils.ConnectDB;
import pt.isec.pd.utils.SecurityUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class UsersRepository {

    private static String getTeacherRegistrationCodeFromDB() throws SQLException {
        try (Connection c = ConnectDB.getConnection()) {
            // Ensure the configuration row exists with default values if table is empty
            try (Statement stmt = c.createStatement()) {
                stmt.execute("INSERT INTO configuration (database_version) SELECT 0 WHERE NOT EXISTS (SELECT 1 FROM configuration)");
            }

            // Retrieve the code
            try (PreparedStatement ps = c.prepareStatement("SELECT teacher_code FROM configuration LIMIT 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("teacher_code");
                    } else {
                        // This case should ideally not be reached if the insert-if-not-exists logic works
                        throw new IllegalStateException("Teacher registration code not found in configuration table.");
                    }
                }
            }
        }
    }

    public static String registerTeacher(String email, String password, String name, String registrationCode) {
        if (email == null || email.isBlank() || password == null || password.isBlank() || name == null || name.isBlank()) {
            return "INVALID_INPUT";
        }

        try {
            String dbRegistrationCode = getTeacherRegistrationCodeFromDB();
            if (!dbRegistrationCode.equals(registrationCode)) {
                return "INVALID_REGISTRATION_CODE";
            }
        } catch (SQLException | IllegalStateException e) {
            System.err.println("Error retrieving teacher registration code: " + e.getMessage());
            return "SQL_ERROR: " + e.getMessage();
        }


        String passwordHash = SecurityUtils.createHash(password);

        try (Connection c = ConnectDB.getConnection()) {
            // Check if email already exists
            try (PreparedStatement ps = c.prepareStatement("SELECT id_teacher FROM Docentes WHERE email = ?")) {
                ps.setString(1, email.toLowerCase());
                if (ps.executeQuery().next()) {
                    return "EMAIL_ALREADY_EXISTS";
                }
            }

            // Insert new teacher
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO Docentes (name, email, password_hash, registration_code) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, name);
                ps.setString(2, email.toLowerCase());
                ps.setString(3, passwordHash);
                ps.setString(4, registrationCode); // Storing the code used for registration
                int affectedRows = ps.executeUpdate();
                return affectedRows > 0 ? "OK" : "ERROR_INSERT";
            }
        } catch (SQLException e) {
            System.err.println("SQL Error during teacher registration: " + e.getMessage());
            return "SQL_ERROR: " + e.getMessage();
        }
    }

    public static String registerStudent(String email, String password, String name, String studentNumber) {
        if (email == null || email.isBlank() || password == null || password.isBlank() || name == null || name.isBlank() || studentNumber == null || studentNumber.isBlank()) {
            return "INVALID_INPUT";
        }

        String passwordHash = SecurityUtils.createHash(password);

        try (Connection c = ConnectDB.getConnection()) {
            // Check for existing email or student number
            try (PreparedStatement ps = c.prepareStatement("SELECT id_student FROM Student WHERE email = ? OR student_number = ?")) {
                ps.setString(1, email.toLowerCase());
                ps.setString(2, studentNumber);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        // This part could be more specific if we query separately
                        return "EMAIL_ALREADY_EXISTS"; // Or STUDENT_NUMBER_ALREADY_EXISTS
                    }
                }
            }

            // Insert new student
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO Student (student_number, name, email, password_hash) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, studentNumber);
                ps.setString(2, name);
                ps.setString(3, email.toLowerCase());
                ps.setString(4, passwordHash);
                int affectedRows = ps.executeUpdate();
                return affectedRows > 0 ? "OK" : "ERROR_INSERT";
            }
        } catch (SQLException e) {
            System.err.println("SQL Error during student registration: " + e.getMessage());
            return "SQL_ERROR: " + e.getMessage();
        }
    }

    public static String authenticate(String role, String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return null;
        }

        String tableName = "DOCENTE".equals(role) ? "Docentes" : "Student";
        String sql = "SELECT name, password_hash FROM " + tableName + " WHERE email = ?";

        try (Connection c = ConnectDB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    if (SecurityUtils.verify(password, storedHash)) {
                        return rs.getString("name");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Error during authentication: " + e.getMessage());
        }
        return null;
    }

    public static String updateTeacher(String currentEmail, String newName, String newEmail, String newPassword) {
        if (currentEmail == null || currentEmail.isBlank()) {
            return "Current email is required.";
        }

        StringBuilder sql = new StringBuilder("UPDATE Docentes SET ");
        boolean first = true;

        if (newName != null && !newName.isBlank()) {
            sql.append("name = ?");
            first = false;
        }
        if (newEmail != null && !newEmail.isBlank()) {
            if (!first) sql.append(", ");
            sql.append("email = ?");
            first = false;
        }
        if (newPassword != null && !newPassword.isBlank()) {
            if (!first) sql.append(", ");
            sql.append("password_hash = ?");
            first = false;
        }

        if (first) {
            return "No update information provided.";
        }

        sql.append(" WHERE email = ?");

        try (Connection c = ConnectDB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            if (newName != null && !newName.isBlank()) {
                ps.setString(paramIndex++, newName);
            }
            if (newEmail != null && !newEmail.isBlank()) {
                ps.setString(paramIndex++, newEmail.toLowerCase());
            }
            if (newPassword != null && !newPassword.isBlank()) {
                ps.setString(paramIndex++, SecurityUtils.createHash(newPassword));
            }
            ps.setString(paramIndex, currentEmail.toLowerCase());

            int affectedRows = ps.executeUpdate();
            return affectedRows > 0 ? "OK" : "USER_NOT_FOUND";
        } catch (SQLException e) {
            System.err.println("SQL Error during teacher update: " + e.getMessage());
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                return "EMAIL_ALREADY_EXISTS";
            }
            return "SQL_ERROR: " + e.getMessage();
        }
    }
}
