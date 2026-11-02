package com.wrathsoul;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Arrays;

/** Server -> client: meter values for the HUD, as a fixed list of ints. */
public record SyncPayload(int[] v) implements CustomPayload {
	public static final int RAGE = 0, RAGE_ACTIVE = 1, RAGE_CD = 2;
	public static final int ADR = 3, ADR_ACTIVE = 4, ADR_CD = 5;
	public static final int SELECTED = 6;
	public static final int CD0 = 7;          // CD0..CD0+3 = ability cooldowns
	public static final int ASC_ACTIVE = 11, SPIRIT_ACTIVE = 12;
	public static final int LENGTH = 13;

	public static final CustomPayload.Id<SyncPayload> ID =
			new CustomPayload.Id<>(Identifier.of(WrathSoulMod.MOD_ID, "sync"));

	public static final PacketCodec<RegistryByteBuf, SyncPayload> CODEC = new PacketCodec<>() {
		@Override
		public SyncPayload decode(RegistryByteBuf buf) {
			int[] v = new int[LENGTH];
			for (int i = 0; i < LENGTH; i++) v[i] = buf.readVarInt();
			return new SyncPayload(v);
		}

		@Override
		public void encode(RegistryByteBuf buf, SyncPayload p) {
			for (int i = 0; i < LENGTH; i++) buf.writeVarInt(p.v[i]);
		}
	};

	public static final SyncPayload EMPTY = new SyncPayload(new int[LENGTH]);

	public int get(int index) {
		return v[index];
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof SyncPayload p && Arrays.equals(v, p.v);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(v);
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
