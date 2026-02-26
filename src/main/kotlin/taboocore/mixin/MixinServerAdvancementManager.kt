package taboocore.mixin

import net.minecraft.advancements.AdvancementHolder
import net.minecraft.advancements.AdvancementTree
import net.minecraft.advancements.TreeNodePosition
import net.minecraft.resources.Identifier
import net.minecraft.server.ServerAdvancementManager
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import java.util.Collections
import java.util.LinkedHashMap

/**
 * 成就管理器 Mixin，支持运行时动态注册成就
 *
 * 成就的 Map 和 Tree 都是在资源加载时构建的，
 * 动态注册需要重建两者并同步到客户端。
 */
@Mixin(ServerAdvancementManager::class)
abstract class MixinServerAdvancementManager {

    @Shadow
    private lateinit var advancements: Map<Identifier, AdvancementHolder>

    @Shadow
    private lateinit var tree: AdvancementTree

    /**
     * 动态添加成就
     */
    @Unique
    fun `taboocore$addAdvancements`(holders: List<AdvancementHolder>) {
        if (holders.isEmpty()) return

        // 合并到新 Map
        val newMap = LinkedHashMap(advancements)
        for (holder in holders) {
            newMap[holder.id()] = holder
        }

        // 重建 Tree
        val newTree = AdvancementTree()
        newTree.addAll(newMap.values)

        // 计算 UI 位置
        for (root in newTree.roots()) {
            if (root.holder().value().display().isPresent) {
                TreeNodePosition.run(root)
            }
        }

        // 原子更新
        advancements = Collections.unmodifiableMap(newMap)
        tree = newTree
    }

    /**
     * 移除成就
     */
    @Unique
    fun `taboocore$removeAdvancements`(ids: Set<Identifier>) {
        if (ids.isEmpty()) return

        val newMap = LinkedHashMap(advancements)
        for (id in ids) {
            newMap.remove(id)
        }

        val newTree = AdvancementTree()
        newTree.addAll(newMap.values)

        for (root in newTree.roots()) {
            if (root.holder().value().display().isPresent) {
                TreeNodePosition.run(root)
            }
        }

        advancements = Collections.unmodifiableMap(newMap)
        tree = newTree
    }
}
