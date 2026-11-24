package com.gkcontas.featureflags.web;

import com.gkcontas.featureflags.flag.FeatureFlag;
import java.time.Instant;

public record FlagResponse(String key, String description, boolean enabled, int rolloutPercentage,
        long version, Instant createdAt, Instant updatedAt) {

    static FlagResponse of(FeatureFlag flag) {
        return new FlagResponse(flag.getKey(), flag.getDescription(), flag.isEnabled(),
                flag.getRolloutPercentage(), flag.getVersion(), flag.getCreatedAt(), flag.getUpdatedAt());
    }
}
