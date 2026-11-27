package com.bricksandbeats

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * «Общий мозг» зомби (по каждому измерению отдельно):
 *  1. Общая цель: если один зомби увидел цель, об этом знают все зомби рядом.
 *  2. Память: последнее место, где видели цель, ещё ~10 секунд остаётся «шумом» — рой идёт туда.
 *  3. Слух: громкие звуки (ломание блоков, бой, взрывы, копание) привлекают зомби в радиусе звука.
 * Работает на событиях Fabric, без миксинов.
 */
object ZombieHive {
	// Радиусы «громкости» в блоках
	const val LOUDNESS_BLOCK_BREAK = 14.0
	const val LOUDNESS_FIGHT = 20.0
	const val LOUDNESS_EXPLOSION = 48.0
	const val LOUDNESS_MINING = 12.0

	private const val INTERVAL = 10L              // как часто думает рой (тики)
	private const val SHARE_RANGE = 30.0          // до какого расстояния до цели делимся целью
	private const val SIGHTING_RADIUS = 24.0      // «шум» от места, где увидели цель
	private const val SIGHTING_TICKS = 200
	private const val NOISE_TICKS = 300
	private const val INVESTIGATE_TICKS = 300
	private const val ARRIVE_DIST_SQR = 9.0
	private const val WALK_SPEED = 1.0

	private class Noise(val pos: Vec3, var radius: Double, var ticksLeft: Int)
	private class Investigation(val pos: Vec3, var ticksLeft: Int)
	private class Hive {
		val mobs = LinkedHashSet<Mob>()
		val noises = ArrayList<Noise>()
		val investigating = HashMap<Int, Investigation>()
		val nextThrow = HashMap<Int, Long>()   // когда зомби снова может кинуть кирпич
	}

	private val hives = HashMap<ResourceKey<Level>, Hive>()

	private fun hiveOf(level: ServerLevel): Hive = hives.getOrPut(level.dimension()) { Hive() }

	/** Типы существ, которые входят в рой. Зомби-свинолюди сюда не входят: они нейтральны. */
	fun isHiveMember(entity: Entity): Boolean {
		if (entity !is Mob) return false
		val t = entity.type
		return t == EntityType.ZOMBIE || t == EntityType.HUSK || t == EntityType.DROWNED ||
			t == EntityType.ZOMBIE_VILLAGER ||
			t == ModEntities.MINER_ZOMBIE_SLOW || t == ModEntities.MINER_ZOMBIE_MEDIUM ||
			t == ModEntities.MINER_ZOMBIE_FAST
	}

	/** Сообщить рою о громком звуке. */
	fun noise(level: ServerLevel, pos: Vec3, radius: Double, ticks: Int = NOISE_TICKS) {
		addNoise(hiveOf(level), pos, radius, ticks)
	}

	private fun addNoise(hive: Hive, pos: Vec3, radius: Double, ticks: Int) {
		for (n in hive.noises) {
			if (n.pos.distanceToSqr(pos) < 9.0) {
				n.radius = maxOf(n.radius, radius)
				n.ticksLeft = maxOf(n.ticksLeft, ticks)
				return
			}
		}
		hive.noises.add(Noise(pos, radius, ticks))
	}

