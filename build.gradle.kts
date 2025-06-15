plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("kapt") version "2.0.21"
    java
    application
}

group = "JvmPerfomanceFuzzing"
version = "1.0"

application {
    mainClass.set("MainKt")
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.0.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.+")

    implementation("org.openjdk.jmh:jmh-core:1.37")
    kapt("org.openjdk.jmh:jmh-generator-annprocess:1.37")

    // ASM
    implementation("org.ow2.asm:asm:9.5")
    implementation("org.ow2.asm:asm-commons:9.5")
    implementation("org.ow2.asm:asm-tree:9.5")
    implementation("org.ow2.asm:asm-analysis:9.5")
    implementation("org.ow2.asm:asm-util:9.5")

    // Sooutup
    implementation("org.soot-oss:sootup.core:1.3.0")
    implementation("org.soot-oss:sootup.core:1.3.0")
    implementation("org.soot-oss:sootup.java.core:1.3.0")
    implementation("org.soot-oss:sootup.jimple.parser:1.3.0")
    implementation("org.soot-oss:sootup.java.bytecode:1.3.0")

    implementation("org.soot-oss:soot:4.6.0")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "MainKt"
        )
    }

    // Исключаем файлы подписей при создании fat JAR
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.MF")

    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map {
            zipTree(it).matching {
                // Исключаем файлы подписей из зависимостей
                exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.MF")
            }
        }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register("buildDockerImage") {
    group = "docker"
    description = "Собирает Docker-образ для фаззинга."

    doLast {
        exec {
            commandLine("docker", "build", "-t", "fuzzer-framework", ".")
        }
    }
}

tasks.register("runDockerContainer") {
    group = "docker"
    description = "Запускает Docker-контейнер с маунтом папки для аномалий."

    doLast {
        val anomaliesDir = "$buildDir/anomalies"
        val containerName = "fuzzer-container"

        // Создаем папку для аномалий, если её ещё нет
        mkdir(anomaliesDir)

        // Запускаем контейнер
        exec {
            commandLine(
                "docker", "run", "--rm", "-it",
                "-v", "$anomaliesDir:/app/anomalies", // Маунт папки
                "--name", containerName,
                "fuzzer-framework"
            )
        }
    }
}

tasks.register("stopDockerContainer") {
    group = "docker"
    description = "Останавливает Docker-контейнер с фаззером."

    doLast {
        exec {
            commandLine("docker", "stop", "fuzzer-container")
        }
    }
}
