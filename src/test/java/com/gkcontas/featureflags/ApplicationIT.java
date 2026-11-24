package com.gkcontas.featureflags;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

class ApplicationIT extends IntegrationTestBase {

    @Autowired
    TestRestTemplate http;

    @Test
    void readinessIsUp() {
        assertThat(http.getForObject("/actuator/health/readiness", String.class)).contains("UP");
    }

    @Test
    void infoExposesTheVersionAndCommitTheJarWasBuiltFrom() {
        Map<String, Map<String, Object>> info = http.getForObject("/actuator/info", Map.class);
        assertThat(info.get("build")).containsEntry("version", "1.0.0").containsKey("commit")
                .doesNotContainKey("time");
    }
}
