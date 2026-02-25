package taboocore.event.entity

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.player.Player
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 实体进入恋爱模式事件
 */
class EntityEnterLoveModeEvent {

    /**
     * 实体进入恋爱模式前触发
     *
     * @property entity 进入恋爱模式的实体
     * @property humanTrigger 触发恋爱模式的玩家（可能为 null）
     */
    class Pre(
        val entity: Animal,
        val humanTrigger: taboocore.player.TabooCorePlayer?
    ) : CancelableInternalEvent()

    /**
     * 实体进入恋爱模式后触发
     *
     * @property entity 进入恋爱模式的实体
     * @property humanTrigger 触发恋爱模式的玩家（可能为 null）
     */
    class Post(
        val entity: Animal,
        val humanTrigger: taboocore.player.TabooCorePlayer?
    ) : InternalEvent()

    companion object {
        /**
         * 实体进入恋爱模式前触发，返回 true 表示事件被取消
         */
        fun firePre(entity: Animal, player: Player?): Boolean {
            val proxyPlayer = if (player is ServerPlayer) taboocore.player.TabooCorePlayer.of(player) else null
            val event = Pre(entity, proxyPlayer)
            event.call()
            return event.isCancelled
        }

        /**
         * 实体进入恋爱模式后触发
         */
        fun firePost(entity: Animal, player: Player?) {
            val proxyPlayer = if (player is ServerPlayer) taboocore.player.TabooCorePlayer.of(player) else null
            Post(entity, proxyPlayer).call()
        }
    }
}
