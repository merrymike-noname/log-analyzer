package org.kovalenko.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTE_LENGTH = 64;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenRepository repository;
    private final JwtProperties jwtProperties;

    /**
     * Generates a new refresh token, stores its hash in DB and returns the raw value.
     * The raw value is shown to the client only once.
     */
    @Transactional
    public String issue(UUID userId) {
        String rawToken = generateRawToken();
        Instant now = Instant.now();

        RefreshToken entity = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash(hash(rawToken))
                .expiresAt(now.plus(jwtProperties.refreshTokenTtl()))
                .build();

        repository.save(entity);
        return rawToken;
    }

    /**
     * Validates raw token and returns the associated entity if active.
     */
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findActive(String rawToken) {
        return repository.findByTokenHash(hash(rawToken))
                .filter(rt -> rt.isActive(Instant.now()));
    }

    @Transactional
    public void revoke(RefreshToken token) {
        token.setRevokedAt(Instant.now());
        repository.save(token);
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        repository.revokeAllByUserId(userId, Instant.now());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes());
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}