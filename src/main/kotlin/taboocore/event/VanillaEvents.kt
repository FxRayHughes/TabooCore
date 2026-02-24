package taboocore.event

import taboolib.common.event.InternalEvent
import taboolib.common.platform.ProxyPlayer

class PlayerJoinEvent(val player: ProxyPlayer) : InternalEvent()
class PlayerQuitEvent(val player: ProxyPlayer) : InternalEvent()
class ServerTickEvent(val tickCount: Int) : InternalEvent()
