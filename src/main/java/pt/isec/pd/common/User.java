package pt.isec.pd.common;

import java.io.Serializable;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String email;
    private String password;
    private String studentNumber;
    private String registrationCode;

    // constructor for registration/editing of Docente
    public User(String name, String email, String password, String registrationCode) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.registrationCode = registrationCode;
    }

    // constructor for registration/editing of Student
    public User(String name, String email, String password, String studentNumber, boolean isStudent) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.studentNumber = studentNumber;
    }

    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getStudentNumber() { return studentNumber; }
    public String getRegistrationCode() { return registrationCode; }

    // Método para identificar o tipo de utilizador para o Servidor
    public boolean isTeacherRequest() {
        return registrationCode != null && !registrationCode.isEmpty();
    }
}