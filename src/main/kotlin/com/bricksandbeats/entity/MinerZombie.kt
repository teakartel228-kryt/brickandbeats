package com.bricksandbeats.entity

import com.bricksandbeats.ZombieHive
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.DifficultyInstance
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.phys.Vec3
import java.util.EnumSet

/** Зомби, который прокапывает путь к цели. Тип скорости задаётся [tier]. */
class MinerZombie(
	type: EntityType<out MinerZombie>,
	level: Level,
	val tier: MinerTier
) : Zombie(type, level) {

	override fun registerGoals() {
		super.registerGoals()
		// Приоритет 1 — выше, чем у атаки (2): пока копает, не мечется.
		goalSelector.addGoal(1, MineTowardsTargetGoal(this))
	}

	override fun populateDefaultEquipmentSlots(random: RandomSource, difficulty: DifficultyInstance) {
		super.populateDefaultEquipmentSlots(random, difficulty)
		setItemSlot(EquipmentSlot.MAINHAND, ItemStack(tier.pickPickaxe(random)))
	}

	class MineTowardsTargetGoal(private val mob: MinerZombie) : Goal() {
		private var lastPos: Vec3 = Vec3.ZERO
		private var stuckPolls = 0
		private var miningPos: BlockPos? = null
		private var progress = 0f

		init {
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK))
		}

		override fun requiresUpdateEveryTick(): Boolean = true

		/** Правило мира mob_griefing: если выключено, копатели не ломают блоки (как криперы и эндермены). */
		private fun griefingAllowed(): Boolean {
			val level = mob.level()
			return level is ServerLevel && level.gameRules.get(GameRules.MOB_GRIEFING)
		}

		override fun canUse(): Boolean {
			if (!griefingAllowed()) return false
			val target = mob.target ?: return false
			if (!target.isAlive) return false
			if (mob.distanceToSqr(target) < 4.0) {
				stuckPolls = 0
				return false
			}
			val now = mob.position()
			if (now.distanceToSqr(lastPos) < 0.01) stuckPolls++ else stuckPolls = 0
			lastPos = now
			if (stuckPolls < 3) return false
			return findBlockToMine(target) != null
		}

		override fun canContinueToUse(): Boolean {
			if (!griefingAllowed()) return false   // правило выключили посреди копания
			val target = mob.target ?: return false
			if (!target.isAlive || mob.distanceToSqr(target) < 4.0) return false
			val pos = miningPos ?: return false
			return isMineable(pos)
		}

		override fun start() {
			miningPos = mob.target?.let { findBlockToMine(it) }
			progress = 0f
		}

		override fun stop() {
			miningPos?.let { mob.level().destroyBlockProgress(mob.id, it, -1) }
			miningPos = null
			progress = 0f
			stuckPolls = 0
		}

		override fun tick() {
			val pos = miningPos ?: return
			val level = mob.level()
			val state = level.getBlockState(pos)
			if (state.isAir) return

			mob.lookControl.setLookAt(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
			mob.navigation.stop()

			val tool = mob.mainHandItem
			val hardness = maxOf(state.getDestroySpeed(level, pos), 0.05f)
			val canHarvest = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state)
			val toolSpeed = tool.getDestroySpeed(state)
			// Формула игрока: скорость / твёрдость / (30 или 100), плюс множитель яйца.
			progress += mob.tier.speedMultiplier * toolSpeed / hardness / (if (canHarvest) 30f else 100f)

			if (mob.tickCount % 6 == 0) {
				mob.swing(InteractionHand.MAIN_HAND)
				level.playSound(null, pos, state.soundType.hitSound, SoundSource.HOSTILE, 0.6f, 0.75f)
				if (level is ServerLevel) ZombieHive.noise(level, Vec3.atCenterOf(pos), ZombieHive.LOUDNESS_MINING, 60)
			}
			level.destroyBlockProgress(mob.id, pos, (progress * 10f).toInt().coerceIn(0, 9))

			if (progress >= 1f) {
				level.destroyBlockProgress(mob.id, pos, -1)
				level.destroyBlock(pos, true, mob)
				miningPos = null
				progress = 0f
				stuckPolls = 0
			}
		}

		private fun isMineable(pos: BlockPos): Boolean {
			val level = mob.level()
			val state: BlockState = level.getBlockState(pos)
			if (state.isAir) return false
			if (state.getDestroySpeed(level, pos) < 0f) return false          // бедрок и т.п.
			if (!state.fluidState.isEmpty) return false
			return !state.getCollisionShape(level, pos).isEmpty               // только то, что мешает пройти
		}

		private fun findBlockToMine(target: LivingEntity): BlockPos? {
			val dir = Direction.getApproximateNearest(target.x - mob.x, 0.0, target.z - mob.z)
			val feet = mob.blockPosition()
			val front = feet.relative(dir)
			val candidates: List<BlockPos> = when {
				target.y > mob.y + 1.2 -> listOf(feet.above(2), front.above(1), front.above(2)) // лесенка вверх
				target.y < mob.y - 1.2 -> listOf(front, front.above(1), front.below(1))        // спуск вниз
				else -> listOf(front, front.above(1))                                          // прямой тоннель
			}
			return candidates.firstOrNull { isMineable(it) }
		}
	}
}
