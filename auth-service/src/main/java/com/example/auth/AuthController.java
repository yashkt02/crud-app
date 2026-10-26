package com.example.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;

    public AuthController(UserAccountRepository users, PasswordEncoder passwordEncoder, JwtTokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody AuthRequests.Credentials credentials) {
        validate(credentials);
        try {
            UserAccount user = users.create(credentials.username().trim(), passwordEncoder.encode(credentials.password()));
            return ResponseEntity.status(HttpStatus.CREATED).body(tokens.issue(user));
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    @PostMapping("/auth/login")
    public AuthRequests.TokenResponse login(@RequestBody AuthRequests.Credentials credentials) {
        if (credentials == null || credentials.username() == null || credentials.password() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        UserAccount user = users.findByUsername(credentials.username().trim())
                .filter(account -> passwordEncoder.matches(credentials.password(), account.passwordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
        return tokens.issue(user);
    }

    @GetMapping("/admin/users")
    public List<Map<String, String>> users() {
        return users.findAll().stream().map(user -> Map.of("username", user.username(), "role", user.role())).toList();
    }

    @GetMapping("/users")
    public List<Map<String, String>> registeredUsers() {
        return users.findAll().stream().map(user -> Map.of("username", user.username(), "role", user.role())).toList();
    }

    @DeleteMapping("/admin/users/{username}")
    public ResponseEntity<Void> deleteUser(@PathVariable String username) {
        return users.delete(username) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private void validate(AuthRequests.Credentials credentials) {
        if (credentials == null || credentials.username() == null || credentials.username().isBlank()
                || credentials.username().length() > 80 || credentials.password() == null || credentials.password().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required and password must contain at least 8 characters");
        }
    }
}