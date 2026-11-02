package com.wrathsoul;

import java.util.HashSet;
import java.util.Set;

/** Per-player server-side data. */
public class MeterState {
	public int rage, rageActive, rageCooldown;
	public int adr, adrActive, adrCooldown, adrFills;

	public int selected;                          // selected awakening ability (0..3)
	public final int[] abilityCd = new int[4];    // cooldowns per ability
	public int dashTicks, spiritTicks, ascTicks;
	public final Set<Integer> dashHit = new HashSet<>();

	public boolean dirty = true;
	public SyncPayload lastSent = null;

	public SyncPayload toPayload() {
		int[] v = new int[SyncPayload.LENGTH];
		v[SyncPayload.RAGE] = rage;
		v[SyncPayload.RAGE_ACTIVE] = rageActive;
		v[SyncPayload.RAGE_CD] = rageCooldown;
		v[SyncPayload.ADR] = adr;
		v[SyncPayload.ADR_ACTIVE] = adrActive;
		v[SyncPayload.ADR_CD] = adrCooldown;
		v[SyncPayload.SELECTED] = selected;
		for (int i = 0; i < 4; i++) v[SyncPayload.CD0 + i] = abilityCd[i];
		v[SyncPayload.ASC_ACTIVE] = ascTicks;
		v[SyncPayload.SPIRIT_ACTIVE] = spiritTicks;
		return new SyncPayload(v);
	}
}
