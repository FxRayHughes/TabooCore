package taboocore.event.world

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家开始破坏方块事件（左键点击方块开始破坏）
 */
class BlockDamageEvent {

    /**
     * 方块开始被破坏前触发
     *
     * @property player 破坏方块的玩家
     * @property level 所在世界
     * @property pos 方块位置
     * @property block 方块状态
     * @property face 被点击的方块面
     * @property itemInHand 玩家手持物品
     * @property instaBreak 是否瞬间破坏（可修改，设为 true 可强制瞬间破坏）
     */
    class Pre(
        val player: TabooCorePlayer,
        val level: ServerLevel,
        val pos: BlockPos,
        val block: BlockState,
        val face: Direction,
        val itemInHand: ItemStack,
        var instaBreak: Boolean
    ) : CancelableInternalEvent()

    /**
     * 方块开始被破坏后触发
     *
     * @property player 破坏方块的玩家
     * @property level 所在世界
     * @property pos 方块位置
     * @property block 方块状态
     * @property face 被点击的方块面
     * @property itemInHand 玩家手持物品
     * @property instaBreak 是否瞬间破坏（创造模式）
     */
    class Post(
        val player: TabooCorePlayer,
        val level: ServerLevel,
        val pos: BlockPos,
        val block: BlockState,
        val face: Direction,
        val itemInHand: ItemStack,
        val instaBreak: Boolean
    ) : InternalEvent()

    companion object {
        /**
         * 方块开始被破坏前触发，返回事件对象（可读取修改后的 instaBreak），返回 null 表示事件被取消
         */
        fun firePre(
            serverPlayer: ServerPlayer,
            level: ServerLevel,
            pos: BlockPos,
            block: BlockState,
            face: Direction,
            itemInHand: ItemStack,
            instaBreak: Boolean
        ): Pre? {
            val event = Pre(TabooCorePlayer.of(serverPlayer), level, pos, block, face, itemInHand, instaBreak)
            event.call()
            return if (event.isCancelled) null else event
        }

        /**
         * 方块开始被破坏后触发
         */
        fun firePost(
            serverPlayer: ServerPlayer,
            level: ServerLevel,
            pos: BlockPos,
            block: BlockState,
            face: Direction,
            itemInHand: ItemStack,
            instaBreak: Boolean
        ) {
            Post(TabooCorePlayer.of(serverPlayer), level, pos, block, face, itemInHand, instaBreak).call()
        }
    }
}
