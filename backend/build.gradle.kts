plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

group = "com.soruhavuzu"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

// OpenTelemetry Java agent: uygulamaya derlenmez, JVM'e -javaagent ile takılır (mimari.md §2, §18)
val otelAgent: Configuration by configurations.creating

dependencyManagement {
    imports {
        mavenBom(
            libs.spring.modulith.bom
                .get()
                .toString(),
        )
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation(libs.springdoc.webmvc.ui)
    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.jdbc)
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jdbc")
    implementation("org.springframework.modulith:spring-modulith-observability-api")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
    runtimeOnly("org.springframework.modulith:spring-modulith-observability-core")
    runtimeOnly("org.springframework.modulith:spring-modulith-runtime")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.mockk)
    testImplementation(libs.springmockk)
    testImplementation(libs.archunit.junit5)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    otelAgent(libs.opentelemetry.javaagent)
}

val otelAgentJar by tasks.registering(Copy::class) {
    description = "OpenTelemetry Java agent'ını build/otel/ altına kopyalar (Docker imajı da bunu kullanır)."
    from(otelAgent)
    into(layout.buildDirectory.dir("otel"))
    rename { "opentelemetry-javaagent.jar" }
}

// ./gradlew bootRun -Potel → agent takılı çalışır; lokal'de dışa aktarım kapalıdır, iz kimlikleri log'a yazılır
tasks.bootRun {
    if (project.hasProperty("otel")) {
        dependsOn(otelAgentJar)
        jvmArgs("-javaagent:${layout.buildDirectory.file("otel/opentelemetry-javaagent.jar").get().asFile}")
        environment("OTEL_SERVICE_NAME", "soruhavuzu-api")
        environment("OTEL_TRACES_EXPORTER", "none")
        environment("OTEL_METRICS_EXPORTER", "none")
        environment("OTEL_LOGS_EXPORTER", "none")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
        allWarningsAsErrors = true
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
    parallel = true
}

ktlint {
    version = libs.versions.ktlint.get()
}

// detekt 1.23 kendi Kotlin derleyicisiyle (2.0.21) çalışır; projenin Kotlin sürümünü almasın
configurations.matching { it.name == "detekt" || it.name.startsWith("detekt") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion("2.0.21")
        }
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "22" // detekt 1.23'ün desteklediği en yüksek hedef; yalnızca analiz için
}

tasks.named("check") {
    dependsOn("detektMain") // type-resolution gerektiren yasaklar (!!, Instant.now) yalnızca burada çalışır
}

tasks.bootJar {
    archiveFileName = "soruhavuzu-api.jar"
}
