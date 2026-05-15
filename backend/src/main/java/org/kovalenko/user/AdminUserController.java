package org.kovalenko.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.kovalenko.common.dto.PageResponse;
import org.kovalenko.user.dto.UpdateEnabledRequest;
import org.kovalenko.user.dto.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<User> users = userService.findAll(pageable);
        return ResponseEntity.ok(PageResponse.from(users, UserResponse::from));
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<UserResponse> setEnabled(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEnabledRequest request) {
        User updated = userService.setEnabled(id, request.enabled());
        return ResponseEntity.ok(UserResponse.from(updated));
    }
}