package com.gkcontas.featureflags.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.featureflags.IntegrationTestBase;
import com.gkcontas.featureflags.flag.FeatureFlagRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;

class ConcurrentUpdateIT extends IntegrationTestBase {

    private static final int WRITERS = 8;

    @Autowired
    TestRestTemplate http;

    @Autowired
    FeatureFlagRepository repository;

    @Test
    void writersRacingOnTheSameVersionProduceExactlyOneWinner() throws Exception {
        repository.deleteAll();
        http.postForEntity("/api/flags", Map.of("key", "contended", "description", "race", "enabled", true,
                "rolloutPercentage", 0), Map.class);

        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpStatusCode>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(WRITERS)) {
            for (int i = 0; i < WRITERS; i++) {
                int percentage = i + 1;
                results.add(pool.submit(() -> {
                    start.await();
                    return http.exchange("/api/flags/contended", HttpMethod.PUT,
                            new HttpEntity<>(Map.of("version", 0, "description", "writer " + percentage,
                                    "enabled", true, "rolloutPercentage", percentage)),
                            Map.class).getStatusCode();
                }));
            }
            start.countDown();
        }

        List<Integer> statuses = new ArrayList<>();
        for (Future<HttpStatusCode> result : results) {
            statuses.add(result.get().value());
        }
        // Writers that read version 0 after the winner committed are stopped by the explicit
        // version check; writers that read it before are stopped by Hibernate's @Version at
        // flush. Either way the loser gets 409, never a silent lost update.
        assertThat(statuses).containsOnly(200, 409).filteredOn(s -> s == 200).hasSize(1);
        assertThat(repository.findById("contended").orElseThrow().getVersion()).isEqualTo(1);
    }
}
