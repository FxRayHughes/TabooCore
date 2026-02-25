package taboocore.event.player

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.state.BlockState
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家上床睡觉事件
 */
class PlayerBedEnterEvent {

    /**
     * 玩家上床前触发
     *
     * @property player 上床的玩家
     * @property bed 床的方块状态
     * @property bedPos 床的位置
     */
    class Pre(
        val player: TabooCorePlayer,
        val bed: BlockState,
        val bedPos: BlockPos
    ) : CancelableInternalEvent()

    /**
     * 玩家上床后触发
     *
     * @property player 上床的玩家
     * @property bed 床的方块状态
     * @property bedPos 床的位置
     */
    class Post(
        val player: TabooCorePlayer,
        val bed: BlockState,
        val bedPos: BlockPos
    ) : InternalEvent()

    companion object {
        /**
         * 玩家上床前触发，返回事件对象，null 表示事件被取消
         */
        fun firePre(player: ServerPlayer, bed: BlockState, bedPos: BlockPos): Pre? {
            val event = Pre(TabooCorePlayer.of(player), bed, bedPos)
            event.call()
            return if (event.isCancelled) null else event
        }

        /**
         * 玩家上床后触发
         */
        fun firePost(player: ServerPlayer, bed: BlockState, bedPos: BlockPos) {
            Post(TabooCorePlayer.of(player), bed, bedPos).call()
        }
    }
}
