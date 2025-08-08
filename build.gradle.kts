plugins {
    kotlin("jvm") version "2.0.0"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    `maven-publish`
}

group = "graphics.scenery"
version = "0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jmdns:jmdns:3.5.9")
    implementation("org.zeromq:jeromq:0.6.0")
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("org.slf4j:slf4j-simple:2.0.7")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("graphics.scenery.AutofabMainKt")
}

tasks.test {
    useJUnitPlatform()
}

java {
    withJavadocJar()
    withSourcesJar()
}

kotlin {
    jvmToolchain(21)
}
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "graphics.scenery"
            artifactId = "autofab"
            version = "0.1"

            from(components["java"])
        }
    }
}