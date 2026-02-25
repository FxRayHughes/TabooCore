package taboocore.packet

import net.minecraft.network.protocol.Packet
import net.minecraft.server.level.ServerPlayer
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 服务端发送数据包事件
 */
class PacketSendEvent {

    /**
     * 发送数据包前触发
     *
     * @property player 接收数据包的玩家
     * @property packet 被发送的数据包
     */
    class Pre(
        val player: TabooCorePlayer,
        val packet: Packet<*>
    ) : CancelableInternalEvent()

    /**
     * 发送数据包后触发
     *
     * @property player 接收数据包的玩家
     * @property packet 被发送的数据包
     */
    class Post(
        val player: TabooCorePlayer,
        val packet: Packet<*>
    ) : InternalEvent()

    companion object {
        /**
         * 发送数据包前触发，返回 true 表示事件被取消
         */
        fun firePre(player: ServerPlayer, packet: Packet<*>): Boolean {
            val event = Pre(TabooCorePlayer.of(player), packet)
            event.call()
            if (event.isCancelled) return true
            // 触发 PacketManager 注册的监听器
            PacketManager.fireSendListeners(event)
            return event.isCancelled
        }

        /**
         * 发送数据包后触发
         */
        fun firePost(player: ServerPlayer, packet: Packet<*>) {
            Post(TabooCorePlayer.of(player), packet).call()
        }
    }
}
