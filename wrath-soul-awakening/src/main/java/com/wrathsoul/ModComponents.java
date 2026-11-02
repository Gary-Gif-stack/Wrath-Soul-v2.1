package com.wrathsoul;

import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModComponents {
	private ModComponents() {}

	private static ComponentType<Integer> intType(String name) {
		return Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of(WrathSoulMod.MOD_ID, name),
				ComponentType.<Integer>builder().codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).build());
	}

	/** 0 = Swift mode, 1 = Heavy mode. */
	public static final ComponentType<Integer> MODE = intType("mode");
	/** Awakening stage 0..4 (stored on each sword). */
	public static final ComponentType<Integer> STAGE = intType("stage");
	/** Hostile mobs slain with this sword. */
	public static final ComponentType<Integer> KILLS = intType("kills");
	/** Bit mask of bosses defeated (see Balance.BOSS_*). */
	public static final ComponentType<Integer> BOSSES = intType("bosses");

	public static void init() {}
}
