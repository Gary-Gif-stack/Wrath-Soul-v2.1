package com.wrathsoul;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** The four awakening abilities, hit effects and the per-tick effects. */
public final class Abilities {
	private Abilities() {}

	/** While true, damage dealt by abilities does not trigger on-hit effects (prevents loops). */
	public static boolean suppress = false;

	private static boolean isTarget(ServerPlayerEntity p, LivingEntity e) {
		return e != p && e.isAlive() && !e.isTeammate(p) && (e instanceof Monster || e instanceof PlayerEntity);
	}

	private static float attack(ServerPlayerEntity p) {
		return (float) p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
	}

	private static void hit(ServerPlayerEntity p, LivingEntity target, float dmg) {
		suppress = true;
		try {
			target.damage(p.getServerWorld().getDamageSources().playerAttack(p), dmg);
		} finally {
			suppress = false;
		}
	}

	// ---------------- keys ----------------

	public static void cast(ServerPlayerEntity p) {
		if (!WrathSoulItem.holdsBlade(p)) return;
		int stage = Progression.stageOf(p.getMainHandStack());
		if (stage <= 0) {
			p.sendMessage(Text.literal("No abilities yet - the sword must awaken (see its tooltip)").formatted(Formatting.GRAY), true);
			return;
		}
		MeterState s = MeterManager.get(p);
		if (s.selected >= stage) s.selected = 0;
		int i = s.selected;
		if (s.abilityCd[i] > 0) {
			p.sendMessage(Text.literal(Balance.ABILITY_NAMES[i] + " on cooldown: " + secs(s.abilityCd[i]) + "s").formatted(Formatting.GRAY), true);
			return;
		}
		boolean ok = switch (i) {
			case 0 -> dash(p, s);
			case 1 -> nova(p);
			case 2 -> spirit(p, s);
			default -> ascension(p, s);
		};
		if (ok) {
			s.abilityCd[i] = Balance.ABILITY_COOLDOWNS[i];
			s.dirty = true;
		}
	}

	public static void cycle(ServerPlayerEntity p) {
		if (!WrathSoulItem.holdsBlade(p)) return;
		int stage = Progression.stageOf(p.getMainHandStack());
		if (stage <= 0) {
			p.sendMessage(Text.literal("No abilities unlocked yet").formatted(Formatting.GRAY), true);
			return;
		}
		MeterState s = MeterManager.get(p);
		s.selected = (s.selected + 1) % stage;
		s.dirty = true;
		p.sendMessage(Text.literal("Selected: " + Balance.ABILITY_NAMES[s.selected]).formatted(Formatting.AQUA), true);
	}

	// ---------------- abilities ----------------

