package pt.isec.pd.utils;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DBSchema {
    private DBSchema() { }

    public static void createTables() {
        String schema = """
            CREATE TABLE IF NOT EXISTS Teacher (
                id_teacher INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                email TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS Student (
                id_student INTEGER PRIMARY KEY AUTOINCREMENT,
                student_number TEXT UNIQUE NOT NULL,
                name TEXT NOT NULL,
                email TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS Question (
                id_question INTEGER PRIMARY KEY AUTOINCREMENT,
                teacher_id INTEGER NOT NULL,
                question_text TEXT NOT NULL,
                access_code TEXT UNIQUE NOT NULL,
                start_datetime TEXT NOT NULL,
                end_datetime TEXT NOT NULL,
                FOREIGN KEY (teacher_id) REFERENCES Teacher(id_teacher)
            );

            CREATE TABLE IF NOT EXISTS Option (
                id_option INTEGER PRIMARY KEY AUTOINCREMENT,
                question_id INTEGER NOT NULL,
                identifier TEXT NOT NULL,
                option_text TEXT NOT NULL,
                is_correct BOOLEAN NOT NULL,
                FOREIGN KEY (question_id) REFERENCES Question(id_question),
                UNIQUE(question_id, identifier)
            );

            CREATE TABLE IF NOT EXISTS Answer (
                id_answer INTEGER PRIMARY KEY AUTOINCREMENT,
                student_id INTEGER NOT NULL,
                question_id INTEGER NOT NULL,
                selected_option TEXT NOT NULL,
                realized_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(student_id, question_id),
                FOREIGN KEY (student_id) REFERENCES Student(id_student),
                FOREIGN KEY (question_id) REFERENCES Question(id_question)
            );
            """;

        try (Connection conn = ConnectDB.getConnection();
             Statement stmt = conn.createStatement()) {

            // Enforces foreign keys for the connection
            stmt.execute("PRAGMA foreign_keys = ON");
            stmt.execute(schema);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to create DB schema: " + ex.getMessage(), ex);
        }
    }

    public static void main(String[] args) {
        createTables();
        System.out.println("Tables ensured.");
    }
}
