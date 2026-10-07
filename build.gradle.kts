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
    useJUnitPlatform {
        excludeTags("object-storage-integration")
    }
}

tasks.register<Test>("objectStorageIntegrationTest") {
    description = "Runs integration tests against local S3-compatible object storage"
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("object-storage-integration")
    }

    shouldRunAfter(tasks.test)
}