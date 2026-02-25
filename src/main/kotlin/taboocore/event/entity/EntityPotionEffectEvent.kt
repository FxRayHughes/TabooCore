package taboocore.event.entity

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 实体药水效果变更事件
 */
class EntityPotionEffectEvent {

    /**
     * 药水效果变更动作
     */
    enum class Action {
        /** 新增药水效果 */
        ADDED,
        /** 替换/更新已有药水效果 */
        CHANGED,
        /** 移除单个药水效果 */
        REMOVED,
        /** 清除所有药水效果 */
        CLEARED
    }

    /**
     * 实体药水效果变更前触发
     *
     * @property entity 受影响的实体
     * @property player 受影响的玩家（如果实体是玩家，否则为 null）
     * @property effect 涉及的药水效果实例（CLEARED 时为 null，可修改）
     * @property source 施加效果的实体来源（可能为 null）
     * @property action 变更动作类型
     */
    class Pre(
        val entity: LivingEntity,
        val player: TabooCorePlayer?,
        var effect: MobEffectInstance?,
        val source: Entity?,
        val action: Action
    ) : CancelableInternalEvent()

    /**
     * 实体药水效果变更后触发
     *
     * @property entity 受影响的实体
     * @property player 受影响的玩家（如果实体是玩家，否则为 null）
     * @property effect 涉及的药水效果实例（CLEARED 时为 null）
     * @property source 施加效果的实体来源（可能为 null）
     * @property action 变更动作类型
     */
    class Post(
        val entity: LivingEntity,
        val player: TabooCorePlayer?,
        val effect: MobEffectInstance?,
        val source: Entity?,
        val action: Action
    ) : InternalEvent()

    companion object {
        /**
         * 实体药水效果变更前触发，返回事件对象（可能已被修改），返回 null 表示事件被取消
         */
        fun firePre(entity: LivingEntity, effect: MobEffectInstance?, source: Entity?, action: Action): Pre? {
            val player = if (entity is ServerPlayer) TabooCorePlayer.of(entity) else null
            val event = Pre(entity, player, effect, source, action)
            event.call()
            return if (event.isCancelled) null else event
        }

        /**
         * 实体药水效果变更后触发
         */
        fun firePost(entity: LivingEntity, effect: MobEffectInstance?, source: Entity?, action: Action) {
            val player = if (entity is ServerPlayer) TabooCorePlayer.of(entity) else null
            Post(entity, player, effect, source, action).call()
        }
    }
}
