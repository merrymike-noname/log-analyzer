package org.kovalenko.user;

import lombok.RequiredArgsConstructor;
import org.kovalenko.auth.RefreshTokenService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<User> findAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Transactional
    public User setEnabled(UUID id, boolean enabled) {
        User user = getById(id);
        user.setEnabled(enabled);
        User saved = userRepository.save(user);

        if (!enabled) {
            refreshTokenService.revokeAllForUser(id);
        }

        return saved;
    }
}