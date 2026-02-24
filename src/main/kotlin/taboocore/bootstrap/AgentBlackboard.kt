package taboocore.bootstrap

import org.spongepowered.asm.service.IGlobalPropertyService
import org.spongepowered.asm.service.IPropertyKey

class AgentBlackboard : IGlobalPropertyService {

    private val properties = mutableMapOf<String, Any?>()

    private class Key(val name: String) : IPropertyKey

    override fun resolveKey(name: String): IPropertyKey = Key(name)

    @Suppress("UNCHECKED_CAST")
    override fun <T> getProperty(key: IPropertyKey): T? = properties[(key as Key).name] as T?

    @Suppress("UNCHECKED_CAST")
    override fun <T> getProperty(key: IPropertyKey, default: T): T =
        (properties[(key as Key).name] as? T) ?: default

    override fun setProperty(key: IPropertyKey, value: Any?) {
        properties[(key as Key).name] = value
    }

    override fun getPropertyString(key: IPropertyKey, default: String?): String? =
        getProperty(key, default)
}