	private static boolean dash(ServerPlayerEntity p, MeterState s) {
		double yaw = Math.toRadians(p.getYaw());
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		p.setVelocity(fx * Balance.DASH_SPEED, 0.2, fz * Balance.DASH_SPEED);
		p.velocityModified = true;
		p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
		p.fallDistance = 0;
		s.dashTicks = Balance.DASH_TICKS;
		s.dashHit.clear();
		p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
				SoundCategory.PLAYERS, 1.4f, 1.3f);
		p.sendMessage(Text.literal("SOUL DASH!").formatted(Formatting.AQUA, Formatting.BOLD), true);
		return true;
	}

	private static boolean nova(ServerPlayerEntity p) {
		ServerWorld w = p.getServerWorld();
		float dmg = attack(p) * Balance.NOVA_DAMAGE_MULT;
		double r = Balance.NOVA_RADIUS;
		for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(r, 2.0, r),
				x -> isTarget(p, x))) {
			double dx = e.getX() - p.getX();
			double dz = e.getZ() - p.getZ();
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist > r + e.getWidth() / 2.0) continue;
			hit(p, e, dmg);
			e.setOnFireFor(5f);
			if (dist > 0.01) {
				e.takeKnockback(Balance.NOVA_KNOCKBACK, -dx, -dz);
				e.velocityModified = true;
			}
		}
		for (int k = 0; k < 36; k++) {
			double a = Math.toRadians(k * 10.0);
			for (double ring : new double[]{2.0, 4.0, 6.0}) {
				w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
						p.getX() + Math.cos(a) * ring, p.getY() + 0.3, p.getZ() + Math.sin(a) * ring,
						1, 0.05, 0.1, 0.05, 0.02);
			}
		}
		w.spawnParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 1.0, p.getZ(), 3, 1.0, 0.3, 1.0, 0.0);
		w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.2f, 0.8f);
		p.sendMessage(Text.literal("WRATH NOVA!").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
		return true;
	}

	private static boolean spirit(ServerPlayerEntity p, MeterState s) {
		if (s.spiritTicks > 0) return false;
		s.spiritTicks = Balance.SPIRIT_DURATION;
		p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.PARTICLE_SOUL_ESCAPE.value(),
				SoundCategory.PLAYERS, 1.5f, 0.8f);
		p.sendMessage(Text.literal("SPIRIT WARRIOR!").formatted(Formatting.DARK_AQUA, Formatting.BOLD), true);
		return true;
	}

	private static boolean ascension(ServerPlayerEntity p, MeterState s) {
		if (s.ascTicks > 0) return false;
		s.ascTicks = Balance.ASC_DURATION;
		ServerWorld w = p.getServerWorld();
		w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 0.8f, 1.2f);
		w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 60, 0.6, 1.0, 0.6, 0.12);
		p.sendMessage(Text.literal("SOUL ASCENSION!").formatted(Formatting.AQUA, Formatting.BOLD), true);
		return true;
	}

	// ---------------- on-hit effects ----------------

	/** Called when a player hits something with a normal attack. */
	public static void onPlayerHit(ServerPlayerEntity attacker, LivingEntity victim, float amount) {
		if (!WrathSoulItem.holdsBlade(attacker)) return;
		int stage = Progression.stageOf(attacker.getMainHandStack());
		if (stage >= 1) victim.setOnFireFor(Balance.IGNITE_SECONDS);
		if (stage >= 2) attacker.heal(amount * Balance.LIFESTEAL);
		MeterState s = MeterManager.get(attacker);
		if (s.ascTicks > 0) flameWave(attacker, victim, amount);
	}

	private static void flameWave(ServerPlayerEntity p, LivingEntity victim, float base) {
		ServerWorld w = p.getServerWorld();
		double yaw = Math.toRadians(p.getYaw());
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double cosHalf = Math.cos(Math.toRadians(45));
		double range = Balance.WAVE_RANGE;
		float dmg = base * Balance.WAVE_DAMAGE_MULT;

		for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(range, 1.5, range),
				x -> isTarget(p, x) && x != victim)) {
			double dx = e.getX() - p.getX();
			double dz = e.getZ() - p.getZ();
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist > range || dist < 0.01) continue;
			if ((dx * fx + dz * fz) / dist < cosHalf) continue;
			e.damage(w.getDamageSources().indirectMagic(p, p), dmg);
			e.setOnFireFor(3f);
		}
		for (int r = 1; r <= 5; r++) {
			for (int k = -2; k <= 2; k++) {
				double a = Math.toRadians(k * 20.0);
				double dirX = fx * Math.cos(a) - fz * Math.sin(a);
				double dirZ = fx * Math.sin(a) + fz * Math.cos(a);
				w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
						p.getX() + dirX * r, p.getY() + 1.0, p.getZ() + dirZ * r, 1, 0.05, 0.1, 0.05, 0.01);
			}
		}
	}

	// ---------------- per tick ----------------

	public static void tick(ServerPlayerEntity p, MeterState s, int serverTick, int stage, boolean carrying) {
		ServerWorld w = p.getServerWorld();

		// Soul Dash: damage everything you pass through
		if (s.dashTicks > 0) {
			s.dashTicks--;
			w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 0.8, p.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
			float dmg = attack(p) * Balance.DASH_DAMAGE_MULT;
			for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(1.2),
					x -> isTarget(p, x))) {
				if (s.dashHit.add(e.getId())) {
					hit(p, e, dmg);
					e.setOnFireFor(3f);
				}
			}
		}

		// Spirit Warrior: a ghost circles you and strikes the nearest hostile
		if (s.spiritTicks > 0) {
			s.spiritTicks--;
			double ang = serverTick * 0.18;
			double gx = p.getX() + Math.cos(ang) * 1.7;
			double gz = p.getZ() + Math.sin(ang) * 1.7;
			w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, gx, p.getY() + 1.5, gz, 1, 0.05, 0.05, 0.05, 0.0);
			w.spawnParticles(ParticleTypes.SOUL, gx, p.getY() + 1.0, gz, 1, 0.1, 0.2, 0.1, 0.0);
			w.spawnParticles(ParticleTypes.SOUL, gx, p.getY() + 0.4, gz, 1, 0.1, 0.1, 0.1, 0.0);

			boolean heavy = WrathSoulItem.holdsBlade(p) && WrathSoulItem.modeOf(p.getMainHandStack()) == WrathSoulItem.MODE_HEAVY;
			int interval = heavy ? Balance.SPIRIT_INTERVAL_HEAVY : Balance.SPIRIT_INTERVAL_SWIFT;
			if (WrathSoulItem.holdsBlade(p) && serverTick % interval == 0) {
				LivingEntity best = null;
				double bestD = Double.MAX_VALUE;
				for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(Balance.SPIRIT_RANGE),
						x -> x instanceof Monster && x.isAlive())) {
					double d = e.squaredDistanceTo(p);
					if (d < bestD) {
						bestD = d;
						best = e;
					}
				}
				if (best != null) {
					hit(p, best, attack(p) * Balance.SPIRIT_DAMAGE_MULT);
					w.spawnParticles(ParticleTypes.SWEEP_ATTACK, best.getX(), best.getY() + best.getHeight() / 2, best.getZ(),
							1, 0, 0, 0, 0);
					w.playSound(null, best.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.6f, 1.4f);
				}
			}
			if (s.spiritTicks == 0) {
				s.dirty = true;
				p.sendMessage(Text.literal("The Spirit Warrior fades").formatted(Formatting.GRAY), true);
			}
		}

		// Soul Ascension: visuals (bonuses are applied in MeterManager)
		if (s.ascTicks > 0) {
			s.ascTicks--;
			if (serverTick % 3 == 0) {
				w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 3, 0.4, 0.8, 0.4, 0.03);
			}
			if (s.ascTicks == 0) {
				s.dirty = true;
				p.sendMessage(Text.literal("The ascension ends").formatted(Formatting.GRAY), true);
			}
		}

		// Stage IV aura: burns nearby hostiles once per second
		if (stage >= 4 && carrying && serverTick % 20 == 0) {
			double r = Balance.AURA_RADIUS;
			for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(r, 1.5, r),
					x -> x instanceof Monster && x.isAlive() && x.squaredDistanceTo(p) <= r * r)) {
				e.damage(w.getDamageSources().indirectMagic(p, p), Balance.AURA_DAMAGE);
				e.setOnFireFor(2f);
			}
			for (int k = 0; k < 16; k++) {
				double a = Math.toRadians(k * 22.5);
				w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
						p.getX() + Math.cos(a) * r, p.getY() + 0.2, p.getZ() + Math.sin(a) * r, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
	}

	private static int secs(int ticks) {
		return (ticks + 19) / 20;
	}
}
