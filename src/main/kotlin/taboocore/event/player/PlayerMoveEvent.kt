package taboocore.event.player

import net.minecraft.server.level.ServerPlayer
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家移动事件
 */
class PlayerMoveEvent {

    /**
     * 玩家移动前触发
     *
     * @property player 移动的玩家
     * @property fromX 移动前 X 坐标
     * @property fromY 移动前 Y 坐标
     * @property fromZ 移动前 Z 坐标
     * @property toX 移动后 X 坐标
     * @property toY 移动后 Y 坐标
     * @property toZ 移动后 Z 坐标
     */
    class Pre(
        val player: TabooCorePlayer,
        val fromX: Double,
        val fromY: Double,
        val fromZ: Double,
        val toX: Double,
        val toY: Double,
        val toZ: Double
    ) : CancelableInternalEvent()

    /**
     * 玩家移动后触发
     *
     * @property player 移动的玩家
     * @property fromX 移动前 X 坐标
     * @property fromY 移动前 Y 坐标
     * @property fromZ 移动前 Z 坐标
     * @property toX 移动后 X 坐标
     * @property toY 移动后 Y 坐标
     * @property toZ 移动后 Z 坐标
     */
    class Post(
        val player: TabooCorePlayer,
        val fromX: Double,
        val fromY: Double,
        val fromZ: Double,
        val toX: Double,
        val toY: Double,
        val toZ: Double
    ) : InternalEvent()

    companion object {
        /**
         * 玩家移动前触发，返回 true 表示事件被取消
         */
        fun firePre(
            player: ServerPlayer,
            fromX: Double, fromY: Double, fromZ: Double,
            toX: Double, toY: Double, toZ: Double
        ): Boolean {
            val event = Pre(TabooCorePlayer.of(player), fromX, fromY, fromZ, toX, toY, toZ)
            event.call()
            return event.isCancelled
        }

        /**
         * 玩家移动后触发
         */
        fun firePost(
            player: ServerPlayer,
            fromX: Double, fromY: Double, fromZ: Double,
            toX: Double, toY: Double, toZ: Double
        ) {
            Post(TabooCorePlayer.of(player), fromX, fromY, fromZ, toX, toY, toZ).call()
        }
    }
}
