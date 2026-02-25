package taboocore.mixin

import net.minecraft.core.Holder
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemUseAnimation
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import taboocore.event.entity.EntityDamageEvent
import taboocore.event.entity.EntityDeathEvent
import taboocore.event.entity.EntityKnockbackEvent
import taboocore.event.entity.EntityPotionEffectEvent
import taboocore.event.entity.EntityRegainHealthEvent
import taboocore.event.entity.EntityResurrectEvent
import taboocore.event.player.PlayerEatEvent
import taboocore.event.player.PlayerItemConsumeEvent

@Mixin(LivingEntity::class)
abstract class MixinLivingEntity {

    @Shadow
    abstract fun getItemInHand(hand: InteractionHand): net.minecraft.world.item.ItemStack

    @Shadow
    abstract fun getActiveEffectsMap(): Map<Holder<MobEffect>, MobEffectInstance>

    @Shadow
    abstract fun setHealth(health: Float)

    @Shadow
    abstract fun getHealth(): Float

    @Shadow
    lateinit var useItem: net.minecraft.world.item.ItemStack

    @Unique
    private var cachedDamageSource: DamageSource? = null

    @Unique
    private var cachedDamage: Float = 0f

    @Unique
    private var knockbackReentry: Boolean = false

    // ============ EntityDamageEvent ============

    @Inject(method = ["hurtServer"], at = [At("HEAD")], cancellable = true)
    private fun onHurtServerPre(level: ServerLevel, source: DamageSource, damage: Float, cir: CallbackInfoReturnable<Boolean>) {
        cachedDamageSource = source
        cachedDamage = damage
        if (EntityDamageEvent.firePre(this as LivingEntity, source, damage)) {
            cachedDamageSource = null
            cir.returnValue = false
        }
    }

    @Inject(method = ["hurtServer"], at = [At("RETURN")])
    private fun onHurtServerPost(level: ServerLevel, source: DamageSource, damage: Float, cir: CallbackInfoReturnable<Boolean>) {
        val src = cachedDamageSource ?: return
        cachedDamageSource = null
        if (cir.returnValue == true) {
            EntityDamageEvent.firePost(this as LivingEntity, src, cachedDamage)
        }
    }

    // ============ EntityDeathEvent ============

    @Inject(method = ["die"], at = [At("HEAD")], cancellable = true)
    private fun onDiePre(source: DamageSource, ci: CallbackInfo) {
        if (EntityDeathEvent.firePre(this as LivingEntity, source)) {
            ci.cancel()
        }
    }

    @Inject(method = ["die"], at = [At("TAIL")])
    private fun onDiePost(source: DamageSource, ci: CallbackInfo) {
        EntityDeathEvent.firePost(this as LivingEntity, source)
    }

    // ============ EntityRegainHealthEvent ============

    @Inject(method = ["heal"], at = [At("HEAD")], cancellable = true)
    private fun onHealPre(heal: Float, ci: CallbackInfo) {
        val event = EntityRegainHealthEvent.firePre(this as LivingEntity, heal) ?: run { ci.cancel(); return }
        // 如果事件处理器修改了恢复量，手动应用修改后的值
        if (event.amount != heal) {
            val health = getHealth()
            if (health > 0.0f) {
                setHealth(health + event.amount)
            }
            ci.cancel()
        }
    }

    @Inject(method = ["heal"], at = [At("TAIL")])
    private fun onHealPost(heal: Float, ci: CallbackInfo) {
        EntityRegainHealthEvent.firePost(this as LivingEntity, heal)
    }

    // ============ EntityPotionEffectEvent (addEffect) ============

    @Inject(method = ["addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"], at = [At("HEAD")], cancellable = true)
    private fun onAddEffectPre(newEffect: MobEffectInstance, source: Entity?, cir: CallbackInfoReturnable<Boolean>) {
        val self = this as LivingEntity
        val existing = getActiveEffectsMap()[newEffect.effect]
        val action = if (existing != null) EntityPotionEffectEvent.Action.CHANGED else EntityPotionEffectEvent.Action.ADDED
        if (EntityPotionEffectEvent.firePre(self, newEffect, source, action) == null) {
            cir.returnValue = false
        }
    }

    @Inject(method = ["addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"], at = [At("RETURN")])
    private fun onAddEffectPost(newEffect: MobEffectInstance, source: Entity?, cir: CallbackInfoReturnable<Boolean>) {
        if (cir.returnValue == true) {
            val self = this as LivingEntity
            EntityPotionEffectEvent.firePost(self, newEffect, source, EntityPotionEffectEvent.Action.ADDED)
        }
    }

    // ============ EntityPotionEffectEvent (removeEffect) ============

    @Inject(method = ["removeEffect"], at = [At("HEAD")], cancellable = true)
    private fun onRemoveEffectPre(effect: Holder<MobEffect>, cir: CallbackInfoReturnable<Boolean>) {
        val self = this as LivingEntity
        val existing = getActiveEffectsMap()[effect]
        if (existing != null) {
            if (EntityPotionEffectEvent.firePre(self, existing, null, EntityPotionEffectEvent.Action.REMOVED) == null) {
                cir.returnValue = false
            }
        }
    }

    @Inject(method = ["removeEffect"], at = [At("RETURN")])
    private fun onRemoveEffectPost(effect: Holder<MobEffect>, cir: CallbackInfoReturnable<Boolean>) {
        if (cir.returnValue == true) {
            val self = this as LivingEntity
            EntityPotionEffectEvent.firePost(self, null, null, EntityPotionEffectEvent.Action.REMOVED)
        }
    }

    // ============ EntityPotionEffectEvent (removeAllEffects) ============

