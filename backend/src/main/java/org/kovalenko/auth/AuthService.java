package org.kovalenko.auth;

import lombok.RequiredArgsConstructor;
import org.kovalenko.auth.dto.LoginRequest;
import org.kovalenko.auth.dto.RegisterRequest;
import org.kovalenko.auth.dto.TokenResponse;
import org.kovalenko.user.Role;
import org.kovalenko.user.User;
import org.kovalenko.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyTakenException(request.email());
        }

        User user = User.builder()
                .id(UUID.randomUUID())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .enabled(true)
                .build();

        userRepository.save(user);

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isEnabled()) {
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        RefreshToken token = refreshTokenService.findActive(rawRefreshToken)
                .orElseThrow(InvalidRefreshTokenException::new);

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(InvalidRefreshTokenException::new);

        // rotation: revoke old, issue new
        refreshTokenService.revoke(token);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.findActive(rawRefreshToken)
                .ifPresent(refreshTokenService::revoke);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.issue(user.getId());
        return new TokenResponse(accessToken, refreshToken, jwtService.getAccessTokenTtlSeconds());
    }
}