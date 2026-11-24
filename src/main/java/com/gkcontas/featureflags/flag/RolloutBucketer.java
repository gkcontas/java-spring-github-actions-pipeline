package com.gkcontas.featureflags.flag;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Deterministic percentage rollout. The same user always lands in the same bucket for
 * a given flag, so raising the percentage only ever adds users. The flag key is part of
 * the hashed input: hashing the user id alone would make every flag at 10% pick the
 * very same 10% of users.
 */
public final class RolloutBucketer {

    public static final int BUCKETS = 100;

    private RolloutBucketer() {
    }

    public static int bucket(String flagKey, String userId) {
        CRC32 crc = new CRC32();
        crc.update((flagKey + ":" + userId).getBytes(StandardCharsets.UTF_8));
        // CRC32 is an unsigned 32-bit value held in a long, so the modulo is never
        // negative. String.hashCode() with Math.abs() would break on Integer.MIN_VALUE.
        return (int) (crc.getValue() % BUCKETS);
    }

    public static boolean isIncluded(String flagKey, String userId, int rolloutPercentage) {
        return bucket(flagKey, userId) < rolloutPercentage;
    }
}
