package taboocore.event.world

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.state.BlockState
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 方块破坏事件
 */
class BlockBreakEvent {

    /**
     * 方块破坏前触发
     *
     * @property player 破坏方块的玩家
     * @property block 方块状态
     * @property x 方块 X 坐标
     * @property y 方块 Y 坐标
     * @property z 方块 Z 坐标
     */
    class Pre(
        val player: TabooCorePlayer,
        val block: BlockState,
        val x: Int,
        val y: Int,
        val z: Int
    ) : CancelableInternalEvent()

    /**
     * 方块破坏后触发
     *
     * @property player 破坏方块的玩家
     * @property block 方块状态
     * @property x 方块 X 坐标
     * @property y 方块 Y 坐标
     * @property z 方块 Z 坐标
     */
    class Post(
        val player: TabooCorePlayer,
        val block: BlockState,
        val x: Int,
        val y: Int,
        val z: Int
    ) : InternalEvent()

    companion object {
        /**
         * 方块破坏前触发，返回 true 表示事件被取消
         */
        fun fireBlockBreakPre(player: ServerPlayer, pos: BlockPos, state: BlockState): Boolean {
            val event = Pre(TabooCorePlayer.of(player), state, pos.x, pos.y, pos.z)
            event.call()
            return event.isCancelled
        }

        /**
         * 方块破坏后触发
         */
        fun fireBlockBreakPost(player: ServerPlayer, pos: BlockPos, state: BlockState) {
            Post(TabooCorePlayer.of(player), state, pos.x, pos.y, pos.z).call()
        }
    }
}
