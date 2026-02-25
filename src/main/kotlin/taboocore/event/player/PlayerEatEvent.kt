package taboocore.event.player

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import taboocore.player.Player
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家吃/喝东西事件
 *
 * 当玩家完成使用食物（EAT）或药水/蜂蜜瓶等（DRINK）时触发。
 * 与 [PlayerItemConsumeEvent] 的区别：本事件仅针对食物和饮料，并携带 [EatType] 类型区分。
 *
 * 功能：
 * - 取消事件（[Pre.isCancelled] = true）→ 阻止消耗，玩家不吃/喝，物品保留
 * - 修改 [Pre.item] → 替换实际被消耗的物品（修改会反映到服务端 useItem，影响实际效果）
 */
class PlayerEatEvent {

    /**
     * 消耗类型
     */
    enum class EatType {
        /** 吃食物（面包、肉类等） */
        EAT,
        /** 喝药水、蜂蜜瓶、牛奶等 */
        DRINK
    }

    /**
     * 玩家吃/喝前触发（可取消）
     *
     * @property player 执行操作的玩家
     * @property item 被消耗的物品（可替换 — 修改此字段将影响实际消耗的物品和效果）
     * @property eatType 操作类型（EAT 或 DRINK）
     */
    class Pre(
        val player: Player,
        var item: ItemStack,
        val eatType: EatType
    ) : CancelableInternalEvent()

    /**
     * 玩家吃/喝后触发
     *
     * @property player 执行操作的玩家
     * @property item 实际被消耗的物品（经过 Pre 事件处理后的最终物品）
     * @property eatType 操作类型（EAT 或 DRINK）
     */
    class Post(
        val player: Player,
        val item: ItemStack,
        val eatType: EatType
    ) : InternalEvent()

    companion object {
        /**
         * 玩家吃/喝前触发，返回事件对象，null 表示事件被取消
         */
        fun firePre(player: ServerPlayer, item: ItemStack, eatType: EatType): Pre? {
            val event = Pre(Player.of(player), item, eatType)
            event.call()
            return if (event.isCancelled) null else event
        }

        /**
         * 玩家吃/喝后触发
         */
        fun firePost(player: ServerPlayer, item: ItemStack, eatType: EatType) {
            Post(Player.of(player), item, eatType).call()
        }
    }
}
