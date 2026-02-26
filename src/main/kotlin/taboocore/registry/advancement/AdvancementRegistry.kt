package taboocore.registry.advancement

import net.minecraft.advancements.*
import net.minecraft.advancements.criterion.ImpossibleTrigger
import net.minecraft.core.ClientAsset
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.Items
import net.minecraft.world.level.ItemLike
import taboocore.mixin.MixinServerAdvancementManager
import taboocore.util.ServerUtils
import taboocore.util.TabooCoreLogger
import java.util.Optional

/**
 * 成就注册表，提供在运行时动态注册/移除成就的 API
 *
 * 成就支持运行时热加载，无需在 bootstrap 阶段注册。
 * 应在 Plugin.onEnable() 或之后调用。
 */
object AdvancementRegistry {

    /**
     * 注册单个成就
     */
    fun register(holder: AdvancementHolder) {
        register(listOf(holder))
    }

    /**
     * 注册多个成就
     */
    fun register(holders: List<AdvancementHolder>) {
        val server = ServerUtils.serverInstance ?: return
        val mixin = server.advancements as MixinServerAdvancementManager

        // 添加到管理器（重建 Map + Tree）
        mixin.`taboocore$addAdvancements`(holders)

        // 同步到所有在线玩家
        val packet = ClientboundUpdateAdvancementsPacket(
            false, holders, emptySet(), emptyMap(), true
        )
        for (player in server.playerList.players) {
            player.connection.send(packet)

            // 为玩家注册新成就的触发器监听
            for (holder in holders) {
                runCatching {
                    val pa = player.advancements
                    val method = pa.javaClass.getDeclaredMethod("registerListeners", AdvancementHolder::class.java)
                    method.isAccessible = true
                    method.invoke(pa, holder)
                }
            }
        }

        for (holder in holders) {
            TabooCoreLogger.info("Registered advancement: ${holder.id()}")
        }
    }

    /**
     * 移除成就
     */
    fun unregister(id: String) {
        unregister(setOf(Identifier.parse(id)))
    }

    /**
     * 移除多个成就
     */
    fun unregister(ids: Set<Identifier>) {
        val server = ServerUtils.serverInstance ?: return
        val mixin = server.advancements as MixinServerAdvancementManager
        mixin.`taboocore$removeAdvancements`(ids)

        val packet = ClientboundUpdateAdvancementsPacket(
            false, emptyList(), ids, emptyMap(), true
        )
        for (player in server.playerList.players) {
            player.connection.send(packet)
        }
    }

    /**
     * 获取所有成就
     */
    fun getAll(): Collection<AdvancementHolder> {
        val server = ServerUtils.serverInstance ?: return emptyList()
        return server.advancements.allAdvancements
    }
}

// ==================== DSL Builders ====================

/**
 * 成就 DSL
 *
 * ```kotlin
 * val adv = advancement("mymod", "first_step") {
 *     parent("minecraft:story/root")
 *     display {
 *         title = Component.literal("First Step")
 *         description = Component.literal("Break a block")
 *         icon = Items.DIAMOND_PICKAXE
 *         type = AdvancementType.TASK
 *     }
 *     rewards { experience = 50 }
 *     criterion("trigger", CriteriaTriggers.IMPOSSIBLE, ImpossibleTrigger.TriggerInstance())
 * }
 * AdvancementRegistry.register(adv)
 * ```
 */
fun advancement(namespace: String, path: String, block: AdvancementDsl.() -> Unit): AdvancementHolder {
    val id = Identifier.tryBuild(namespace, path) ?: throw IllegalArgumentException("Invalid advancement id: $namespace:$path")
    return AdvancementDsl(id).apply(block).build()
}

fun advancement(id: Identifier, block: AdvancementDsl.() -> Unit): AdvancementHolder {
    return AdvancementDsl(id).apply(block).build()
}

class AdvancementDsl(private val id: Identifier) {
    private var parentHolder: AdvancementHolder? = null
    private var displayInfo: DisplayInfo? = null
    private var rewards: AdvancementRewards = AdvancementRewards.EMPTY
    private val criteria = mutableMapOf<String, Criterion<*>>()
    private var strategy: AdvancementRequirements.Strategy = AdvancementRequirements.Strategy.AND
    private var telemetry = true

    fun parent(id: String) = parent(Identifier.parse(id))
    fun parent(id: Identifier) = apply {
        // 从服务端成就管理器查找 AdvancementHolder
        val server = ServerUtils.serverInstance
        val holder = server?.advancements?.get(id)
        this.parentHolder = holder ?: AdvancementHolder(id, Advancement(
            java.util.Optional.empty(),
            java.util.Optional.empty(),
            AdvancementRewards.EMPTY,
            emptyMap(),
            AdvancementRequirements(emptyList()),
            false
        ))
    }
    fun parent(holder: AdvancementHolder) = apply { this.parentHolder = holder }

    fun display(block: DisplayDsl.() -> Unit) = apply {
        this.displayInfo = DisplayDsl().apply(block).build()
    }

    fun rewards(block: RewardsDsl.() -> Unit) = apply {
        this.rewards = RewardsDsl().apply(block).build()
    }

    fun <T : CriterionTriggerInstance> criterion(
        name: String,
        trigger: CriterionTrigger<T>,
        instance: T
    ) = apply {
        criteria[name] = Criterion(trigger, instance)
    }

    fun requireAll() = apply { strategy = AdvancementRequirements.Strategy.AND }
    fun requireAny() = apply { strategy = AdvancementRequirements.Strategy.OR }
    fun telemetry(enabled: Boolean) = apply { this.telemetry = enabled }

    fun build(): AdvancementHolder {
        val builder = Advancement.Builder.advancement()
        parentHolder?.let { builder.parent(it) }
        displayInfo?.let { builder.display(it) }
        builder.rewards(rewards)

        if (criteria.isEmpty()) {
            criteria["impossible"] = Criterion(
                CriteriaTriggers.IMPOSSIBLE,
                ImpossibleTrigger.TriggerInstance()
            )
        }

        criteria.forEach { (name, criterion) -> builder.addCriterion(name, criterion) }
        builder.requirements(strategy)
        if (telemetry) builder.sendsTelemetryEvent()

        return builder.build(id)
    }
}

class DisplayDsl {
    var title: Component = Component.literal("Advancement")
    var description: Component = Component.literal("")
    var icon: ItemLike = Items.STONE
    var background: Identifier? = null
    var type: AdvancementType = AdvancementType.TASK
    var showToast: Boolean = true
    var announceToChat: Boolean = true
    var hidden: Boolean = false

    fun build(): DisplayInfo {
        return DisplayInfo(
            ItemStackTemplate(icon.asItem()),
            title,
            description,
            background?.let { ClientAsset.ResourceTexture(it) }?.let { Optional.of(it) } ?: Optional.empty(),
            type,
            showToast,
            announceToChat,
            hidden
        )
    }
}

class RewardsDsl {
    var experience: Int = 0

    fun build(): AdvancementRewards {
        if (experience == 0) return AdvancementRewards.EMPTY
        return AdvancementRewards(experience, emptyList(), emptyList(), Optional.empty())
    }
}

/**
 * 扩展函数
 */
fun AdvancementHolder.register() = AdvancementRegistry.register(this)
fun List<AdvancementHolder>.register() = AdvancementRegistry.register(this)
