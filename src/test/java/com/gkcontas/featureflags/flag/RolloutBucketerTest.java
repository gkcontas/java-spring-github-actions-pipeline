package com.gkcontas.featureflags.flag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RolloutBucketerTest {

    private static final int USERS = 100_000;

    @Test
    void sameUserAlwaysGetsTheSameBucket() {
        assertThat(RolloutBucketer.bucket("new-checkout", "user-42"))
                .isEqualTo(RolloutBucketer.bucket("new-checkout", "user-42"))
                .isBetween(0, RolloutBucketer.BUCKETS - 1);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 25, 50, 90})
    void includedShareMatchesThePercentage(int percentage) {
        long included = IntStream.range(0, USERS)
                .filter(i -> RolloutBucketer.isIncluded("new-checkout", "user-" + i, percentage))
                .count();
        assertThat(100.0 * included / USERS).isCloseTo(percentage, within(0.5));
    }

    @Test
    void zeroIncludesNobodyAndHundredIncludesEverybody() {
        assertThat(IntStream.range(0, 1_000).noneMatch(i -> RolloutBucketer.isIncluded("f", "u" + i, 0))).isTrue();
        assertThat(IntStream.range(0, 1_000).allMatch(i -> RolloutBucketer.isIncluded("f", "u" + i, 100))).isTrue();
    }

    @Test
    void raisingThePercentageOnlyAddsUsers() {
        for (int i = 0; i < 10_000; i++) {
            String user = "user-" + i;
            if (RolloutBucketer.isIncluded("new-checkout", user, 10)) {
                assertThat(RolloutBucketer.isIncluded("new-checkout", user, 20)).isTrue();
            }
        }
    }

    @Test
    void differentFlagsPickDifferentUsersAtTheSamePercentage() {
        long both = IntStream.range(0, USERS)
                .filter(i -> RolloutBucketer.isIncluded("flag-a", "user-" + i, 10)
                        && RolloutBucketer.isIncluded("flag-b", "user-" + i, 10))
                .count();
        // Independent 10% samples overlap on ~1% of users; the same hash for both
        // flags would overlap on all 10%.
        assertThat(100.0 * both / USERS).isCloseTo(1.0, within(0.3));
    }
}
