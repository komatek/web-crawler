import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    `java-library`
    application
}

// Updated group to match your package structure
group = "com.monzo.crawler"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

application {
    mainClass.set("com.monzo.crawler.CrawlerApplication")
}

dependencies {
    implementation("org.jsoup:jsoup:1.17.2")

    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // Redis client
    implementation("io.lettuce:lettuce-core:6.3.0.RELEASE")

    // Test dependencies
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.12.0")
    testImplementation("com.github.tomakehurst:wiremock-jre8:3.0.1")
    testImplementation("org.assertj:assertj-core:3.26.0")

    // Testcontainers dependencies - FIXED: removed the non-existent "redis" artifact
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.19.8"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:testcontainers")

    // For testing
    implementation("com.google.guava:guava:32.1.2-jre")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<JavaExec> {
    jvmArgs = listOf("--enable-preview")
}


tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs = listOf("--enable-preview")
    testLogging {
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.monzo.crawler.CrawlerApplication"
    }

    // Optionally include dependencies (fat jar) if needed:
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}
