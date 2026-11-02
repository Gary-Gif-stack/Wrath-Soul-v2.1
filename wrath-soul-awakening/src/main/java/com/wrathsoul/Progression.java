package com.wrathsoul;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** Awakening progression: kills and bosses level the sword up through 4 stages. */
public final class Progression {
	private Progression() {}

	public static final String[] STAGE_NAMES = {"Dormant", "Stirring", "Roused", "Ascendant", "Awakened"};
	private static final String[] UNLOCKS = {
			"",
			"Soul Dash + soul-fire hits",
			"Wrath Nova + lifesteal",
			"Spirit Warrior + faster Rage and Adrenaline",
			"Soul Ascension + soul-fire aura (Awakened Wrath Soul!)"
	};

	// ---------------- lookup ----------------

	public static int stageOf(ItemStack stack) {
		if (!(stack.getItem() instanceof WrathSoulItem item)) return 0;
		if (item.isAwakened()) return 4;
		return Math.min(4, stack.getOrDefault(ModComponents.STAGE, 0));
	}

	/** Slot of the sword: selected hotbar slot first, then the rest of the hotbar, then the offhand (slot 40). -1 if none. */
	public static int findBladeSlot(PlayerEntity player) {
		PlayerInventory inv = player.getInventory();
		if (inv.getStack(inv.selectedSlot).getItem() instanceof WrathSoulItem) return inv.selectedSlot;
		for (int i = 0; i < 9; i++) {
			if (inv.getStack(i).getItem() instanceof WrathSoulItem) return i;
		}
		if (player.getOffHandStack().getItem() instanceof WrathSoulItem) return 40;
		return -1;
	}

	public static int stageOf(PlayerEntity player) {
		int slot = findBladeSlot(player);
		return slot < 0 ? 0 : stageOf(player.getInventory().getStack(slot));
	}

	// ---------------- tracking ----------------

	public static void onKill(LivingEntity victim, DamageSource source) {
		ServerPlayerEntity player = null;
		if (source.getAttacker() instanceof ServerPlayerEntity a) player = a;
		else if (victim.getAttacker() instanceof ServerPlayerEntity b) player = b;
		if (player == null || victim == player) return;

		int slot = findBladeSlot(player);
		if (slot < 0) return;
		PlayerInventory inv = player.getInventory();
		ItemStack stack = inv.getStack(slot);

		int bit = bossBit(victim.getType());
		if (bit != 0) {
			int mask = stack.getOrDefault(ModComponents.BOSSES, 0);
			if ((mask & bit) == 0) {
				stack.set(ModComponents.BOSSES, mask | bit);
				player.sendMessage(Text.literal("The sword drinks the soul of " + victim.getName().getString() + "...")
						.formatted(Formatting.DARK_PURPLE), false);
			}
		} else if (victim instanceof Monster && WrathSoulItem.holdsBlade(player)) {
			stack.set(ModComponents.KILLS, stack.getOrDefault(ModComponents.KILLS, 0) + 1);
		} else {
			return;
		}
		refresh(player, slot);
	}

	private static int bossBit(EntityType<?> type) {
		if (type == EntityType.ELDER_GUARDIAN) return Balance.BOSS_ELDER_GUARDIAN;
		if (type == EntityType.WITHER) return Balance.BOSS_WITHER;
		if (type == EntityType.WARDEN) return Balance.BOSS_WARDEN;
		if (type == EntityType.ENDER_DRAGON) return Balance.BOSS_DRAGON;
		return 0;
	}

	private static boolean requirementsMet(int stage, int kills, int bosses) {
		return switch (stage) {
			case 1 -> kills >= Balance.KILLS_STAGE_1;
			case 2 -> kills >= Balance.KILLS_STAGE_2;
			case 3 -> has(bosses, Balance.BOSS_ELDER_GUARDIAN) && has(bosses, Balance.BOSS_WITHER);
			case 4 -> has(bosses, Balance.BOSS_WARDEN) && has(bosses, Balance.BOSS_DRAGON);
			default -> false;
		};
	}

	private static boolean has(int mask, int bit) {
		return (mask & bit) != 0;
	}

	private static void refresh(ServerPlayerEntity player, int slot) {
		PlayerInventory inv = player.getInventory();
		ItemStack stack = inv.getStack(slot);
		int stage = stageOf(stack);
		int kills = stack.getOrDefault(ModComponents.KILLS, 0);
		int bosses = stack.getOrDefault(ModComponents.BOSSES, 0);

		int target = stage;
		while (target < 4 && requirementsMet(target + 1, kills, bosses)) target++;
		if (target == stage) return;

		if (target >= 4) {
			// Turn into the Awakened Wrath Soul, keeping enchantments, damage, mode, kills...
			ItemStack awakened = new ItemStack(ModItems.AWAKENED_WRATH_SOUL);
			awakened.applyChanges(stack.getComponentChanges());
			awakened.set(ModComponents.STAGE, 4);
			inv.setStack(slot, awakened);
		} else {
			stack.set(ModComponents.STAGE, target);
		}

		var world = player.getServerWorld();
		world.playSound(null, player.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1f, 1f);
		world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.0, player.getZ(),
				40, 0.6, 0.9, 0.6, 0.08);
		player.sendMessage(Text.literal("The Wrath Soul awakens: Stage " + target + " - " + STAGE_NAMES[target])
				.formatted(Formatting.AQUA, Formatting.BOLD), false);
		player.sendMessage(Text.literal("Unlocked: " + UNLOCKS[target]).formatted(Formatting.GOLD), false);
	}

	// ---------------- tooltip ----------------

	public static void addGoalLines(ItemStack stack, List<Text> tooltip) {
		int stage = stageOf(stack);
		int kills = stack.getOrDefault(ModComponents.KILLS, 0);
		int bosses = stack.getOrDefault(ModComponents.BOSSES, 0);
		Formatting f = Formatting.GRAY;
		switch (stage) {
			case 0 -> tooltip.add(Text.literal("Goal: slay hostile mobs " + Math.min(kills, Balance.KILLS_STAGE_1)
					+ "/" + Balance.KILLS_STAGE_1).formatted(f));
			case 1 -> tooltip.add(Text.literal("Goal: slay hostile mobs " + Math.min(kills, Balance.KILLS_STAGE_2)
					+ "/" + Balance.KILLS_STAGE_2).formatted(f));
			case 2 -> tooltip.add(Text.literal("Goal: defeat the Elder Guardian " + box(bosses, Balance.BOSS_ELDER_GUARDIAN)
					+ " and the Wither " + box(bosses, Balance.BOSS_WITHER)).formatted(f));
			case 3 -> tooltip.add(Text.literal("Goal: defeat the Warden " + box(bosses, Balance.BOSS_WARDEN)
					+ " and the Ender Dragon " + box(bosses, Balance.BOSS_DRAGON)).formatted(f));
			default -> tooltip.add(Text.literal("Fully awakened.").formatted(f));
		}
	}

	private static String box(int mask, int bit) {
		return has(mask, bit) ? "[x]" : "[ ]";
	}
}
