package com.liveshield;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@SpringBootApplication
public class LiveShieldApplication {
    public static void main(String[] args) {
        loadDotEnv();
        normalizeDatabaseUrl();
        SpringApplication.run(LiveShieldApplication.class, args);
    }

    private static void normalizeDatabaseUrl() {
        String dbUrl = System.getenv("DATABASE_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = System.getProperty("DATABASE_URL");
        }
        if (dbUrl != null && !dbUrl.isBlank()) {
            if (dbUrl.startsWith("postgres://")) {
                dbUrl = "jdbc:postgresql://" + dbUrl.substring("postgres://".length());
                System.setProperty("spring.datasource.url", dbUrl);
            } else if (dbUrl.startsWith("postgresql://")) {
                dbUrl = "jdbc:" + dbUrl;
                System.setProperty("spring.datasource.url", dbUrl);
            } else if (dbUrl.startsWith("jdbc:")) {
                System.setProperty("spring.datasource.url", dbUrl);
            }
            System.out.println(">>> [DATABASE] Configured DataSource URL from environment.");
        }
    }

    private static void loadDotEnv() {
        Path envPath = Paths.get(".env");
        if (!Files.exists(envPath)) {
            envPath = Paths.get("../.env");
        }
        if (Files.exists(envPath)) {
            try {
                List<String> lines = Files.readAllLines(envPath);
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int eqIndex = line.indexOf('=');
                    if (eqIndex > 0) {
                        String key = line.substring(0, eqIndex).trim();
                        String value = line.substring(eqIndex + 1).trim();
                        if ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, value);
                        }
                    }
                }
                System.out.println(">>> [DOTENV] Successfully loaded environment from: " + envPath.toAbsolutePath());
            } catch (Exception ex) {
                System.err.println(">>> [DOTENV] Error reading .env: " + ex.getMessage());
            }
        } else {
            System.out.println(">>> [DOTENV] No .env file found at: " + envPath.toAbsolutePath());
        }
    }
}
