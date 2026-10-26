package com.example.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Service
public class JwtTokenService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final long ttlSeconds;

    public JwtTokenService(@Value("${spring.security.oauth2.resourceserver.jwt.secret}") String secret,
                           @Value("${app.jwt.issuer}") String issuer,
                           @Value("${app.jwt.ttl-seconds}") long ttlSeconds) {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(key, "HmacSHA256")));
        this.issuer = issuer;
        this.ttlSeconds = ttlSeconds;
    }

    public AuthRequests.TokenResponse issue(UserAccount user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .subject(user.username())
                .claim("roles", List.of(user.role()))
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthRequests.TokenResponse(token, "Bearer", ttlSeconds, user.username(), user.role());
    }
}