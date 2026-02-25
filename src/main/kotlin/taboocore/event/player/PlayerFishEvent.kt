package taboocore.event.player

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.projectile.FishingHook
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家钓鱼事件
 */
class PlayerFishEvent {

    /**
     * 钓鱼状态枚举
     */
    enum class State {
        /** 正在钓鱼（抛出鱼钩） */
        FISHING,
        /** 钓到鱼 */
        CAUGHT_FISH,
        /** 钓到实体 */
        CAUGHT_ENTITY,
        /** 鱼钩落在地面上 */
        IN_GROUND,
        /** 未钓到任何东西 */
        FAILED_ATTEMPT
    }

    /**
     * 玩家钓鱼前触发
     *
     * @property player 钓鱼的玩家
     * @property hook 鱼钩实体
     * @property state 钓鱼状态
     */
    class Pre(
        val player: TabooCorePlayer,
        val hook: FishingHook,
        val state: State
    ) : CancelableInternalEvent()

    /**
     * 玩家钓鱼后触发
     *
     * @property player 钓鱼的玩家
     * @property hook 鱼钩实体
     * @property state 钓鱼状态
     */
    class Post(
        val player: TabooCorePlayer,
        val hook: FishingHook,
        val state: State
    ) : InternalEvent()

    companion object {
        /**
         * 玩家钓鱼前触发，返回 true 表示事件被取消
         */
        fun firePre(player: ServerPlayer, hook: FishingHook, state: State): Boolean {
            val event = Pre(TabooCorePlayer.of(player), hook, state)
            event.call()
            return event.isCancelled
        }

        /**
         * 玩家钓鱼后触发
         */
        fun firePost(player: ServerPlayer, hook: FishingHook, state: State) {
            Post(TabooCorePlayer.of(player), hook, state).call()
        }
    }
}
