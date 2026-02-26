package taboocore.registry.recipe

import net.minecraft.core.registries.Registries
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.crafting.*
import net.minecraft.world.level.ItemLike
import taboocore.mixin.MixinRecipeManager
import taboocore.util.ServerUtils

/**
 * 配方注册表，提供在运行时动态注册/移除配方的 API
 *
 * 配方使用不可变的 RecipeMap 存储，每次修改都会重建 Map 并同步到客户端。
 * 应在 Plugin.onEnable() 或之后调用。
 */
object RecipeRegistry {

    /**
     * 注册单个配方
     */
    fun register(holder: RecipeHolder<*>): Boolean {
        return register(listOf(holder))
    }

    /**
     * 注册多个配方
     */
    fun register(holders: List<RecipeHolder<*>>): Boolean {
        val server = ServerUtils.serverInstance ?: return false
        val mixin = server.recipeManager as MixinRecipeManager
        mixin.`taboocore$addRecipes`(holders)
        syncToPlayers(server)
        return true
    }

    /**
     * 移除配方
     */
    fun unregister(id: String): Boolean {
        val key = ResourceKey.create(Registries.RECIPE, Identifier.parse(id))
        return unregister(setOf(key))
    }

    /**
     * 移除多个配方
     */
    fun unregister(keys: Set<ResourceKey<Recipe<*>>>): Boolean {
        val server = ServerUtils.serverInstance ?: return false
        val mixin = server.recipeManager as MixinRecipeManager
        mixin.`taboocore$removeRecipes`(keys)
        syncToPlayers(server)
        return true
    }

    /**
     * 获取所有配方
     */
    fun getAll(): Collection<RecipeHolder<*>> {
        val server = ServerUtils.serverInstance ?: return emptyList()
        val mixin = server.recipeManager as MixinRecipeManager
        return mixin.`taboocore$getAllRecipes`()
    }

    private fun syncToPlayers(server: MinecraftServer) {
        val rm = server.recipeManager
        val packet = ClientboundUpdateRecipesPacket(
            rm.synchronizedItemProperties,
            rm.synchronizedStonecutterRecipes
        )
        for (player in server.playerList.players) {
            player.connection.send(packet)
        }
    }
}

// ==================== DSL Builders ====================

/**
 * 有序合成配方 DSL
 *
 * ```kotlin
 * val recipe = shapedRecipe("mymod", "diamond_compress") {
 *     pattern("###", "###", "###")
 *     key('#', Items.DIAMOND)
 *     result(Items.DIAMOND_BLOCK)
 * }
 * RecipeRegistry.register(recipe)
 * ```
 */
fun shapedRecipe(namespace: String, name: String, block: ShapedRecipeBuilder.() -> Unit): RecipeHolder<ShapedRecipe> {
    return ShapedRecipeBuilder(namespace, name).apply(block).build()
}

/**
 * 无序合成配方 DSL
 */
fun shapelessRecipe(namespace: String, name: String, block: ShapelessRecipeBuilder.() -> Unit): RecipeHolder<ShapelessRecipe> {
    return ShapelessRecipeBuilder(namespace, name).apply(block).build()
}

/**
 * 熔炉配方 DSL
 */
fun smeltingRecipe(namespace: String, name: String, block: CookingRecipeBuilder.() -> Unit): RecipeHolder<SmeltingRecipe> {
    return CookingRecipeBuilder(namespace, name).apply(block).buildSmelting()
}

class ShapedRecipeBuilder(private val namespace: String, private val name: String) {
    private var patternLines: List<String> = emptyList()
    private val keys = mutableMapOf<Char, Ingredient>()
    private var resultItem: ItemLike? = null
    private var resultCount: Int = 1

    fun pattern(vararg lines: String) = apply { this.patternLines = lines.toList() }
    fun key(char: Char, ingredient: Ingredient) = apply { keys[char] = ingredient }
    fun key(char: Char, item: ItemLike) = apply { keys[char] = Ingredient.of(item) }
    fun result(item: ItemLike, count: Int = 1) = apply { resultItem = item; resultCount = count }

    fun build(): RecipeHolder<ShapedRecipe> {
        check(patternLines.isNotEmpty()) { "Pattern must not be empty" }
        check(keys.isNotEmpty()) { "Keys must not be empty" }
        val item = checkNotNull(resultItem) { "Result must be set" }

        val id = ResourceKey.create(Registries.RECIPE, Identifier.tryBuild(namespace, name)
            ?: throw IllegalArgumentException("Invalid recipe id: $namespace:$name"))
        val recipePattern = ShapedRecipePattern.of(keys, patternLines)
        val recipe = ShapedRecipe(
            Recipe.CommonInfo(true),
            CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
            recipePattern,
            ItemStackTemplate(item.asItem(), resultCount)
        )
        return RecipeHolder(id, recipe)
    }
}

class ShapelessRecipeBuilder(private val namespace: String, private val name: String) {
    private val ingredients = mutableListOf<Ingredient>()
    private var resultItem: ItemLike? = null
    private var resultCount: Int = 1

    fun ingredient(ingredient: Ingredient) = apply { ingredients.add(ingredient) }
    fun ingredient(item: ItemLike) = apply { ingredients.add(Ingredient.of(item)) }
    fun result(item: ItemLike, count: Int = 1) = apply { resultItem = item; resultCount = count }

    fun build(): RecipeHolder<ShapelessRecipe> {
        check(ingredients.isNotEmpty()) { "Ingredients must not be empty" }
        val item = checkNotNull(resultItem) { "Result must be set" }

        val id = ResourceKey.create(Registries.RECIPE, Identifier.tryBuild(namespace, name)
            ?: throw IllegalArgumentException("Invalid recipe id: $namespace:$name"))
        val recipe = ShapelessRecipe(
            Recipe.CommonInfo(true),
            CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
            ItemStackTemplate(item.asItem(), resultCount),
            ingredients
        )
        return RecipeHolder(id, recipe)
    }
}

class CookingRecipeBuilder(private val namespace: String, private val name: String) {
    var input: Ingredient? = null
    private var resultItem: ItemLike? = null
    private var resultCount: Int = 1
    var experience: Float = 0.1f
    var cookingTime: Int = 200

    fun input(ingredient: Ingredient) = apply { this.input = ingredient }
    fun input(item: ItemLike) = apply { this.input = Ingredient.of(item) }
    fun result(item: ItemLike, count: Int = 1) = apply { resultItem = item; resultCount = count }

    fun buildSmelting(): RecipeHolder<SmeltingRecipe> {
        val inp = checkNotNull(input) { "Input must be set" }
        val item = checkNotNull(resultItem) { "Result must be set" }

        val id = ResourceKey.create(Registries.RECIPE, Identifier.tryBuild(namespace, name)
            ?: throw IllegalArgumentException("Invalid recipe id: $namespace:$name"))
        val recipe = SmeltingRecipe(
            Recipe.CommonInfo(true),
            AbstractCookingRecipe.CookingBookInfo(CookingBookCategory.MISC, ""),
            inp,
            ItemStackTemplate(item.asItem(), resultCount),
            experience,
            cookingTime
        )
        return RecipeHolder(id, recipe)
    }
}
