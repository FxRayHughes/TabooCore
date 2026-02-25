package taboocore.event.player

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家传送事件
 */
class PlayerTeleportEvent {

    /**
     * 玩家传送前触发
     *
     * @property player 传送的玩家
     * @property fromLevel 传送前所在的世界
     * @property fromX 传送前 X 坐标
     * @property fromY 传送前 Y 坐标
     * @property fromZ 传送前 Z 坐标
     * @property toLevel 传送目标世界
     * @property toX 传送目标 X 坐标
     * @property toY 传送目标 Y 坐标
     * @property toZ 传送目标 Z 坐标
     */
    class Pre(
        val player: TabooCorePlayer,
        val fromLevel: ServerLevel,
        val fromX: Double,
        val fromY: Double,
        val fromZ: Double,
        var toLevel: ServerLevel,
        var toX: Double,
        var toY: Double,
        var toZ: Double
    ) : CancelableInternalEvent()

    /**
     * 玩家传送后触发
     *
     * @property player 传送的玩家
     * @property fromLevel 传送前所在的世界
     * @property fromX 传送前 X 坐标
     * @property fromY 传送前 Y 坐标
     * @property fromZ 传送前 Z 坐标
     * @property toLevel 传送目标世界
     * @property toX 传送目标 X 坐标
     * @property toY 传送目标 Y 坐标
     * @property toZ 传送目标 Z 坐标
     */
    class Post(
        val player: TabooCorePlayer,
        val fromLevel: ServerLevel,
        val fromX: Double,
        val fromY: Double,
        val fromZ: Double,
        val toLevel: ServerLevel,
        val toX: Double,
        val toY: Double,
        val toZ: Double
    ) : InternalEvent()

    companion object {
        /**
         * 玩家传送前触发，返回事件对象，null 表示事件被取消
         */
        fun firePre(
            player: ServerPlayer,
            fromLevel: ServerLevel, fromX: Double, fromY: Double, fromZ: Double,
            toLevel: ServerLevel, toX: Double, toY: Double, toZ: Double
        ): Pre? {
            val event = Pre(TabooCorePlayer.of(player), fromLevel, fromX, fromY, fromZ, toLevel, toX, toY, toZ)
            event.call()
            return if (event.isCancelled) null else event
        }

        /**
         * 玩家传送后触发
         */
        fun firePost(
            player: ServerPlayer,
            fromLevel: ServerLevel, fromX: Double, fromY: Double, fromZ: Double,
            toLevel: ServerLevel, toX: Double, toY: Double, toZ: Double
        ) {
            Post(TabooCorePlayer.of(player), fromLevel, fromX, fromY, fromZ, toLevel, toX, toY, toZ).call()
        }
    }
}
