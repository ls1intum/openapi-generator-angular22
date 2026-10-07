import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.plugins.signing.Sign

plugins {
    java
    `java-library`
    // Publishes signed artifacts to the Maven Central (Sonatype) Portal. Replaces the manual
    // maven-publish + signing setup: it wires the sources/javadoc jars, the POM, GPG signing, and the
    // Central Portal upload. See RELEASING.md for the required credentials and the release procedure.
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "de.tum.cit.aet"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val openapiGeneratorCli by configurations.creating

dependencies {
    val openapiGeneratorVersion = "7.21.0"

    // OpenAPI Generator core dependency
    implementation("org.openapitools:openapi-generator:$openapiGeneratorVersion")
    openapiGeneratorCli("org.openapitools:openapi-generator-cli:$openapiGeneratorVersion")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.register<JavaExec>("generateExample") {
    dependsOn(tasks.named("jar"))
    doFirst {
        delete("build/generated/example")
    }
    classpath = openapiGeneratorCli + files(tasks.named<Jar>("jar").get().archiveFile.get().asFile)
    mainClass.set("org.openapitools.codegen.OpenAPIGenerator")
    args(
        "generate",
        "-g", "angular22",
        "-i", "example/example-openapi.yaml",
        "-o", "build/generated/example"
    )
}

val installTypeScript by tasks.registering(Exec::class) {
    val typescriptDir = layout.projectDirectory.dir("src/test/typescript")
    workingDir(typescriptDir)
    commandLine("npm", "ci", "--no-audit", "--no-fund")
    inputs.file(typescriptDir.file("package-lock.json"))
    outputs.dir(typescriptDir.dir("node_modules"))
}

tasks.test {
    useJUnitPlatform()
    dependsOn(installTypeScript)
}

// Register the generator with OpenAPI Generator's SPI
tasks.withType<Jar> {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }
}

mavenPublishing {
    // Upload to the Maven Central Portal (central.sonatype.com) and release automatically once the
    // staged deployment validates. Consumers then resolve the artifact from plain mavenCentral() with
    // no authentication.
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    // All artifacts must be GPG-signed for Maven Central. The signing key is provided via Gradle
    // properties / environment variables in CI (see RELEASING.md); signing is skipped for
    // publishToMavenLocal, so building from source needs no key.
    signAllPublications()

    coordinates(group.toString(), "openapi-generator-angular22", version.toString())

    pom {
        name.set("OpenAPI Generator Angular 22")
        description.set("Custom OpenAPI Generator for modern Angular 22 with httpResource and signals")
        url.set("https://github.com/ls1intum/openapi-generator-angular22")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("ls1intum")
                name.set("LS1 TUM")
                email.set("krusche@tum.de")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/ls1intum/openapi-generator-angular22.git")
            developerConnection.set("scm:git:ssh://github.com/ls1intum/openapi-generator-angular22.git")
            url.set("https://github.com/ls1intum/openapi-generator-angular22")
        }
    }
}

// Only sign when a signing key is configured — i.e. during a Central Portal release in CI, where the
// SIGNING_KEY secret is provided as ORG_GRADLE_PROJECT_signingInMemoryKey. Building from source via
// `./gradlew publishToMavenLocal` (the fallback consumers use to regenerate the client) needs no GPG
// key: the local repository does not require signatures. The flag is read at configuration time so it
// stays compatible with the configuration cache.
val signingKeyPresent = providers.gradleProperty("signingInMemoryKey").isPresent
tasks.withType<Sign>().configureEach {
    onlyIf { signingKeyPresent }
}
