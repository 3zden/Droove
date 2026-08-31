package org.aezden.paymentservice.Service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.aezden.paymentservice.Exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class IdentityService {
    private final SecretKey signingKey;

    public IdentityService(@Value("${JWT_SECRET:local-development-secret-must-be-at-least-32-bytes}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public UUID resolve(String forwardedUserId, String authorization) {
        if (forwardedUserId != null && !forwardedUserId.isBlank()) {
            try {
                return UUID.fromString(forwardedUserId);
            } catch (IllegalArgumentException ignored) {
                throw new UnauthorizedException();
            }
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthorizedException();
        }
        try {
            String subject = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(authorization.substring("Bearer ".length()).trim())
                    .getPayload()
                    .getSubject();
            return UUID.fromString(subject);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedException();
        }
    }
}
