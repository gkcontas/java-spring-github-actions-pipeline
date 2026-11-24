package com.gkcontas.featureflags;

import static org.assertj.core.api.Assertions.assertThat;

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
}
