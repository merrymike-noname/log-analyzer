package org.kovalenko.user.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateEnabledRequest(
        @NotNull Boolean enabled
) {
}