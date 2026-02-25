package taboocore.util

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import taboocore.player.TabooCorePlayer
import java.util.function.Predicate

/**
 * 实体工具
 * 提供实体距离计算、范围查询、碰撞箱操作等常用功能
 */
object EntityUtils {

    // ========================= 距离与范围 =========================

    /**
     * 计算到另一个实体的距离
     * @param other 另一个实体
     * @return 欧氏距离
     */
    @JvmStatic
    fun Entity.distanceTo(other: Entity): Double {
        return this.position().distanceTo(other.position())
    }

    /**
     * 判断另一个实体是否在指定范围内
     * @param other 另一个实体
     * @param range 范围半径
     * @return 是否在范围内
     */
    @JvmStatic
    fun Entity.isInRange(other: Entity, range: Double): Boolean {
        return this.distanceToSqr(other) <= range * range
    }

    /**
     * 获取周围的所有实体
     * @param range 范围半径
     * @return 范围内的实体列表（不含自身）
     */
    @JvmStatic
    fun Entity.nearbyEntities(range: Double): List<Entity> {
        val pos = this.position()
        val aabb = AABB(
            pos.x - range, pos.y - range, pos.z - range,
            pos.x + range, pos.y + range, pos.z + range
        )
        return this.level().getEntities(this, aabb) { it.position().distanceTo(pos) <= range }
    }

    /**
     * 获取周围的所有玩家
     * @param range 范围半径
     * @return 范围内的玩家列表
     */
    @JvmStatic
    fun Entity.nearbyPlayers(range: Double): List<ServerPlayer> {
        val level = this.level()
        if (level !is ServerLevel) return emptyList()
        val pos = this.position()
        return level.players().filter { it.position().distanceTo(pos) <= range }
    }

    // ========================= 类型转换 =========================

    /**
     * 将 ServerPlayer 转换为 TabooCore 的 Player 包装
     * @return Player 代理对象
     */
    @JvmStatic
    fun ServerPlayer.toPlayer(): TabooCorePlayer {
        return TabooCorePlayer.of(this)
    }

    // ========================= 生命与伤害 =========================

    /**
     * 清除生物身上的所有药水效果
     */
    @JvmStatic
    fun LivingEntity.clearAllEffects() {
        removeAllEffects()
    }

    /**
     * 治疗生物指定血量
     * @param amount 治疗量
     */
    @JvmStatic
    fun LivingEntity.healAmount(amount: Float) {
        heal(amount)
    }

    /**
     * 对生物造成伤害
     * @param amount 伤害量
     * @param source 伤害来源
     */
    @JvmStatic
    fun LivingEntity.dealDamage(amount: Float, source: DamageSource) {
        val level = this.level()
        if (level is ServerLevel) {
            hurtServer(level, source, amount)
        }
    }

    // ========================= 碰撞箱工具 =========================

    /**
     * 获取实体当前碰撞箱（绝对坐标 AABB）
     */
    val Entity.currentBoundingBox: AABB
        get() = this.boundingBox

    /**
     * 获取实体类型定义的基础碰撞箱尺寸（未受姿态等状态影响）
     */
    val Entity.baseDimensions: EntityDimensions
        get() = this.type.dimensions

    /**
     * 获取碰撞箱宽度
     */
    val Entity.boxWidth: Float
        get() = this.bbWidth

    /**
     * 获取碰撞箱高度
     */
    val Entity.boxHeight: Float
        get() = this.bbHeight

    /**
     * 手动设置实体碰撞箱（慎用，会覆盖原始碰撞箱）
     * @param box 新的碰撞箱
     */
    @JvmStatic
    fun Entity.overrideBoundingBox(box: AABB) {
        this.setBoundingBox(box)
    }

    /**
     * 判断两个实体碰撞箱是否相交
     * @param other 另一个实体
     * @return 碰撞箱是否相交
     */
    @JvmStatic
    fun Entity.intersects(other: Entity): Boolean {
        return this.boundingBox.intersects(other.boundingBox)
    }

    /**
     * 判断实体碰撞箱是否与指定 AABB 相交
     * @param box 目标 AABB
     * @return 是否相交
     */
    @JvmStatic
    fun Entity.intersects(box: AABB): Boolean {
        return this.boundingBox.intersects(box)
    }

    /**
     * 判断点是否在实体碰撞箱内
     * @param x X 坐标
     * @param y Y 坐标
     * @param z Z 坐标
     * @return 点是否在碰撞箱内
     */
    @JvmStatic
    fun Entity.containsPoint(x: Double, y: Double, z: Double): Boolean {
        return this.boundingBox.contains(x, y, z)
    }

    /**
     * 以实体为中心，将碰撞箱向外膨胀指定半径构造新 AABB
     * @param range 膨胀半径
     * @return 膨胀后的 AABB
     */
    @JvmStatic
    fun Entity.expandedBoundingBox(range: Double): AABB {
        return this.boundingBox.inflate(range)
    }

    /**
     * 判断实体是否与某个方块的碰撞形状相交
     * @param pos 方块坐标
     * @return 是否碰撞
     */
    @JvmStatic
    fun Entity.collidesWithBlock(pos: BlockPos): Boolean {
        val level = this.level()
        val blockShape = level.getBlockState(pos).getCollisionShape(level, pos)
        if (blockShape.isEmpty) return false
        return blockShape.toAabbs().any { aabb ->
            aabb.move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()).intersects(this.boundingBox)
        }
    }
}

// ========================= AABB 扩展函数 =========================

/**
 * 将 AABB 三轴分别膨胀指定量
 * @param x X 轴膨胀量
 * @param y Y 轴膨胀量
 * @param z Z 轴膨胀量
 * @return 膨胀后的新 AABB
 */
fun AABB.expand(x: Double, y: Double, z: Double): AABB = inflate(x, y, z)

/**
 * 将 AABB 各轴均匀膨胀指定量
 * @param amount 膨胀量
 * @return 膨胀后的新 AABB
 */
fun AABB.expand(amount: Double): AABB = inflate(amount)

/**
 * 获取 AABB 的中心点坐标
 */
val AABB.centerPoint: Vec3
    get() = getCenter()

/**
 * 获取 AABB 的 X 轴宽度
 */
val AABB.width: Double
    get() = maxX - minX

/**
 * 获取 AABB 的 Y 轴高度
 */
val AABB.height: Double
    get() = maxY - minY

/**
 * 获取 AABB 的 Z 轴深度
 */
val AABB.depth: Double
    get() = maxZ - minZ

/**
 * 获取碰撞箱内所有实体
 * @param level 世界
 * @param predicate 过滤条件
 * @return 范围内满足条件的实体列表
 */
fun AABB.getEntities(level: ServerLevel, predicate: Predicate<Entity> = Predicate { true }): List<Entity> {
    return level.getEntities(null as Entity?, this, predicate)
}

/**
 * 获取碰撞箱内所有玩家
 * @param level 世界
 * @return 范围内的玩家列表
 */
fun AABB.getPlayers(level: ServerLevel): List<ServerPlayer> {
    return level.getPlayers { this.intersects(it.boundingBox) }
}