	fun register() {
		ServerEntityEvents.ENTITY_LOAD.register(ServerEntityEvents.Load { entity, world ->
			if (isHiveMember(entity)) {
				hiveOf(world).mobs.add(entity as Mob)
				ZombieBricks.equipOnSpawn(entity)
			}
		})

		ServerEntityEvents.ENTITY_UNLOAD.register(ServerEntityEvents.Unload { entity, world ->
			if (isHiveMember(entity)) {
				val hive = hiveOf(world)
				hive.mobs.remove(entity as Mob)
				hive.investigating.remove(entity.id)
				hive.nextThrow.remove(entity.id)
			} else if (entity.type == EntityType.TNT && entity.removalReason == Entity.RemovalReason.DISCARDED) {
				// Динамит исчезает ровно в момент взрыва.
				noise(world, entity.position(), LOUDNESS_EXPLOSION)
			} else if (entity.type == EntityType.CREEPER &&
				entity.removalReason == Entity.RemovalReason.DISCARDED &&
				world.getNearestPlayer(entity.x, entity.y, entity.z, 24.0, false) != null
			) {
				// Крипер, исчезнувший рядом с игроком, а не пропавший по правилам деспауна, — взорвался.
				noise(world, entity.position(), LOUDNESS_EXPLOSION)
			}
		})

		PlayerBlockBreakEvents.AFTER.register(PlayerBlockBreakEvents.After { world, _, pos, _, _ ->
			if (world is ServerLevel) noise(world, Vec3.atCenterOf(pos), LOUDNESS_BLOCK_BREAK)
		})

		ServerLivingEntityEvents.AFTER_DAMAGE.register(ServerLivingEntityEvents.AfterDamage { entity, source, _, _, _ ->
			val level = entity.level()
			if (level is ServerLevel && (entity is Player || source.entity is Player)) {
				noise(level, entity.position(), LOUDNESS_FIGHT)
			}
		})

		ServerTickEvents.END_SERVER_TICK.register(ServerTickEvents.EndTick { server ->
			for (level in server.allLevels) {
				if (level.gameTime % INTERVAL != 0L) continue
				val hive = hives[level.dimension()] ?: continue
				if (hive.mobs.isEmpty()) {
					hive.noises.clear()
					hive.investigating.clear()
					continue
				}
				think(level, hive)
			}
		})
	}

	private fun isValidTarget(t: LivingEntity?): Boolean {
		if (t == null || !t.isAlive) return false
		if (t is Player && (t.isCreative || t.isSpectator)) return false
		return true
	}

	private fun think(level: ServerLevel, hive: Hive) {
		hive.mobs.removeIf { it.isRemoved || it.level() !== level }

		// старение шумов
		val it = hive.noises.iterator()
		while (it.hasNext()) {
			val n = it.next()
			n.ticksLeft -= INTERVAL.toInt()
			if (n.ticksLeft <= 0) it.remove()
		}

		// кто сейчас видит цель
		val holders = ArrayList<Mob>()
		for (m in hive.mobs) {
			if (isValidTarget(m.target)) holders.add(m)
		}
		// запоминаем, где цель, как «шум»
		for (h in holders) {
			val t = h.target ?: continue
			addNoise(hive, t.position(), SIGHTING_RADIUS, SIGHTING_TICKS)
		}

		val shareSqr = SHARE_RANGE * SHARE_RANGE
		for (m in hive.mobs) {
			if (isValidTarget(m.target)) {
				hive.investigating.remove(m.id)
				continue
			}

			// 1) общая цель: берём ближайшую цель кого-то из роя
			var best: LivingEntity? = null
			var bestD = shareSqr
			for (h in holders) {
				val t = h.target ?: continue
				val d = m.distanceToSqr(t)
				if (d < bestD) {
					bestD = d
					best = t
				}
			}
			if (best != null) {
				m.target = best
				hive.investigating.remove(m.id)
				continue
			}

			// 2) уже идёт на звук
			val inv = hive.investigating[m.id]
			if (inv != null) {
				inv.ticksLeft -= INTERVAL.toInt()
				if (inv.ticksLeft <= 0 || m.distanceToSqr(inv.pos) < ARRIVE_DIST_SQR) {
					hive.investigating.remove(m.id)
				} else {
					m.navigation.moveTo(inv.pos.x, inv.pos.y, inv.pos.z, WALK_SPEED)
					continue
				}
			}

			// 3) выбираем самый «громкий» для него звук
			var bestNoise: Noise? = null
			var bestScore = 0.0
			for (n in hive.noises) {
				val d = m.distanceToSqr(n.pos)
				if (d <= n.radius * n.radius && d > ARRIVE_DIST_SQR) {
					val score = n.radius * n.radius - d
					if (score > bestScore) {
						bestScore = score
						bestNoise = n
					}
				}
			}
			if (bestNoise != null) {
				hive.investigating[m.id] = Investigation(bestNoise.pos, INVESTIGATE_TICKS)
				m.navigation.moveTo(bestNoise.pos.x, bestNoise.pos.y, bestNoise.pos.z, WALK_SPEED)
			}
		}

		// 4) зомби с кирпичами кидают их в цель (копия списка: бросок создаёт новые сущности)
		for (m in ArrayList(hive.mobs)) {
			if (isValidTarget(m.target)) ZombieBricks.tick(level, m, hive.nextThrow)
		}
	}
}
