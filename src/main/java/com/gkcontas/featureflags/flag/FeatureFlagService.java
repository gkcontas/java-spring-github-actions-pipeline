package com.gkcontas.featureflags.flag;

import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    private final FeatureFlagRepository repository;
    private final Clock clock;

    @Transactional
    public FeatureFlag create(String key, String description, boolean enabled, int rolloutPercentage) {
        if (repository.existsById(key)) {
            throw new FlagConflictException("Feature flag '" + key + "' already exists");
        }
        return repository.save(new FeatureFlag(key, description, enabled, rolloutPercentage, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<FeatureFlag> findAll() {
        return repository.findAll(Sort.by("key"));
    }

    @Transactional(readOnly = true)
    public FeatureFlag find(String key) {
        return repository.findById(key).orElseThrow(() -> new FlagNotFoundException(key));
    }

    @Transactional
    public FeatureFlag update(String key, long expectedVersion, String description, boolean enabled,
            int rolloutPercentage) {
        FeatureFlag flag = find(key);
        // The version from the client is compared explicitly: the entity was just loaded,
        // so Hibernate's own @Version check would only catch a race inside this
        // transaction, not a client editing a stale copy it read minutes ago.
        if (flag.getVersion() != expectedVersion) {
            throw new FlagConflictException("Feature flag '" + key + "' is at version " + flag.getVersion()
                    + ", request was based on version " + expectedVersion);
        }
        flag.change(description, enabled, rolloutPercentage, clock.instant());
        return repository.saveAndFlush(flag);
    }

    @Transactional(readOnly = true)
    public Evaluation evaluate(String key, String userId) {
        FeatureFlag flag = find(key);
        if (!flag.isEnabled()) {
            return new Evaluation(key, userId, false, Evaluation.Reason.FLAG_DISABLED);
        }
        boolean included = RolloutBucketer.isIncluded(key, userId, flag.getRolloutPercentage());
        return new Evaluation(key, userId, included,
                included ? Evaluation.Reason.ROLLOUT_INCLUDED : Evaluation.Reason.ROLLOUT_EXCLUDED);
    }
}
