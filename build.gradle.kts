import java.util.jar.Manifest
import java.util.jar.Attributes
import java.util.jar.JarOutputStream

plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.imecuadorian"
version = "0.0.1-SNAPSHOT"
description = "emitta"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {

    // =========================================================
    // SPRING BOOT CORE
    // =========================================================

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")


    // =========================================================
    // SECURITY + JWT
    // =========================================================

    implementation("org.springframework.boot:spring-boot-starter-security")

    implementation(
        "org.springframework.boot:spring-boot-starter-security-oauth2-resource-server"
    )


    // =========================================================
    // DATABASE
    // =========================================================

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    runtimeOnly("org.postgresql:postgresql")


    // =========================================================
    // REDIS
    // =========================================================

    implementation("org.springframework.boot:spring-boot-starter-data-redis")


    // =========================================================
    // RABBITMQ
    // =========================================================

    implementation("org.springframework.boot:spring-boot-starter-amqp")


    // =========================================================
    // OPENAPI / SWAGGER UI
    // =========================================================

    implementation(
        "org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1"
    )


    // =========================================================
    // RESILIENCE4J
    // Circuit Breaker, Retry, Rate Limiter, Bulkhead...
    // =========================================================

    implementation(
        "io.github.resilience4j:resilience4j-spring-boot4:2.4.0"
    )

    implementation("org.springframework.boot:spring-boot-starter-aspectj")


    // =========================================================
    // XML / JAXB
    // Generación de XML de comprobantes electrónicos
    // =========================================================

    implementation(
        "jakarta.xml.bind:jakarta.xml.bind-api"
    )

    runtimeOnly(
        "org.glassfish.jaxb:jaxb-runtime"
    )


    // =========================================================
    // FIRMA ELECTRÓNICA XAdES / PKCS#12
    // DSS - Digital Signature Services
    // =========================================================

    implementation(
        platform("eu.europa.ec.joinup.sd-dss:dss-bom:6.5")
    )

    implementation(
        "eu.europa.ec.joinup.sd-dss:dss-xades"
    )

    implementation(
        "eu.europa.ec.joinup.sd-dss:dss-token"
    )

    implementation(
        "eu.europa.ec.joinup.sd-dss:dss-validation"
    )

    implementation(
        "eu.europa.ec.joinup.sd-dss:dss-utils-apache-commons"
    )

    // =========================================================
// S3-COMPATIBLE OBJECT STORAGE
// MinIO / AWS S3 / Cloudflare R2
// =========================================================

    implementation(
        platform("software.amazon.awssdk:bom:2.32.29")
    )

    implementation(
        "software.amazon.awssdk:s3"
    )

    implementation(
        "software.amazon.awssdk:url-connection-client"
    )

    // =========================================================
    // DEVELOPMENT
    // =========================================================

    implementation("org.apache.pdfbox:pdfbox:3.0.8")
    implementation("com.google.zxing:core:3.5.4")

    compileOnly("org.projectlombok:lombok")

    annotationProcessor("org.projectlombok:lombok")

    developmentOnly(
        "org.springframework.boot:spring-boot-devtools"
    )


    // =========================================================
    // TESTING
    // =========================================================

    testImplementation(
        "org.springframework.boot:spring-boot-starter-actuator-test"
    )

    testImplementation(
        "org.springframework.boot:spring-boot-starter-amqp-test"
    )

    testImplementation(
        "org.springframework.boot:spring-boot-starter-data-jpa-test"
    )

    testImplementation(
        "org.springframework.boot:spring-boot-starter-data-redis-test"
    )

    testImplementation(
        "org.springframework.boot:spring-boot-starter-flyway-test"
    )

    testImplementation(
        "org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test"
    )

    testImplementation(
        "org.springframework.boot:spring-boot-starter-security-test"
    )

    testImplementation(
        "org.springframework.boot:spring-boot-starter-validation-test"
    )

    testImplementation(
        "org.springframework.boot:spring-boot-starter-webmvc-test"
    )


    // =========================================================
    // TESTCONTAINERS
    // =========================================================

    testImplementation(
        "org.springframework.boot:spring-boot-testcontainers"
    )

    testImplementation(
        "org.testcontainers:testcontainers-junit-jupiter"
    )

    testImplementation(
        "org.testcontainers:testcontainers-postgresql"
    )

    testImplementation(
        "org.testcontainers:testcontainers-rabbitmq"
    )


    // =========================================================
    // LOMBOK TEST
    // =========================================================

    testCompileOnly("org.projectlombok:lombok")

    testAnnotationProcessor("org.projectlombok:lombok")

    testRuntimeOnly(
        "org.junit.platform:junit-platform-launcher"
    )
}

tasks.named<Test>("test") {
    environment("RUN_SRI_LIVE_TEST", "false")
    useJUnitPlatform {
        excludeTags("object-storage-integration")
        excludeTags("operational-load")
    }
}

tasks.withType<Test>().configureEach {
    doFirst {
        val launcher = layout.buildDirectory.file("operational/test-runtime-launcher.jar").get().asFile
        launcher.parentFile.mkdirs()
        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        manifest.mainAttributes[Attributes.Name.CLASS_PATH] = sourceSets["test"].runtimeClasspath.files
            .joinToString(" ") { it.toURI().toASCIIString() }
        JarOutputStream(launcher.outputStream(), manifest).use { }
        systemProperty("emitta.test.runtime-launcher", launcher.absolutePath)
    }
    // Isolated tests that do not provision Redis must never fall back to the developer's service.
    environment("REDIS_HOST", "127.0.0.1")
    environment("REDIS_PORT", "1")
    environment("EMITTA_OBSERVABILITY_ENABLED", "false")
    environment("SPRING_PROFILES_ACTIVE", "test")
    environment("SPRING_APPLICATION_JSON", "{}")
}

tasks.register<Test>("operationalLoadTest") {
    description = "Runs k6 against an isolated Testcontainers REST admission environment"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("operational-load") }
    maxHeapSize = "1g"
    outputs.upToDateWhen { false }
    shouldRunAfter(tasks.test)
}

tasks.register<Test>("objectStorageIntegrationTest") {
    description = "Runs integration tests against local S3-compatible object storage"
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    environment("RUN_SRI_LIVE_TEST", "false")

    useJUnitPlatform {
        includeTags("object-storage-integration")
    }

    shouldRunAfter(tasks.test)
}
