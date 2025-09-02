plugins {
    kotlin("jvm") version "2.0.0"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.gradleup.nmcp.aggregation").version("1.1.0")
    `maven-publish`
    signing
    publishing
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

            pom {
                name.set("AutoFab")
                description.set("Automated fabrication toolkit for graphics.scenery")
                url.set("https://github.com/scenerygraphics/autofab")
                
                licenses {
                    license {
                        name.set("GNU Lesser General Public License v3.0")
                        url.set("https://www.gnu.org/licenses/lgpl-3.0.html")
                    }
                }
                
                developers {
                    developer {
                        id.set("skalarproduktraum")
                        name.set("Ulrik Günther")
                        email.set("hello@ulrik.is")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/scenerygraphics/autofab.git")
                    developerConnection.set("scm:git:ssh://github.com:scenerygraphics/autofab.git")
                    url.set("https://github.com/scenerygraphics/autofab")
                }
            }
        }
    }
    
    repositories {
        maven {
            name = "sonatype"
            url = if (version.toString().endsWith("SNAPSHOT")) {
                uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            } else {
                uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            }
            credentials {
                username = project.findProperty("sonatypeUsername") as String? ?: System.getenv("SONATYPE_USERNAME")
                password = project.findProperty("sonatypePassword") as String? ?: System.getenv("SONATYPE_PASSWORD")
            }
        }
    }
}

signing {
    //    setRequired({ project.hasProperty("release") })
    useGpgCmd()
    sign(publishing.publications["maven"])
    sign(configurations.archives.get())
}

nmcpAggregation {
    centralPortal {
        username = (properties["sonatypeUsername"] as? String) ?: ""
        password = (properties["sonatypePassword"] as? String) ?: ""
        // publish manually from the portal
        publishingType = "USER_MANAGED"
    }

    // Publish all projects that apply the 'maven-publish' plugin
    publishAllProjectsProbablyBreakingProjectIsolation()
}