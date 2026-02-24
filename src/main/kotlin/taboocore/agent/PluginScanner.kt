package taboocore.agent

import java.io.File
import java.util.jar.JarFile

data class PluginInfo(val jar: File, val mixinConfigs: List<String>)

/**
 * 扫描 plugins/ 目录，读取每个插件 JAR 的 MANIFEST.MF
 * 插件需在 MANIFEST.MF 中声明：
 *   TabooLib-Mixins: myplugin.mixins.json
 * 多个配置用逗号分隔
 */
object PluginScanner {

    fun scan(): List<PluginInfo> {
        val pluginsDir = File("plugins")
        if (!pluginsDir.exists()) return emptyList()

        return pluginsDir.listFiles { f -> f.extension == "jar" }
            ?.mapNotNull { jar ->
                runCatching {
                    JarFile(jar).use { jf ->
                        val value = jf.manifest?.mainAttributes?.getValue("TabooLib-Mixins")
                        val configs = value?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                        PluginInfo(jar, configs)
                    }
                }.getOrNull()
            } ?: emptyList()
    }
}
