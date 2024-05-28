package com.gungoronline.jwo.Helper;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseHelper {
    private static final String DB_URL = "jdbc:sqlite:default.sqlite";
    private static boolean logErrors = false;

    public static void initializeDatabase() {
        try (BufferedReader reader = new BufferedReader(new FileReader(".jwoconf"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("logErrors")) {
                    String[] parts = line.split("=");
                    if (parts.length == 2) {
                        logErrors = Boolean.parseBoolean(parts[1].trim());
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to load configuration file: " + e.getMessage());
        }

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            Class.forName("org.sqlite.JDBC");
            if (conn != null) {
                Statement stmt = conn.createStatement();
                String sql = "CREATE TABLE IF NOT EXISTS logs (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "timestamp TEXT NOT NULL," +
                        "method TEXT NOT NULL," +
                        "requestedFile TEXT NOT NULL," +
                        "statusCode INTEGER NOT NULL)";
                stmt.execute(sql);
            }
        } catch (SQLException | ClassNotFoundException e) {
            System.out.println(e.getMessage());
        }
    }

    public static void logRequest(String method, String requestedFile, int statusCode) {
        if (logErrors==true) {
            String sql = "INSERT INTO logs(timestamp, method, requestedFile, statusCode) VALUES(datetime('now'), ?, ?, ?)";

            try (Connection conn = DriverManager.getConnection(DB_URL);
                 java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, method);
                pstmt.setString(2, requestedFile);
                pstmt.setInt(3, statusCode);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }


    }

    // Error logging'i açıp kapatmak için bir metot eklenebilir
    public static void setLogErrors(boolean logErrors) {
        DatabaseHelper.logErrors = logErrors;
    }
}