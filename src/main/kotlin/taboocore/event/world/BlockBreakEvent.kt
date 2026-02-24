package taboocore.event.world

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.state.BlockState
import taboocore.player.Player
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

class BlockBreakEvent {

    class Pre(
        val player: Player,
        val blockId: String,
        val x: Int,
        val y: Int,
        val z: Int
    ) : CancelableInternalEvent()

    class Post(
        val player: Player,
        val blockId: String,
        val x: Int,
        val y: Int,
        val z: Int
    ) : InternalEvent()

    companion object {
        /**
         * 方块破坏前触发，返回 true 表示事件被取消
         */
        fun fireBlockBreakPre(player: ServerPlayer, pos: BlockPos, state: BlockState): Boolean {
            val blockId = state.block.descriptionId
            val event = Pre(Player.of(player), blockId, pos.x, pos.y, pos.z)
            event.call()
            return event.isCancelled
        }

        /**
         * 方块破坏后触发
         */
        fun fireBlockBreakPost(player: ServerPlayer, pos: BlockPos, blockId: String) {
            Post(Player.of(player), blockId, pos.x, pos.y, pos.z).call()
        }
    }
}
