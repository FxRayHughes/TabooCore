import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    `maven-publish`
}

group = "taboocore"
version = "1.0.0"

repositories {
    maven("https://repo.spongepowered.org/maven")
    maven("https://repo.tabooproject.org/repository/releases")
    maven("https://libraries.minecraft.net")
    mavenLocal()
    mavenCentral()
}

// 下载原版服务端核心
apply(from = "downloader.gradle.kts")

val minecraftServerJar: File by extra

dependencies {
    // 打包进 agent fat JAR
    implementation(libs.mixin)
    implementation(libs.gson)
    implementation(libs.guava)
    implementation(libs.coroutines.core)
    implementation(libs.jline)
    implementation(libs.asm)
    implementation(libs.asm.util)
    implementation(libs.asm.commons)
    implementation(kotlin("reflect"))
    // Reflex：打包并 relocate 到 taboolib.library.reflex（与 TabooLib 运行时一致）
    compileOnly(libs.reflex)
    compileOnly(libs.reflex.analyser)

    // 编译时依赖（运行时由 TabooLibLoader 动态加载，不打包进 JAR）
    compileOnly(libs.asm)
    compileOnly(libs.asm.tree)
    compileOnly(libs.taboolib.common)
    compileOnly(libs.taboolib.common.platform.api)
    compileOnly(libs.taboolib.common.util)

    // 服务端依赖
    compileOnly(files(minecraftServerJar)) // 原版服务端（不打包，运行时由服务端提供）
    compileOnly(libs.mojang.datafixerupper)
    compileOnly(libs.mojang.logging)
    compileOnly(libs.mojang.brigadier)
    compileOnly(libs.log4j.api)
    compileOnly(libs.log4j.core)
    compileOnly(libs.netty.all)
}

// 资源处理
tasks.processResources {
    filesMatching("/*.json") {
        expand(
            "modules" to listOf("common-reflex", "basic-configuration", "minecraft-command-helper")
                .joinToString(prefix = "[", postfix = "]", transform = { "\"$it\"" }),
            "taboolibVersion" to libs.versions.taboolib.get(),
            "kotlinVersion" to libs.versions.kotlin.get(),
            "coroutinesVersion" to libs.versions.coroutines.get(),
        )
    }
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks.named("shadowJar"))
        }
    }
}
