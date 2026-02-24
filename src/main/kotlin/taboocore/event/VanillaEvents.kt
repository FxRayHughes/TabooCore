package taboocore.event

import net.minecraft.commands.CommandSourceStack
import taboolib.common.event.InternalEvent
import taboolib.common.platform.ProxyPlayer

class VanillaPlayerJoinEvent(val player: ProxyPlayer) : InternalEvent()
class VanillaPlayerQuitEvent(val player: ProxyPlayer) : InternalEvent()
class VanillaServerTickEvent(val tickCount: Int) : InternalEvent()
class VanillaCommandRegisterEvent(val dispatcher: Any) : InternalEvent()
