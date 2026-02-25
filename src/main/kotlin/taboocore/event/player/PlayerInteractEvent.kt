package taboocore.event.player

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家交互事件
 */
class PlayerInteractEvent {

    /**
     * 交互类型
     */
    enum class Action {
        /** 左键点击方块 */
        LEFT_CLICK_BLOCK,
        /** 右键点击方块 */
        RIGHT_CLICK_BLOCK,
        /** 左键点击空气 */
        LEFT_CLICK_AIR,
        /** 右键点击空气 */
        RIGHT_CLICK_AIR,
        /** 物理交互（踩压力板、踩耕地等） */
        PHYSICAL;

        /** 是否为左键点击 */
        fun isLeftClick(): Boolean = this == LEFT_CLICK_BLOCK || this == LEFT_CLICK_AIR

        /** 是否为右键点击 */
        fun isRightClick(): Boolean = this == RIGHT_CLICK_BLOCK || this == RIGHT_CLICK_AIR
    }

    /**
     * 玩家交互前触发
     *
     * @property player 交互的玩家
     * @property action 交互类型
     * @property item 手持的物品
     * @property hand 使用的手（PHYSICAL 时为 null）
     * @property block 点击的方块状态（点击空气时为 null）
     * @property blockPos 点击的方块坐标（点击空气时为 null）
     * @property blockFace 点击的方块面（点击空气时为 null）
     */
    class Pre(
        val player: TabooCorePlayer,
        val action: Action,
        val item: ItemStack,
        val hand: InteractionHand?,
        val block: BlockState?,
        val blockPos: BlockPos?,
        val blockFace: Direction?
    ) : CancelableInternalEvent()

    /**
     * 玩家交互后触发
     *
     * @property player 交互的玩家
     * @property action 交互类型
     * @property item 手持的物品
     * @property hand 使用的手（PHYSICAL 时为 null）
     * @property block 点击的方块状态（点击空气时为 null）
     * @property blockPos 点击的方块坐标（点击空气时为 null）
     * @property blockFace 点击的方块面（点击空气时为 null）
     */
    class Post(
        val player: TabooCorePlayer,
        val action: Action,
        val item: ItemStack,
        val hand: InteractionHand?,
        val block: BlockState?,
        val blockPos: BlockPos?,
        val blockFace: Direction?
    ) : InternalEvent()

    companion object {
        /**
         * 玩家交互前触发，返回 true 表示事件被取消
         */
        fun firePre(
            player: ServerPlayer,
            action: Action,
            item: ItemStack,
            hand: InteractionHand?,
            block: BlockState?,
            blockPos: BlockPos?,
            blockFace: Direction?
        ): Boolean {
            val event = Pre(TabooCorePlayer.of(player), action, item, hand, block, blockPos, blockFace)
            event.call()
            return event.isCancelled
        }

        /**
         * 玩家交互后触发
         */
        fun firePost(
            player: ServerPlayer,
            action: Action,
            item: ItemStack,
            hand: InteractionHand?,
            block: BlockState?,
            blockPos: BlockPos?,
            blockFace: Direction?
        ) {
            Post(TabooCorePlayer.of(player), action, item, hand, block, blockPos, blockFace).call()
        }
    }
}
