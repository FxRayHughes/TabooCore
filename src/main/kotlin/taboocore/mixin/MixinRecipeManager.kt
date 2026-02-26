package taboocore.mixin

import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.crafting.RecipeManager
import net.minecraft.world.item.crafting.RecipeMap
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique

/**
 * 配方管理器 Mixin，提供动态注册/移除配方的能力
 *
 * RecipeMap 是不可变的（ImmutableMultimap + ImmutableMap），
 * 因此每次修改都需要重建整个 RecipeMap 并原子替换引用。
 */
@Mixin(RecipeManager::class)
abstract class MixinRecipeManager {

    @Shadow
    private var recipes: RecipeMap = RecipeMap.EMPTY

    /**
     * 添加新的配方
     */
    @Unique
    fun `taboocore$addRecipes`(holders: List<RecipeHolder<*>>) {
        val allHolders = recipes.values().toList() + holders
        this.recipes = RecipeMap.create(allHolders)
    }

    /**
     * 移除指定的配方
     */
    @Unique
    fun `taboocore$removeRecipes`(ids: Set<ResourceKey<Recipe<*>>>) {
        val filtered = recipes.values().filter { it.id() !in ids }
        this.recipes = RecipeMap.create(filtered)
    }

    /**
     * 获取当前所有配方
     */
    @Unique
    fun `taboocore$getAllRecipes`(): Collection<RecipeHolder<*>> {
        return recipes.values()
    }
}
