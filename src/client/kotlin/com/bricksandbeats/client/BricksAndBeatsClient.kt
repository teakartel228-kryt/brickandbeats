package com.bricksandbeats.client

import com.bricksandbeats.ModEntities
import com.bricksandbeats.entity.ThrownBrick
import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.EntityRenderers
import net.minecraft.client.renderer.entity.ThrownItemRenderer
import net.minecraft.client.renderer.entity.ZombieRenderer
import net.minecraft.world.entity.monster.zombie.Zombie

object BricksAndBeatsClient : ClientModInitializer {
	override fun onInitializeClient() {
		val provider = EntityRendererProvider<Zombie> { context -> ZombieRenderer(context) }
		EntityRenderers.register(ModEntities.MINER_ZOMBIE_SLOW, provider)
		EntityRenderers.register(ModEntities.MINER_ZOMBIE_MEDIUM, provider)
		EntityRenderers.register(ModEntities.MINER_ZOMBIE_FAST, provider)

		// Кирпич в полёте рисуется моделью предмета throwing_brick (наша 3D-модель).
		EntityRenderers.register(ModEntities.THROWN_BRICK, EntityRendererProvider<ThrownBrick> { context -> ThrownItemRenderer(context) })
	}
}
