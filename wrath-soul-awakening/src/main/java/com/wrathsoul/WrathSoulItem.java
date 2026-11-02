package com.wrathsoul;

import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** Used for both the Wrath Soul and the Awakened Wrath Soul (final form). */
public class WrathSoulItem extends SwordItem {
	public static final int MODE_SWIFT = 0;
	public static final int MODE_HEAVY = 1;

	private final boolean awakened;

	public WrathSoulItem(Settings settings, boolean awakened) {
		super(ToolMaterials.NETHERITE, settings);
		this.awakened = awakened;
	}

	public boolean isAwakened() {
		return awakened;
	}

	public static AttributeModifiersComponent modifiersFor(int mode) {
		boolean heavy = mode == MODE_HEAVY;
		double damage = heavy ? Balance.HEAVY_DAMAGE_ATTR : Balance.SWIFT_DAMAGE_ATTR;
		double speed = heavy ? Balance.HEAVY_SPEED_MOD : Balance.SWIFT_SPEED_MOD;
		return AttributeModifiersComponent.builder()
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE,
						new EntityAttributeModifier(Item.BASE_ATTACK_DAMAGE_MODIFIER_ID, damage,
								EntityAttributeModifier.Operation.ADD_VALUE),
						AttributeModifierSlot.MAINHAND)
				.add(EntityAttributes.GENERIC_ATTACK_SPEED,
						new EntityAttributeModifier(Item.BASE_ATTACK_SPEED_MODIFIER_ID, speed,
								EntityAttributeModifier.Operation.ADD_VALUE),
						AttributeModifierSlot.MAINHAND)
				.build();
	}

	public static int modeOf(ItemStack stack) {
		return stack.getOrDefault(ModComponents.MODE, MODE_SWIFT);
	}

	public static String modeName(int mode) {
		return mode == MODE_HEAVY ? "Heavy Mode" : "Swift Mode";
	}

	/** True if the player has a Wrath Soul in the hotbar or offhand. */
	public static boolean carriesBlade(PlayerEntity player) {
		return Progression.findBladeSlot(player) >= 0;
	}

	public static boolean holdsBlade(PlayerEntity player) {
		return player.getMainHandStack().getItem() instanceof WrathSoulItem;
	}

	@Override
	public Text getName(ItemStack stack) {
		Text base = super.getName(stack);
		return awakened ? base.copy().formatted(Formatting.AQUA) : base;
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		int stage = Progression.stageOf(stack);
		tooltip.add(Text.literal("Stage " + stage + ": " + Progression.STAGE_NAMES[stage])
				.formatted(stage >= 4 ? Formatting.AQUA : Formatting.GOLD));
		Progression.addGoalLines(stack, tooltip);

		int mode = modeOf(stack);
		tooltip.add(Text.literal(modeName(mode)).formatted(mode == MODE_HEAVY ? Formatting.DARK_RED : Formatting.AQUA));
		tooltip.add(Text.literal("Rage: fills as you take damage.").formatted(Formatting.RED));
		tooltip.add(Text.literal("Adrenaline: fills over time. Only a player's hit resets it.").formatted(Formatting.GOLD));
		for (int i = 0; i < stage; i++) {
			tooltip.add(Text.literal("Ability: " + Balance.ABILITY_NAMES[i]).formatted(Formatting.DARK_AQUA));
		}
	}
}
