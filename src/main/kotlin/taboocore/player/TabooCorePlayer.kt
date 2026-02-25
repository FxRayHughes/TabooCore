package taboocore.player

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import taboolib.common.platform.ProxyGameMode
import taboolib.common.platform.ProxyPlayer
import taboolib.common.util.Location
import taboolib.common.util.Vector
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 原版 ServerPlayer 的 ProxyPlayer 包装
 * 高版本 MC 不混淆，直接调用 NMS API
 * 部分 private/protected 字段通过反射访问
 */
class TabooCorePlayer(val handle: ServerPlayer) : ProxyPlayer {

    companion object {

        private val cache = ConcurrentHashMap<UUID, TabooCorePlayer>()

        /** 获取或创建缓存的 ProxyPlayer */
        fun of(player: ServerPlayer): TabooCorePlayer {
            return cache.computeIfAbsent(player.uuid) { TabooCorePlayer(player) }
        }

        /** 玩家退出时移除缓存 */
        fun remove(player: ServerPlayer) {
            cache.remove(player.uuid)
        }
    }

    override val origin: Any
        get() = handle

    override val name: String
        get() = handle.plainTextName

    override val uniqueId: UUID
        get() = handle.uuid

    override val address: InetSocketAddress?
        get() = runCatching {
            val connField = handle.connection.javaClass.superclass.getDeclaredField("connection")
            connField.isAccessible = true
            val conn = connField.get(handle.connection) as net.minecraft.network.Connection
            conn.remoteAddress as? InetSocketAddress
        }.getOrNull()

    override val ping: Int
        get() = handle.connection.latency()

    override val locale: String
        get() = runCatching {
            val field = ServerPlayer::class.java.getDeclaredField("language")
            field.isAccessible = true
            field.get(handle) as String
        }.getOrElse { "en_us" }

    override val world: String
        get() = handle.level().dimension().identifier().toString()

    override val location: Location
        get() = Location(world, handle.x, handle.y, handle.z, handle.yRot, handle.xRot)

    override var isOp: Boolean
        get() = handle.level().server.playerList.isOp(handle.nameAndId())
        set(_) {}

    override var compassTarget: Location
        get() = location
        set(_) {}

    override var bedSpawnLocation: Location?
        get() = null
        set(_) {}

    override var displayName: String?
        get() = name
        set(_) {}

    override var playerListName: String?
        get() = name
        set(_) {}

    override var gameMode: ProxyGameMode
        get() = when (handle.gameMode.gameModeForPlayer) {
            net.minecraft.world.level.GameType.SURVIVAL -> ProxyGameMode.SURVIVAL
            net.minecraft.world.level.GameType.CREATIVE -> ProxyGameMode.CREATIVE
            net.minecraft.world.level.GameType.ADVENTURE -> ProxyGameMode.ADVENTURE
            net.minecraft.world.level.GameType.SPECTATOR -> ProxyGameMode.SPECTATOR
        }
        set(value) {
            handle.setGameMode(
                when (value) {
                    ProxyGameMode.SURVIVAL -> net.minecraft.world.level.GameType.SURVIVAL
                    ProxyGameMode.CREATIVE -> net.minecraft.world.level.GameType.CREATIVE
                    ProxyGameMode.ADVENTURE -> net.minecraft.world.level.GameType.ADVENTURE
                    ProxyGameMode.SPECTATOR -> net.minecraft.world.level.GameType.SPECTATOR
                }
            )
        }

