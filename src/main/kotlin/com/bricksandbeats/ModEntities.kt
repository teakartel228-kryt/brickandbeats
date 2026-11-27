package com.bricksandbeats

import com.bricksandbeats.entity.MinerTier
import com.bricksandbeats.entity.MinerZombie
import com.bricksandbeats.entity.ThrownBrick
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.monster.zombie.Zombie

object ModEntities {
	val MINER_ZOMBIE_SLOW: EntityType<MinerZombie> = registerMiner(MinerTier.SLOW)
	val MINER_ZOMBIE_MEDIUM: EntityType<MinerZombie> = registerMiner(MinerTier.MEDIUM)
	val MINER_ZOMBIE_FAST: EntityType<MinerZombie> = registerMiner(MinerTier.FAST)

	/** Брошенный кирпич: размеры и отслеживание как у снежка. */
	val THROWN_BRICK: EntityType<ThrownBrick> = run {
		val key = ResourceKey.create(Registries.ENTITY_TYPE, BricksAndBeats.id("thrown_brick"))
		val builder = EntityType.Builder.of(
			EntityType.EntityFactory<ThrownBrick> { type, level -> ThrownBrick(type, level) },
			MobCategory.MISC
		).sized(0.25f, 0.25f).clientTrackingRange(4).updateInterval(10)
		Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key))
	}

	private fun registerMiner(tier: MinerTier): EntityType<MinerZombie> {
		val key = ResourceKey.create(Registries.ENTITY_TYPE, BricksAndBeats.id("miner_zombie_${tier.id}"))
		val builder = EntityType.Builder.of(
			EntityType.EntityFactory<MinerZombie> { type, level -> MinerZombie(type, level, tier) },
			MobCategory.MONSTER
		).sized(0.6f, 1.95f).clientTrackingRange(8)
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key))
	}

	/** Вызов нужен, чтобы инициализировать объект и зарегистрировать атрибуты. */
	fun register() {
		for (type in listOf(MINER_ZOMBIE_SLOW, MINER_ZOMBIE_MEDIUM, MINER_ZOMBIE_FAST)) {
			FabricDefaultAttributeRegistry.register(type, Zombie.createAttributes())
		}
	}
}
