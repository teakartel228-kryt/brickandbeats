package com.bricksandbeats.entity

import net.minecraft.util.RandomSource
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

/**
 * Скорость копания зомби относительно игрока.
 * speedMultiplier: 1.0 = как игрок с той же киркой.
 * pickaxeWeights: шанс появиться с киркой [деревянная, каменная, железная, алмазная].
 */
enum class MinerTier(
	val id: String,
	val speedMultiplier: Float,
	private val pickaxeWeights: IntArray
) {
	SLOW("slow", 0.5f, intArrayOf(55, 35, 9, 1)),
	MEDIUM("medium", 1.0f, intArrayOf(20, 35, 35, 10)),
	FAST("fast", 1.75f, intArrayOf(5, 15, 45, 35));

	fun pickPickaxe(random: RandomSource): Item {
		val total = pickaxeWeights.sum()
		var roll = random.nextInt(total)
		for (i in pickaxeWeights.indices) {
			roll -= pickaxeWeights[i]
			if (roll < 0) return PICKAXES[i]
		}
		return PICKAXES[0]
	}

	companion object {
		private val PICKAXES: List<Item> = listOf(
			Items.WOODEN_PICKAXE,
			Items.STONE_PICKAXE,
			Items.IRON_PICKAXE,
			Items.DIAMOND_PICKAXE
		)
	}
}