    override val isSneaking: Boolean get() = handle.isCrouching
    override val isSprinting: Boolean get() = handle.isSprinting
    override val isBlocking: Boolean get() = handle.isBlocking
    override var isGliding: Boolean
        get() = handle.isFallFlying
        set(_) {}
    override var isGlowing: Boolean
        get() = handle.isCurrentlyGlowing
        set(v) { handle.setGlowingTag(v) }
    override var isSwimming: Boolean
        get() = handle.isSwimming
        set(v) { handle.isSwimming = v }
    override val isRiptiding: Boolean get() = handle.isAutoSpinAttack
    override val isSleeping: Boolean get() = handle.isSleeping
    override val sleepTicks: Int get() = handle.getSleepTimer()
    override var isSleepingIgnored: Boolean
        get() = false
        set(_) {}
    override val isDead: Boolean get() = handle.isDeadOrDying
    override val isConversing: Boolean get() = false
    override val isLeashed: Boolean get() = false
    override val isOnGround: Boolean get() = handle.onGround()
    override val isInsideVehicle: Boolean get() = handle.isPassenger
    override var hasGravity: Boolean
        get() = !handle.isNoGravity
        set(v) { handle.setNoGravity(!v) }
    override val attackCooldown: Int get() = (handle.getAttackStrengthScale(0f) * 20).toInt()
    override var playerTime: Long get() = handle.level().gameTime; set(_) {}
    override val firstPlayed: Long
        get() = runCatching {
            handle.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME)).toLong()
        }.getOrElse { 0L }
    override val lastPlayed: Long get() = System.currentTimeMillis()
    override var absorptionAmount: Double
        get() = handle.absorptionAmount.toDouble()
        set(v) { handle.absorptionAmount = v.toFloat() }
    override var noDamageTicks: Int
        get() = handle.invulnerableTime
        set(v) { handle.invulnerableTime = v }
    override var remainingAir: Int
        get() = handle.airSupply
        set(v) { handle.airSupply = v }
    override val maximumAir: Int get() = handle.maxAirSupply
    override var level: Int
        get() = handle.experienceLevel
        set(v) { handle.experienceLevel = v }
    override var exp: Float
        get() = handle.experienceProgress
        set(v) { handle.experienceProgress = v }
    override var exhaustion: Float
        get() = runCatching {
            val field = handle.foodData.javaClass.getDeclaredField("exhaustionLevel")
            field.isAccessible = true
            field.getFloat(handle.foodData)
        }.getOrElse { 0f }
        set(v) { handle.foodData.addExhaustion(v) }
    override var saturation: Float
        get() = handle.foodData.saturationLevel
        set(v) { handle.foodData.setSaturation(v) }
    override var foodLevel: Int
        get() = handle.foodData.foodLevel
        set(v) { handle.foodData.setFoodLevel(v) }
    override var health: Double
        get() = handle.health.toDouble()
        set(v) { handle.health = v.toFloat() }
    override var maxHealth: Double get() = handle.maxHealth.toDouble(); set(_) {}
    override var allowFlight: Boolean
        get() = handle.abilities.mayfly
        set(v) { handle.abilities.mayfly = v; handle.onUpdateAbilities() }
    override var isFlying: Boolean
        get() = handle.abilities.flying
        set(v) { handle.abilities.flying = v; handle.onUpdateAbilities() }
    override var flySpeed: Float
        get() = handle.abilities.flyingSpeed
        set(v) { handle.abilities.flyingSpeed = v; handle.onUpdateAbilities() }
    override var walkSpeed: Float
        get() = handle.abilities.walkingSpeed
        set(v) { handle.abilities.walkingSpeed = v; handle.onUpdateAbilities() }
    override val pose: String get() = handle.pose.name
    override val facing: String get() = handle.direction.name

    @Suppress("SENSELESS_COMPARISON")
    override fun isOnline(): Boolean = handle.connection != null

    override fun sendMessage(message: String) {
        handle.sendSystemMessage(Component.literal(message))
    }

    override fun hasPermission(permission: String): Boolean = isOp

    override fun performCommand(command: String): Boolean {
        return runCatching {
            handle.level().server.commands.performPrefixedCommand(handle.createCommandSourceStack(), command)
            true
        }.getOrElse { false }
    }

    override fun kick(message: String?) {
        handle.connection.disconnect(Component.literal(message ?: "Kicked by server"))
    }

    override fun chat(message: String) {
        performCommand("say $message")
    }

    override fun playSound(location: Location, sound: String, volume: Float, pitch: Float) {
        handle.connection.send(
            ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(
                    SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(sound))
                ),
                SoundSource.PLAYERS,
                location.x, location.y, location.z,
                volume, pitch, handle.level().random.nextLong()
            )
        )
    }

    override fun playSoundResource(location: Location, sound: String, volume: Float, pitch: Float) =
        playSound(location, sound, volume, pitch)

    override fun sendTitle(title: String?, subtitle: String?, fadein: Int, stay: Int, fadeout: Int) {
        handle.connection.send(ClientboundSetTitlesAnimationPacket(fadein, stay, fadeout))
        title?.let { handle.connection.send(ClientboundSetTitleTextPacket(Component.literal(it))) }
        subtitle?.let { handle.connection.send(ClientboundSetSubtitleTextPacket(Component.literal(it))) }
    }

    override fun sendActionBar(message: String) {
        handle.connection.send(ClientboundSetActionBarTextPacket(Component.literal(message)))
    }

    override fun sendRawMessage(message: String) {
        sendMessage(message)
    }

    override fun sendParticle(particle: String, location: Location, offset: Vector, count: Int, speed: Double, data: Any?) {
        // TODO: 通过 ClientboundLevelParticlesPacket 发送
    }

    override fun teleport(location: Location) {
        val key = ResourceKey.create(
            Registries.DIMENSION,
            Identifier.withDefaultNamespace(location.world ?: world)
        )
        val targetLevel = handle.level().server.getLevel(key) ?: handle.level()
        handle.teleportTo(targetLevel, location.x, location.y, location.z, setOf(), location.yaw, location.pitch, true)
    }

    override fun giveExp(exp: Int) {
        handle.giveExperiencePoints(exp)
    }

    override fun onQuit(callback: Runnable) {
        // TODO: 注册离线回调
    }
}
