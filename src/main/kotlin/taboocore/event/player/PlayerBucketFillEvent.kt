package taboocore.event.player

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家使用桶装取液体事件
 */
class PlayerBucketFillEvent {

    /**
     * 玩家使用桶装取液体前触发
     *
     * @property player 装取液体的玩家
     * @property hand 使用的手
     * @property blockPos 液体方块位置
     * @property block 液体方块状态
     * @property item 使用的桶物品（可修改）
     */
    class Pre(
        val player: TabooCorePlayer,
        val hand: InteractionHand,
        val blockPos: BlockPos,
        val block: BlockState,
        var item: ItemStack
    ) : CancelableInternalEvent()

    /**
     * 玩家使用桶装取液体后触发
     *
     * @property player 装取液体的玩家
     * @property hand 使用的手
     * @property blockPos 液体方块位置
     * @property block 液体方块状态
     * @property item 使用的桶物品
     */
    class Post(
        val player: TabooCorePlayer,
        val hand: InteractionHand,
        val blockPos: BlockPos,
        val block: BlockState,
        val item: ItemStack
    ) : InternalEvent()

    companion object {
        /**
         * 玩家使用桶装取液体前触发，返回事件对象，null 表示事件被取消
         */
        fun firePre(player: ServerPlayer, hand: InteractionHand, blockPos: BlockPos, block: BlockState, item: ItemStack): Pre? {
            val event = Pre(TabooCorePlayer.of(player), hand, blockPos, block, item)
            event.call()
            return if (event.isCancelled) null else event
        }

        /**
         * 玩家使用桶装取液体后触发
         */
        fun firePost(player: ServerPlayer, hand: InteractionHand, blockPos: BlockPos, block: BlockState, item: ItemStack) {
            Post(TabooCorePlayer.of(player), hand, blockPos, block, item).call()
        }
    }
}