    @Inject(method = ["removeAllEffects"], at = [At("HEAD")], cancellable = true)
    private fun onRemoveAllEffectsPre(cir: CallbackInfoReturnable<Boolean>) {
        val self = this as LivingEntity
        if (EntityPotionEffectEvent.firePre(self, null, null, EntityPotionEffectEvent.Action.CLEARED) == null) {
            cir.returnValue = false
        }
    }

    @Inject(method = ["removeAllEffects"], at = [At("RETURN")])
    private fun onRemoveAllEffectsPost(cir: CallbackInfoReturnable<Boolean>) {
        val self = this as LivingEntity
        EntityPotionEffectEvent.firePost(self, null, null, EntityPotionEffectEvent.Action.CLEARED)
    }

    // ============ EntityKnockbackEvent ============

    @Inject(method = ["knockback"], at = [At("HEAD")], cancellable = true)
    private fun onKnockbackPre(power: Double, xd: Double, zd: Double, ci: CallbackInfo) {
        if (knockbackReentry) return
        val event = EntityKnockbackEvent.firePre(this as LivingEntity, power, xd, zd) ?: run { ci.cancel(); return }
        // 如果事件处理器修改了击退参数，使用修改后的值重新执行击退逻辑
        if (event.strength != power || event.ratioX != xd || event.ratioZ != zd) {
            knockbackReentry = true
            try {
                (this as LivingEntity).knockback(event.strength, event.ratioX, event.ratioZ)
            } finally {
                knockbackReentry = false
            }
            ci.cancel()
        }
    }

    @Inject(method = ["knockback"], at = [At("TAIL")])
    private fun onKnockbackPost(power: Double, xd: Double, zd: Double, ci: CallbackInfo) {
        EntityKnockbackEvent.firePost(this as LivingEntity, power, xd, zd)
    }

    // ============ EntityResurrectEvent ============

    @Inject(method = ["checkTotemDeathProtection"], at = [At("HEAD")], cancellable = true)
    private fun onCheckTotemPre(killingDamage: DamageSource, cir: CallbackInfoReturnable<Boolean>) {
        val self = this as LivingEntity
        // 查找持有不死图腾的手
        var totemHand: InteractionHand? = null
        for (hand in InteractionHand.entries) {
            val itemStack = getItemInHand(hand)
            val protection = itemStack.get(net.minecraft.core.component.DataComponents.DEATH_PROTECTION)
            if (protection != null) {
                totemHand = hand
                break
            }
        }
        if (EntityResurrectEvent.firePre(self, totemHand, killingDamage) == null) {
            cir.returnValue = false
        }
    }

    @Inject(method = ["checkTotemDeathProtection"], at = [At("RETURN")])
    private fun onCheckTotemPost(killingDamage: DamageSource, cir: CallbackInfoReturnable<Boolean>) {
        if (cir.returnValue == true) {
            EntityResurrectEvent.firePost(this as LivingEntity, null, killingDamage)
        }
    }

    // ============ PlayerItemConsumeEvent + PlayerEatEvent ============

    @Unique
    private var consumeItemCopy: net.minecraft.world.item.ItemStack? = null

    @Unique
    private var eatItemCopy: net.minecraft.world.item.ItemStack? = null

    @Unique
    private var cachedEatType: PlayerEatEvent.EatType? = null

    @Inject(method = ["completeUsingItem"], at = [At("HEAD")], cancellable = true)
    private fun onCompleteUsingItemPre(ci: CallbackInfo) {
        val self = this as LivingEntity
        if (self !is net.minecraft.server.level.ServerPlayer) return
        val item = useItem.copy()

        // 1. 通用消耗事件
        val consumeEvent = PlayerItemConsumeEvent.firePre(self, item)
        if (consumeEvent == null) {
            ci.cancel()
            return
        }
        consumeItemCopy = consumeEvent.item
        // 如果 item 被替换，同步到 useItem 使服务端使用新物品
        if (consumeEvent.item !== item) {
            useItem = consumeEvent.item
        }

        // 2. 吃/喝专用事件（仅限 EAT/DRINK 动画）
        val eatType = resolveEatType(useItem) ?: return
        val eatEvent = PlayerEatEvent.firePre(self, consumeEvent.item.copy(), eatType)
        if (eatEvent == null) {
            ci.cancel()
            return
        }
        eatItemCopy = eatEvent.item
        cachedEatType = eatType
        // 如果 eat 事件再次替换物品，也同步到 useItem
        if (eatEvent.item !== consumeEvent.item) {
            useItem = eatEvent.item
            consumeItemCopy = eatEvent.item
        }
    }

    @Inject(method = ["completeUsingItem"], at = [At("RETURN")])
    private fun onCompleteUsingItemPost(ci: CallbackInfo) {
        val self = this as LivingEntity
        if (self !is net.minecraft.server.level.ServerPlayer) return

        // 通用消耗事件后置
        val item = consumeItemCopy ?: return
        consumeItemCopy = null
        PlayerItemConsumeEvent.firePost(self, item)

        // 吃/喝事件后置
        val eatItem = eatItemCopy
        val eatType = cachedEatType
        eatItemCopy = null
        cachedEatType = null
        if (eatItem != null && eatType != null) {
            PlayerEatEvent.firePost(self, eatItem, eatType)
        }
    }

    /**
     * 根据 ItemStack 的使用动画判断吃/喝类型，非食物/饮料返回 null
     */
    @Unique
    private fun resolveEatType(item: net.minecraft.world.item.ItemStack): PlayerEatEvent.EatType? {
        return when (item.useAnimation) {
            ItemUseAnimation.EAT   -> PlayerEatEvent.EatType.EAT
            ItemUseAnimation.DRINK -> PlayerEatEvent.EatType.DRINK
            else -> null
        }
    }
}
