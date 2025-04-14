plugins {
    kotlin("jvm") version "2.0.21"
    java
//    id("antlr")
}

group = "org.example"
version = "1.0-SNAPSHOT"

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
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")

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
//    implementation("org.soot-oss:sootup.java.core:1.3.0")
//    implementation("org.soot-oss:sootup.java.sourcecode:1.3.0")
//    implementation("org.soot-oss:sootup.java.bytecode:1.3.0")
//    implementation("org.soot-oss:sootup.jimple.parser:1.3.0")
//    implementation("org.soot-oss:sootup.callgraph:1.3.0")
//    implementation("org.soot-oss:sootup.analysis:1.3.0")
//    implementation("org.soot-oss:sootup.qilin:1.3.0")

    implementation("org.soot-oss:soot:4.6.0")

//    antlr("org.antlr:antlr4:4.13.0") // Зависимость для ANTLR
//    implementation("org.antlr:antlr4-runtime:4.9.3") // Runtime-библиотека ANTLR
}

//configurations.all {
//    resolutionStrategy {
//        force("org.antlr:antlr4-runtime:4.9.3")
//    }
//}


tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
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
