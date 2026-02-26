import java.security.MessageDigest
import java.util.zip.ZipFile

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val minecraftVersion = catalog.findVersion("minecraft").get().requiredVersion
val cacheDir = file(".gradle/minecraft").apply { mkdirs() }
val serverFile = File(cacheDir, "server-$minecraftVersion.jar")

if (!serverFile.exists()) {
    logger.lifecycle("Resolving Minecraft $minecraftVersion server...")

    // 1. 下载版本清单
    val manifestUrl = uri("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json").toURL()
    @Suppress("UNCHECKED_CAST")
    val manifest = groovy.json.JsonSlurper().parse(manifestUrl) as Map<String, Any>
    @Suppress("UNCHECKED_CAST")
    val versions = manifest["versions"] as List<Map<String, Any>>
    val versionEntry = versions.find { it["id"] == minecraftVersion }
        ?: error("Minecraft version '$minecraftVersion' not found in version manifest")
    val versionJsonUrl = versionEntry["url"] as String

    // 2. 下载版本 JSON，获取 server 下载地址
    @Suppress("UNCHECKED_CAST")
    val versionJson = groovy.json.JsonSlurper().parse(uri(versionJsonUrl).toURL()) as Map<String, Any>
    @Suppress("UNCHECKED_CAST")
    val serverDownload = (versionJson["downloads"] as Map<String, Any>)["server"] as Map<String, Any>
    val serverUrl = serverDownload["url"] as String
    val expectedSha1 = serverDownload["sha1"] as String

    // 3. 下载 bundled server.jar（带缓存和 SHA1 校验）
    val bundledJar = File(cacheDir, "server-$minecraftVersion-bundled.jar")
    if (!bundledJar.exists() || sha1(bundledJar) != expectedSha1) {
        logger.lifecycle("Downloading server.jar from Mojang...")
        uri(serverUrl).toURL().openStream().use { input ->
            bundledJar.outputStream().use { output -> input.copyTo(output) }
        }
        val actualSha1 = sha1(bundledJar)
        check(actualSha1 == expectedSha1) {
            bundledJar.delete()
            "SHA1 mismatch: expected $expectedSha1, got $actualSha1"
        }
        logger.lifecycle("Downloaded and verified server.jar (${bundledJar.length() / 1024 / 1024}MB)")
    } else {
        logger.lifecycle("Using cached server.jar")
    }

    // 4. 从 bundled JAR 中提取实际的 server JAR
    ZipFile(bundledJar).use { zip ->
        val versionsListEntry = zip.getEntry("META-INF/versions.list")
            ?: error("META-INF/versions.list not found in bundled server.jar")
        val versionsListContent = zip.getInputStream(versionsListEntry).bufferedReader().readText().trim()

        // versions.list 格式: <hash>\t<id>\t<path> (每行一个)
        val line = versionsListContent.lines().first()
        val parts = line.split("\t")
        check(parts.size == 3) { "Unexpected versions.list format: $line" }
        val path = parts[2]

        val serverEntry = zip.getEntry("META-INF/versions/$path")
            ?: error("Server JAR entry META-INF/versions/$path not found in bundled JAR")

        zip.getInputStream(serverEntry).use { input ->
            serverFile.outputStream().use { output -> input.copyTo(output) }
        }
        logger.lifecycle("Extracted ${serverFile.name} (${serverFile.length() / 1024 / 1024}MB)")
    }
}

fun sha1(file: File): String {
    val digest = MessageDigest.getInstance("SHA-1")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { b -> "%02x".format(b) }
}

extra["minecraftServerJar"] = serverFile
