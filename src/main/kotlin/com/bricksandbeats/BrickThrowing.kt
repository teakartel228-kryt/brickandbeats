package com.bricksandbeats

import com.bricksandbeats.entity.ThrownBrick
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.stats.Stats
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

/**
 * Обычные кирпичи (minecraft:brick) и незерские (minecraft:nether_brick) можно кидать: ПКМ.
 * Работает через событие Fabric, без новых предметов и миксинов.
 * Внешний вид — кастомная 3D-модель, которая подменяет ванильную (assets/minecraft/items/*.json).
 */
object BrickThrowing {
	const val THROW_SPEED = 1.3f
	/** Пауза между бросками, тики (10 = 0.5 сек). */
	const val COOLDOWN_TICKS = 10

	fun isThrowable(item: Item): Boolean = item === Items.BRICK || item === Items.NETHER_BRICK

	fun register() {
		UseItemCallback.EVENT.register(UseItemCallback { player, level, hand ->
			val stack = player.getItemInHand(hand)
			if (player.isSpectator || !isThrowable(stack.item)) return@UseItemCallback InteractionResult.PASS
			if (player.cooldowns.isOnCooldown(stack)) return@UseItemCallback InteractionResult.FAIL

			// Клиент только подтверждает «использование» (улетит пакет на сервер); бросает сервер.
			if (level !is ServerLevel) return@UseItemCallback InteractionResult.SUCCESS

			level.playSound(
				null, player.x, player.y, player.z,
				SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL,
				0.5f, 0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f)
			)
			// В сущность кладём копию на 1 штуку, иначе она «опустеет» вместе со стаком игрока.
			val brick = ThrownBrick(level, player, stack.copyWithCount(1))
			brick.shootFromRotation(player, player.xRot, player.yRot, 0.0f, THROW_SPEED, 1.0f)
			level.addFreshEntity(brick)

			player.awardStat(Stats.ITEM_USED.get(stack.item))
			// Кулдаун ставим ДО consume: потом стак может стать пустым.
			player.cooldowns.addCooldown(stack, COOLDOWN_TICKS)
			stack.consume(1, player)
			InteractionResult.SUCCESS
		})
	}
}
