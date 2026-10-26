package com.example.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class AuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }

    @Bean
    CommandLineRunner seedDemoAccounts(UserAccountRepository users, PasswordEncoder passwordEncoder,
                                       @Value("${app.admin.username}") String adminUsername,
                                       @Value("${app.admin.password}") String adminPassword,
                                       @Value("${app.demo.password}") String demoPassword,
                                       @Value("${app.demo.users}") String demoUsers) {
        return args -> {
            users.seed(adminUsername, passwordEncoder.encode(adminPassword), "ADMIN");
            String passwordHash = passwordEncoder.encode(demoPassword);
            for (String username : demoUsers.split(",")) {
                if (!username.isBlank()) {
                    users.seed(username.trim(), passwordHash, "USER");
                }
            }
        };
    }
}