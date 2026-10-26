package com.example.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Optional;

@Repository
public class UserAccountRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app_users (username_key VARCHAR(80) PRIMARY KEY, username VARCHAR(80) NOT NULL, password_hash VARCHAR(100) NOT NULL, role VARCHAR(16) NOT NULL)");
    }

    public Optional<UserAccount> findByUsername(String username) {
        List<UserAccount> results = jdbcTemplate.query(
                "SELECT username, password_hash, role FROM app_users WHERE username_key = ?",
                (result, row) -> new UserAccount(result.getString("username"), result.getString("password_hash"), result.getString("role")),
                normalize(username));
        return results.stream().findFirst();
    }

    public UserAccount create(String username, String passwordHash) {
        UserAccount user = new UserAccount(username, passwordHash, "USER");
        try {
            jdbcTemplate.update("INSERT INTO app_users (username_key, username, password_hash, role) VALUES (?, ?, ?, ?)",
                    normalize(username), username, passwordHash, user.role());
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("Username is already registered", exception);
        }
        return user;
    }

    public List<UserAccount> findAll() {
        return jdbcTemplate.query("SELECT username, password_hash, role FROM app_users ORDER BY username_key",
                (result, row) -> new UserAccount(result.getString("username"), result.getString("password_hash"), result.getString("role")));
    }

    public void seed(String username, String passwordHash, String role) {
        jdbcTemplate.update("MERGE INTO app_users (username_key, username, password_hash, role) KEY(username_key) VALUES (?, ?, ?, ?)",
                normalize(username), username, passwordHash, role);
    }

    public boolean delete(String username) {
        return jdbcTemplate.update("DELETE FROM app_users WHERE username_key = ?", normalize(username)) > 0;
    }

    private String normalize(String username) {
        return username.toLowerCase();
    }
}