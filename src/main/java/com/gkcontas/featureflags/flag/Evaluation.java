package com.gkcontas.featureflags.flag;

public record Evaluation(String key, String userId, boolean enabled, Reason reason) {

    public enum Reason {
        FLAG_DISABLED,
        ROLLOUT_INCLUDED,
        ROLLOUT_EXCLUDED
    }
}
