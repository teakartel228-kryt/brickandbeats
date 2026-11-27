package com.bricksandbeats

import net.fabricmc.api.ModInitializer
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object BricksAndBeats : ModInitializer {
	const val MOD_ID: String = "bricks_and_beats"

	val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		ModEntities.register()
		ModItems.register()
		HoeWeapons.register()
		ZombieHive.register()
		BrickThrowing.register()
		LOGGER.info("Bricks and Beats loaded")
	}

	fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
