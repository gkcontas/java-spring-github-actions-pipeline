package com.gkcontas.featureflags.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class FlagRequests {

    private FlagRequests() {
    }

    public record Create(
            @NotBlank(message = "key is required")
            @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}",
                    message = "key must be lowercase letters, digits and hyphens, up to 64 characters")
            String key,
            @NotBlank(message = "description is required")
            @Size(max = 255, message = "description must be at most 255 characters")
            String description,
            @NotNull(message = "enabled is required")
            Boolean enabled,
            @NotNull(message = "rolloutPercentage is required")
            @Min(value = 0, message = "rolloutPercentage must be between 0 and 100")
            @Max(value = 100, message = "rolloutPercentage must be between 0 and 100")
            Integer rolloutPercentage) {
    }

    public record Update(
            @NotNull(message = "version is required")
            Long version,
            @NotBlank(message = "description is required")
            @Size(max = 255, message = "description must be at most 255 characters")
            String description,
            @NotNull(message = "enabled is required")
            Boolean enabled,
            @NotNull(message = "rolloutPercentage is required")
            @Min(value = 0, message = "rolloutPercentage must be between 0 and 100")
            @Max(value = 100, message = "rolloutPercentage must be between 0 and 100")
            Integer rolloutPercentage) {
    }
}
