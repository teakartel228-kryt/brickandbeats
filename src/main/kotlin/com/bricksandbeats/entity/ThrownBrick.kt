package com.bricksandbeats.entity

import com.bricksandbeats.ModEntities
import com.bricksandbeats.ZombieHive
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult

/**
 * Брошенный кирпич. Летит как снежок, но бьёт по-настоящему и разбивается о цель.
 * Летит с тем предметом, которым бросили: обычный или незерский кирпич.
 * В полёте рисуется ванильным ThrownItemRenderer — то есть кастомной 3D-моделью этого кирпича.
 */
class ThrownBrick : ThrowableItemProjectile {

	/** Для регистрации типа сущности и загрузки с диска. */
	constructor(type: EntityType<out ThrownBrick>, level: Level) : super(type, level)

	/** Бросок живым существом (игрок или зомби). */
	constructor(level: Level, owner: LivingEntity, stack: ItemStack) :
		super(ModEntities.THROWN_BRICK, owner, level, stack)

	/** Бросок из точки (раздатчик и т.п.). */
	constructor(level: Level, x: Double, y: Double, z: Double, stack: ItemStack) :
		super(ModEntities.THROWN_BRICK, x, y, z, level, stack)

	override fun getDefaultItem(): Item = Items.BRICK

	/** Кирпичи зомби не должны бить других зомби: сквозь своих кирпич просто пролетает. */
	private fun isFriendlyFire(target: net.minecraft.world.entity.Entity): Boolean {
		val owner = getOwner() ?: return false
		return ZombieHive.isHiveMember(owner) && ZombieHive.isHiveMember(target)
	}

	override fun onHit(hitResult: HitResult) {
		val level = level()
		if (level !is ServerLevel) {
			super.onHit(hitResult)
			return
		}
		if (hitResult is EntityHitResult && isFriendlyFire(hitResult.entity)) return
		super.onHit(hitResult)
		breakApart(level)
	}

	override fun onHitEntity(hitResult: EntityHitResult) {
		super.onHitEntity(hitResult)
		val level = level() as? ServerLevel ?: return
		val damage = if (getOwner() is Player) PLAYER_DAMAGE else MOB_DAMAGE
		val source = level.damageSources().thrown(this, getOwner())
		hitResult.entity.hurtServer(level, source, damage)
	}

	private fun breakApart(level: ServerLevel) {
		val thrown = getItem()
		val state = (if (thrown.item === Items.NETHER_BRICK) Blocks.NETHER_BRICKS else Blocks.BRICKS).defaultBlockState()
		level.playSound(null, x, y, z, state.soundType.breakSound, SoundSource.NEUTRAL, 0.8f, 1.3f)
		level.sendParticles(BlockParticleOption(ParticleTypes.BLOCK, state), x, y, z, 12, 0.1, 0.1, 0.1, 0.05)
		// Иногда кирпич остаётся целым и его можно подобрать.
		if (random.nextFloat() < SURVIVE_CHANCE) {
			level.addFreshEntity(ItemEntity(level, x, y, z, thrown.copyWithCount(1)))
		}
		discard()
	}

	companion object {
		/** Урон кирпича, брошенного игроком (в половинках сердца: 3 = 1.5 сердца). */
		const val PLAYER_DAMAGE = 3.0f
		/** Урон кирпича, брошенного зомби (и любым другим мобом). */
		const val MOB_DAMAGE = 2.0f
		/** Шанс, что кирпич не разобьётся и упадёт предметом. */
		const val SURVIVE_CHANCE = 0.3f
	}
}
