package com.bricksandbeats

import com.bricksandbeats.entity.ThrownBrick
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.Mob
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.sqrt

/**
 * Зомби с кирпичами (обычными и незерскими). Кирпичи лежат в левой руке (их видно, и они выпадают при смерти);
 * зомби кидает их в цель с расстояния, пока не кончатся. Подходит всем зомби роя.
 */
object ZombieBricks {
	/** Шанс, что только что появившийся зомби получит кирпичи. */
	const val CARRY_CHANCE = 0.25f
	const val MIN_BRICKS = 3
	const val MAX_BRICKS = 8

	private const val MIN_RANGE = 5.0          // ближе — просто бьёт в ближнем бою
	private const val MAX_RANGE = 18.0
	private const val COOLDOWN_TICKS = 50L     // пауза между бросками
	private const val COOLDOWN_JITTER = 30     // + случайно 0..30 тиков
	private const val THROW_SPEED = 1.1f
	private const val INACCURACY = 6.0f        // чем больше, тем хуже целится

	/** Метка в NBT: бросок кубика на кирпичи делается один раз на зомби, а не при каждой загрузке чанка. */
	private const val ROLLED_TAG = "bricks_and_beats.brick_roll"

	fun equipOnSpawn(mob: Mob) {
		if (!mob.addTag(ROLLED_TAG)) return
		if (mob.random.nextFloat() >= CARRY_CHANCE) return
		val count = MIN_BRICKS + mob.random.nextInt(MAX_BRICKS - MIN_BRICKS + 1)
		// Три четверти — обычные кирпичи, четверть — незерские.
		val item = if (mob.random.nextFloat() < 0.25f) Items.NETHER_BRICK else Items.BRICK
		mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack(item, count))
	}

	/** Вызывается роем для каждого зомби, у которого есть живая цель. */
	fun tick(level: ServerLevel, mob: Mob, nextThrow: MutableMap<Int, Long>) {
		val stack = mob.getItemBySlot(EquipmentSlot.OFFHAND)
		if (stack.isEmpty || !BrickThrowing.isThrowable(stack.item)) return
		val target = mob.target ?: return

		val now = level.gameTime
		if (now < (nextThrow[mob.id] ?: 0L)) return

		val distSqr = mob.distanceToSqr(target)
		if (distSqr < MIN_RANGE * MIN_RANGE || distSqr > MAX_RANGE * MAX_RANGE) return
		if (!mob.hasLineOfSight(target)) return

		val brick = ThrownBrick(level, mob, stack.copyWithCount(1))
		val dx = target.x - mob.x
		val dy = target.getY(0.33) - brick.y     // целимся в туловище
		val dz = target.z - mob.z
		val horizontal = sqrt(dx * dx + dz * dz)
		// Поправка на падение: чем дальше цель, тем выше берём.
		brick.shoot(dx, dy + horizontal * 0.2, dz, THROW_SPEED, INACCURACY)
		level.addFreshEntity(brick)

		mob.swing(InteractionHand.OFF_HAND)
		level.playSound(null, mob.x, mob.y, mob.z, SoundEvents.SNOWBALL_THROW, SoundSource.HOSTILE, 0.8f, 0.8f)

		stack.shrink(1)
		if (stack.isEmpty) mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY)
		nextThrow[mob.id] = now + COOLDOWN_TICKS + mob.random.nextInt(COOLDOWN_JITTER)
	}
}
