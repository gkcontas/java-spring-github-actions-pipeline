package com.gkcontas.featureflags.flag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "feature_flag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeatureFlag {

    @Id
    @Column(name = "flag_key")
    private String key;

    private String description;

    private boolean enabled;

    private int rolloutPercentage;

    @Version
    private long version;

    private Instant createdAt;

    private Instant updatedAt;

    public FeatureFlag(String key, String description, boolean enabled, int rolloutPercentage, Instant now) {
        this.key = key;
        this.description = description;
        this.enabled = enabled;
        this.rolloutPercentage = rolloutPercentage;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void change(String description, boolean enabled, int rolloutPercentage, Instant now) {
        this.description = description;
        this.enabled = enabled;
        this.rolloutPercentage = rolloutPercentage;
        this.updatedAt = now;
    }
}
