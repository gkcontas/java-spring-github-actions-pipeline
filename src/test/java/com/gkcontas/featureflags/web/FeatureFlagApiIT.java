package com.gkcontas.featureflags.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.featureflags.IntegrationTestBase;
import com.gkcontas.featureflags.flag.FeatureFlagRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class FeatureFlagApiIT extends IntegrationTestBase {

    @Autowired
    TestRestTemplate http;

    @Autowired
    FeatureFlagRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void createsReadsAndListsFlags() {
        ResponseEntity<FlagResponse> created = create("new-checkout", true, 25);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).hasPath("/api/flags/new-checkout");
        assertThat(created.getBody().version()).isZero();
        create("dark-mode", false, 0);

        FlagResponse read = http.getForObject("/api/flags/new-checkout", FlagResponse.class);
        assertThat(read.rolloutPercentage()).isEqualTo(25);
        assertThat(read.enabled()).isTrue();

        List<FlagResponse> all = http.exchange("/api/flags", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<FlagResponse>>() { }).getBody();
        assertThat(all).extracting(FlagResponse::key).containsExactly("dark-mode", "new-checkout");
    }

    @Test
    void duplicateKeyIsAConflict() {
        create("new-checkout", true, 25);
        ResponseEntity<Map> second = http.postForEntity("/api/flags", body("new-checkout", true, 50), Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().get("detail")).isEqualTo("Feature flag 'new-checkout' already exists");
    }

    @Test
    void invalidBodyListsEveryViolationInEnglish() {
        ResponseEntity<Map> response = http.postForEntity("/api/flags",
                Map.of("key", "Bad Key", "description", "", "rolloutPercentage", 101), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((List<String>) response.getBody().get("errors")).containsExactly(
                "description: description is required",
                "enabled: enabled is required",
                "key: key must be lowercase letters, digits and hyphens, up to 64 characters",
                "rolloutPercentage: rolloutPercentage must be between 0 and 100");
    }

    @Test
    void updateWithCurrentVersionBumpsItAndStaleVersionIsRejected() {
        create("new-checkout", true, 10);

        ResponseEntity<FlagResponse> updated = update("new-checkout", 0, 50);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().version()).isEqualTo(1);
        assertThat(updated.getBody().rolloutPercentage()).isEqualTo(50);

        ResponseEntity<Map> stale = http.exchange("/api/flags/new-checkout", HttpMethod.PUT,
                new HttpEntity<>(updateBody(0, 90)), Map.class);
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody().get("detail"))
                .isEqualTo("Feature flag 'new-checkout' is at version 1, request was based on version 0");
        assertThat(http.getForObject("/api/flags/new-checkout", FlagResponse.class).rolloutPercentage())
                .isEqualTo(50);
    }

    @Test
    void unknownFlagIsNotFound() {
        ResponseEntity<Map> response = http.getForEntity("/api/flags/missing/evaluation?userId=u1", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("detail")).isEqualTo("Feature flag 'missing' does not exist");
        assertThat(update("missing", 0, 10).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void evaluationExplainsTheDecision() {
        create("off", false, 100);
        create("everyone", true, 100);
        create("nobody", true, 0);

        assertThat(evaluate("off", "u1")).extracting("enabled", "reason").containsExactly(false, "FLAG_DISABLED");
        assertThat(evaluate("everyone", "u1")).extracting("enabled", "reason")
                .containsExactly(true, "ROLLOUT_INCLUDED");
        assertThat(evaluate("nobody", "u1")).extracting("enabled", "reason")
                .containsExactly(false, "ROLLOUT_EXCLUDED");
    }

    @Test
    void evaluationRequiresUserId() {
        create("everyone", true, 100);
        ResponseEntity<Map> missing = http.getForEntity("/api/flags/everyone/evaluation", Map.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((List<String>) missing.getBody().get("errors")).containsExactly("userId is required");

        ResponseEntity<Map> blank = http.getForEntity("/api/flags/everyone/evaluation?userId= ", Map.class);
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((List<String>) blank.getBody().get("errors")).containsExactly("userId is required");
    }

    private Map<String, Object> evaluate(String key, String user) {
        return http.getForObject("/api/flags/" + key + "/evaluation?userId=" + user, Map.class);
    }

    private ResponseEntity<FlagResponse> create(String key, boolean enabled, int percentage) {
        return http.postForEntity("/api/flags", body(key, enabled, percentage), FlagResponse.class);
    }

    private ResponseEntity<FlagResponse> update(String key, long version, int percentage) {
        return http.exchange("/api/flags/" + key, HttpMethod.PUT, new HttpEntity<>(updateBody(version, percentage)),
                FlagResponse.class);
    }

    private static Map<String, Object> body(String key, boolean enabled, int percentage) {
        return Map.of("key", key, "description", "flag " + key, "enabled", enabled,
                "rolloutPercentage", percentage);
    }

    private static Map<String, Object> updateBody(long version, int percentage) {
        return Map.of("version", version, "description", "changed", "enabled", true,
                "rolloutPercentage", percentage);
    }
}
