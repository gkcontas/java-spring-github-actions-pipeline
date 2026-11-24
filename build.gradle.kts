plugins {
    java
    id("org.springframework.boot") version "4.2.0"
    id("io.spring.dependency-management") version "1.1.7"
    checkstyle
    jacoco
    id("com.github.spotbugs") version "6.5.13"
}

group = "com.gkcontas"
version = "1.0.0"
description = "Feature flag service used as the subject of a GitHub Actions pipeline: cached Gradle builds, Testcontainers on the runner, static analysis, a coverage gate, layered Docker images and tag-driven releases"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:2.0.5")
    }
}

springBoot {
    buildInfo {
        // "time" changes on every build: it would make two builds of the same commit
        // produce different jars and defeat both the build cache and the checksum the
        // release publishes. The commit is what identifies a build.
        excludes = setOf("time")
        properties {
            additional = mapOf("commit" to (System.getenv("GITHUB_SHA") ?: "local"))
        }
    }
}

// Reproducible archives: no file timestamps, entries in a stable order.
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// Only the executable jar is shipped; the plain jar would just be a second, wrong
// candidate for build/libs/*.jar in the Dockerfile and the release.
tasks.jar {
    enabled = false
}

checkstyle {
    toolVersion = "10.26.1"
    maxWarnings = 0
}

spotbugs {
    excludeFilter = file("config/spotbugs/exclude.xml")
}

// The Spring dependency-management plugin applies the Boot BOM to every configuration,
// tool classpaths included: it pins commons-lang3 to 3.17.0 under SpotBugs, whose BCEL
// needs 3.18+ (org.apache.commons.lang3.Strings) and dies mid-analysis. The analyzer's
// classpath is not the application's; give it back the versions it asked for.
configurations.named("spotbugs") {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.apache.commons") {
            useVersion(requested.version!!)
            because("SpotBugs is a tool classpath; the Spring Boot BOM must not downgrade it")
        }
    }
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") { required = true }
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        csv.required = true
        html.required = true
    }
}

// The gate lives in the build, not in the workflow YAML: the same command fails on a
// laptop and on the runner, and nobody can skip it by editing a CI step.
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(files(classDirectories.files.map {
        fileTree(it) { exclude("**/FeatureFlagsApplication.class") }
    }))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
