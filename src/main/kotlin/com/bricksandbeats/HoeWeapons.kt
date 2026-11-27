package com.bricksandbeats

import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EquipmentSlotGroup
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.Item
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.item.component.Weapon
import java.util.function.BiConsumer
import java.util.function.Predicate

/** Делает все мотыги (ванильные и совместимые по id *_hoe) полноценным оружием. */
object HoeWeapons {
	/** Итоговый урон мотыги (как в подсказке предмета). Неизвестные мотыги получают 4. */
	private val DAMAGE = mapOf(
		"wooden_hoe" to 3.0,
		"stone_hoe" to 4.0,
		"golden_hoe" to 3.0,
		"iron_hoe" to 5.0,
		"diamond_hoe" to 6.0,
		"netherite_hoe" to 7.0
	)
	private const val DEFAULT_DAMAGE = 4.0

	/** Итоговая скорость атаки: быстрее топора, чуть быстрее меча. */
	private const val ATTACK_SPEED = 2.2

	private val BASE_DAMAGE_ID: Identifier = Identifier.withDefaultNamespace("base_attack_damage")
	private val BASE_SPEED_ID: Identifier = Identifier.withDefaultNamespace("base_attack_speed")

	private fun isHoe(item: Item): Boolean {
		val key = BuiltInRegistries.ITEM.getKey(item)
		return key.namespace == "minecraft" && key.path.endsWith("_hoe")
	}

	fun register() {
		DefaultItemComponentEvents.MODIFY.register { context ->
			context.modify(
				Predicate<Item> { isHoe(it) },
				BiConsumer<DataComponentMap.Builder, Item> { builder, item ->
					val total = DAMAGE[BuiltInRegistries.ITEM.getKey(item).path] ?: DEFAULT_DAMAGE
					val modifiers = ItemAttributeModifiers.builder()
						.add(
							Attributes.ATTACK_DAMAGE,
							AttributeModifier(BASE_DAMAGE_ID, total - 1.0, AttributeModifier.Operation.ADD_VALUE),
							EquipmentSlotGroup.MAINHAND
						)
						.add(
							Attributes.ATTACK_SPEED,
							AttributeModifier(BASE_SPEED_ID, ATTACK_SPEED - 4.0, AttributeModifier.Operation.ADD_VALUE),
							EquipmentSlotGroup.MAINHAND
						)
						.build()
					builder.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers)
					// Компонент weapon: мотыга считается оружием (статистика «использовано» за удар)
					// и теряет 1 прочность за удар, как меч, а не 2, как инструмент.
					builder.set(DataComponents.WEAPON, Weapon(1, 0.0f))
				}
			)
		}
	}
}
