package com.wrathsoul;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public final class ModItems {
	private ModItems() {}

	public static final Item WRATH_SOUL = Registry.register(
			Registries.ITEM,
			Identifier.of(WrathSoulMod.MOD_ID, "wrath_soul"),
			new WrathSoulItem(new Item.Settings()
					.rarity(Rarity.EPIC)
					.fireproof()
					.component(ModComponents.MODE, WrathSoulItem.MODE_SWIFT)
					.attributeModifiers(WrathSoulItem.modifiersFor(WrathSoulItem.MODE_SWIFT)), false));

	/** Final form. Not craftable: the Wrath Soul turns into this at Stage IV. */
	public static final Item AWAKENED_WRATH_SOUL = Registry.register(
			Registries.ITEM,
			Identifier.of(WrathSoulMod.MOD_ID, "awakened_wrath_soul"),
			new WrathSoulItem(new Item.Settings()
					.rarity(Rarity.EPIC)
					.fireproof()
					.component(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
					.component(ModComponents.MODE, WrathSoulItem.MODE_SWIFT)
					.attributeModifiers(WrathSoulItem.modifiersFor(WrathSoulItem.MODE_SWIFT)), true));

	public static void init() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
			entries.add(WRATH_SOUL);
			entries.add(AWAKENED_WRATH_SOUL);
		});
	}
}
