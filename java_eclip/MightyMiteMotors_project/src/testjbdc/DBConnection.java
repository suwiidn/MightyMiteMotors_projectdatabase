package testjbdc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String URL =
            "jdbc:sqlserver://localhost:1433;" +
            "databaseName=MightyMiteMotors;" +
            "user=sa;" +
            "password=P@ssw0rd_2024!;" +
            "encrypt=true;" +
            "trustServerCertificate=true;";

    private DBConnection() {}

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }
}