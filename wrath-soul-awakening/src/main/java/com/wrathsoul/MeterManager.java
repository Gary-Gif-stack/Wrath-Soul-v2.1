package com.wrathsoul;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Events, Rage, Adrenaline, mode switching, bonuses and syncing. */
public final class MeterManager {
	private MeterManager() {}

	private static final Map<UUID, MeterState> STATES = new HashMap<>();

	private static final Identifier DAMAGE_ID = Identifier.of(WrathSoulMod.MOD_ID, "wrath_damage");
	private static final Identifier SPEED_ID = Identifier.of(WrathSoulMod.MOD_ID, "wrath_speed");

	public static MeterState get(ServerPlayerEntity player) {
		return STATES.computeIfAbsent(player.getUuid(), id -> new MeterState());
	}

	public static void init() {
		PayloadTypeRegistry.playC2S().register(ActivateAbilityPayload.ID, ActivateAbilityPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ActivateAbilityPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			switch (payload.ability()) {
				case ActivateAbilityPayload.RAGE -> activateRage(player);
				case ActivateAbilityPayload.ADRENALINE -> activateAdrenaline(player);
				case ActivateAbilityPayload.CAST -> Abilities.cast(player);
				case ActivateAbilityPayload.CYCLE -> Abilities.cycle(player);
				case ActivateAbilityPayload.MODE -> toggleMode(player);
				default -> { }
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				tick(player, server.getTicks());
			}
		});

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			// Something hurts a player wearing/carrying the sword
			if (entity instanceof ServerPlayerEntity player
					&& amount > 0
					&& !player.isInvulnerableTo(source)
					&& player.timeUntilRegen <= 10) {
				onHurt(player, amount, source);
			}
			// A player hits something with a normal attack
			if (!Abilities.suppress
					&& amount > 0
					&& source.getAttacker() instanceof ServerPlayerEntity attacker
					&& source.isOf(DamageTypes.PLAYER_ATTACK)
					&& entity != attacker
					&& entity.timeUntilRegen <= 10) {
				Abilities.onPlayerHit(attacker, entity, amount);
			}
			return true;
		});

		// Kill / boss tracking for the awakening progression
		ServerLivingEntityEvents.AFTER_DEATH.register(Progression::onKill);

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				STATES.remove(handler.getPlayer().getUuid()));

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) STATES.remove(oldPlayer.getUuid());
		});
	}

	// ------------------------------------------------------------------

	private static void onHurt(ServerPlayerEntity player, float damage, DamageSource source) {
		MeterState s = get(player);

		// Adrenaline only resets when ANOTHER PLAYER hurts you, and not once it has been full twice or more.
		boolean hitByPlayer = source.getAttacker() instanceof PlayerEntity attacker && attacker != player;
		boolean protectedFull = s.adr >= Balance.ADR_FILL_TICKS && s.adrFills >= Balance.ADR_PROTECTED_AFTER_FILLS;
		if (hitByPlayer && !protectedFull && s.adrActive == 0 && s.adrCooldown == 0 && s.adr > 0) {
			s.adr = 0;
			s.dirty = true;
			player.sendMessage(Text.literal("Adrenaline lost!").formatted(Formatting.GRAY), true);
		}

		// Rage fills from any damage taken.
		if (WrathSoulItem.carriesBlade(player) && s.rageActive == 0 && s.rage < Balance.RAGE_MAX) {
			float mult = Progression.stageOf(player) >= 3 ? Balance.STAGE3_RAGE_MULT : 1f;
			int gain = Math.max(1, Math.round(damage * Balance.RAGE_PER_DAMAGE * mult));
			s.rage = Math.min(Balance.RAGE_MAX, s.rage + gain);
			s.dirty = true;
			if (s.rage >= Balance.RAGE_MAX && s.rageCooldown == 0) {
				player.sendMessage(Text.literal("Rage is ready!").formatted(Formatting.RED, Formatting.BOLD), true);
				player.getServerWorld().playSound(null, player.getBlockPos(),
						SoundEvents.ENTITY_BLAZE_AMBIENT, SoundCategory.PLAYERS, 1f, 0.6f);
			}
		}
	}

	public static void activateRage(ServerPlayerEntity player) {
		if (!WrathSoulItem.carriesBlade(player)) return;
		MeterState s = get(player);
		if (s.rageActive > 0) return;

		if (s.rageCooldown > 0) {
			player.sendMessage(Text.literal("Rage on cooldown: " + secs(s.rageCooldown) + "s").formatted(Formatting.GRAY), true);
			return;
		}
		if (s.rage < Balance.RAGE_MAX) {
			player.sendMessage(Text.literal("Rage meter isn't full yet").formatted(Formatting.GRAY), true);
			return;
		}

		s.rage = 0;
		s.rageActive = Balance.RAGE_DURATION;
		s.dirty = true;

		ServerWorld world = player.getServerWorld();
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 1.2f, 0.9f);
		world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(),
				20, 0.5, 0.7, 0.5, 0.02);
		player.sendMessage(Text.literal("RAGE!").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
	}

	public static void activateAdrenaline(ServerPlayerEntity player) {
		if (!WrathSoulItem.carriesBlade(player)) return;
		MeterState s = get(player);
		if (s.adrActive > 0) return;

		if (s.adrCooldown > 0) {
			player.sendMessage(Text.literal("Adrenaline on cooldown: " + secs(s.adrCooldown) + "s").formatted(Formatting.GRAY), true);
			return;
		}
		if (s.adr < Balance.ADR_FILL_TICKS) {
			player.sendMessage(Text.literal("Adrenaline isn't full yet").formatted(Formatting.GRAY), true);
			return;
		}

		s.adrActive = Balance.ADR_DURATION;
		s.dirty = true;

		ServerWorld world = player.getServerWorld();
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT,
				SoundCategory.PLAYERS, 1.5f, 1.0f);
		world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.0, player.getZ(),
				25, 0.5, 0.8, 0.5, 0.05);
		player.sendMessage(Text.literal("ADRENALINE!").formatted(Formatting.GOLD, Formatting.BOLD), true);
	}

	/** Switch the held sword between Swift mode (fast, light) and Heavy mode (slow, crushing). */
	public static void toggleMode(ServerPlayerEntity player) {
		ItemStack stack = player.getMainHandStack();
		if (!(stack.getItem() instanceof WrathSoulItem)) return;

		int next = WrathSoulItem.modeOf(stack) == WrathSoulItem.MODE_SWIFT
				? WrathSoulItem.MODE_HEAVY : WrathSoulItem.MODE_SWIFT;
		stack.set(ModComponents.MODE, next);
		stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, WrathSoulItem.modifiersFor(next));

		ServerWorld world = player.getServerWorld();
		if (next == WrathSoulItem.MODE_HEAVY) {
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK,
					SoundCategory.PLAYERS, 1.2f, 0.7f);
			player.sendMessage(Text.literal("HEAVY MODE - slow, crushing blows").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
		} else {
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
					SoundCategory.PLAYERS, 1.0f, 1.7f);
			player.sendMessage(Text.literal("SWIFT MODE - fast, lighter blows").formatted(Formatting.AQUA, Formatting.BOLD), true);
		}
	}

	// ------------------------------------------------------------------

	private static void tick(ServerPlayerEntity player, int serverTick) {
		MeterState s = get(player);
		boolean carrying = WrathSoulItem.carriesBlade(player);
		int stage = Progression.stageOf(player);
		ServerWorld world = player.getServerWorld();

		// ---- Rage timers ----
		if (s.rageActive > 0) {
			s.rageActive--;
			if (s.rageActive == 0) {
				s.rageCooldown = Balance.RAGE_COOLDOWN;
				s.dirty = true;
			} else if (serverTick % 10 == 0) {
				world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY() + 1.2, player.getZ(),
						1, 0.4, 0.5, 0.4, 0.0);
			}
		} else if (s.rageCooldown > 0) {
			s.rageCooldown--;
			if (s.rageCooldown == 0) s.dirty = true;
		}

		// ---- Adrenaline timers ----
		if (s.adrActive > 0) {
			s.adrActive--;
			if (s.adrActive == 0) {
				s.adr = 0;
				s.adrCooldown = Balance.ADR_COOLDOWN;
				s.dirty = true;
			} else if (serverTick % 4 == 0) {
				world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.0, player.getZ(),
						2, 0.4, 0.6, 0.4, 0.02);
			}
		} else if (s.adrCooldown > 0) {
			s.adrCooldown--;
			if (s.adrCooldown == 0) s.dirty = true;
		} else if (carrying && s.adr < Balance.ADR_FILL_TICKS) {
			s.adr++;
			if (stage >= 3 && serverTick % 2 == 0) s.adr++; // Stage III+: fills 50% faster
			if (s.adr >= Balance.ADR_FILL_TICKS) {
				s.adr = Balance.ADR_FILL_TICKS;
				s.adrFills++;
				s.dirty = true;
				world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT,
						SoundCategory.PLAYERS, 1f, 1.4f);
				player.sendMessage(Text.literal("Adrenaline is ready!").formatted(Formatting.GOLD, Formatting.BOLD), true);
			}
		}

		// ---- Ability cooldowns + ability effects ----
		for (int i = 0; i < 4; i++) {
			if (s.abilityCd[i] > 0) {
				s.abilityCd[i]--;
				if (s.abilityCd[i] == 0) s.dirty = true;
			}
		}
		Abilities.tick(player, s, serverTick, stage, carrying);

		// ---- Attribute bonuses ----
		// Damage only applies while the sword is in hand. Ascension / adrenaline / rage do not stack
		// with each other (highest wins), but all stack with Strength potions.
		double damageBonus = 0;
		if (WrathSoulItem.holdsBlade(player)) {
			if (s.ascTicks > 0) damageBonus = Math.max(damageBonus, Balance.ASC_DAMAGE_BONUS);
			if (s.adrActive > 0) damageBonus = Math.max(damageBonus, Balance.ADR_DAMAGE_BONUS);
			if (s.rageActive > 0) damageBonus = Math.max(damageBonus, Balance.RAGE_DAMAGE_BONUS);
		}
		double speedBonus = s.ascTicks > 0 ? Balance.ASC_SPEED_BONUS : (s.adrActive > 0 ? Balance.ADR_SPEED_BONUS : 0);
		applyModifier(player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE),
				DAMAGE_ID, damageBonus, damageBonus > 0);
		applyModifier(player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED),
				SPEED_ID, speedBonus, speedBonus > 0);

		// ---- Sync to client ----
		SyncPayload now = s.toPayload();
		if (s.dirty || (serverTick % 2 == 0 && !now.equals(s.lastSent))) {
			ServerPlayNetworking.send(player, now);
			s.lastSent = now;
			s.dirty = false;
		}
	}

	private static void applyModifier(EntityAttributeInstance inst, Identifier id, double value, boolean want) {
		if (inst == null) return;
		EntityAttributeModifier current = inst.getModifier(id);
		if (!want) {
			if (current != null) inst.removeModifier(id);
			return;
		}
		if (current != null && current.value() == value) return;
		if (current != null) inst.removeModifier(id);
		inst.addTemporaryModifier(new EntityAttributeModifier(id, value,
				EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	private static int secs(int ticks) {
		return (ticks + 19) / 20;
	}
}
