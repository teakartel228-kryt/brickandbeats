package com.bricksandbeats

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item
import net.minecraft.world.item.SpawnEggItem

object ModItems {
	val MINER_ZOMBIE_SLOW_SPAWN_EGG: Item = registerEgg("miner_zombie_slow_spawn_egg", ModEntities.MINER_ZOMBIE_SLOW)
	val MINER_ZOMBIE_MEDIUM_SPAWN_EGG: Item = registerEgg("miner_zombie_medium_spawn_egg", ModEntities.MINER_ZOMBIE_MEDIUM)
	val MINER_ZOMBIE_FAST_SPAWN_EGG: Item = registerEgg("miner_zombie_fast_spawn_egg", ModEntities.MINER_ZOMBIE_FAST)

	private fun registerEgg(name: String, type: EntityType<*>): Item {
		val key = ResourceKey.create(Registries.ITEM, BricksAndBeats.id(name))
		val item = SpawnEggItem(Item.Properties().setId(key).spawnEgg(type))
		return Registry.register(BuiltInRegistries.ITEM, key, item)
	}

	fun register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register { output ->
			output.accept(MINER_ZOMBIE_SLOW_SPAWN_EGG)
			output.accept(MINER_ZOMBIE_MEDIUM_SPAWN_EGG)
			output.accept(MINER_ZOMBIE_FAST_SPAWN_EGG)
		}
	}
}
