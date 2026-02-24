import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.10"
    id("com.gradleup.shadow") version "9.3.1"
}

group = "taboocore"
version = "1.0.0"

repositories {
    maven("https://repo.spongepowered.org/maven")
    maven("https://repo.tabooproject.org/repository/releases")
    mavenLocal()
    mavenCentral()
}

dependencies {
    // 打包进 agent fat JAR
    implementation("org.spongepowered:mixin:0.8.7")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.google.guava:guava:33.4.0-jre")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation(kotlin("reflect"))

    // 编译时依赖（运行时由 TabooLibLoader 动态加载，不打包进 JAR）
    compileOnly("org.ow2.asm:asm:9.8")
    compileOnly("org.ow2.asm:asm-tree:9.8")
    compileOnly("io.izzel.taboolib:common:6.2.4-local-dev")
    compileOnly("io.izzel.taboolib:common-platform-api:6.2.4-local-dev")
    compileOnly("io.izzel.taboolib:common-util:6.2.4-local-dev")

    // 原版服务端（不打包，运行时由服务端提供）
    compileOnly(fileTree("libs"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("")
//    relocate("org.spongepowered.asm", "taboocore.library.mixin")
//    relocate("kotlinx.coroutines", "taboocore.library.coroutines")
    manifest {
        attributes(
            "Premain-Class" to "taboocore.agent.TabooCoreAgent",
            "Agent-Class" to "taboocore.agent.TabooCoreAgent",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true"
        )
    }
}

tasks.build {
    dependsOn("shadowJar")
}
