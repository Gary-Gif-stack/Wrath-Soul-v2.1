package com.wrathsoul;

/** All tuning values in one place. Times are in ticks (20 ticks = 1 second). */
public final class Balance {
	private Balance() {}

	// ---- Rage ----
	public static final int RAGE_MAX = 100;
	public static final float RAGE_PER_DAMAGE = 5f;      // 20 damage taken = full
	public static final int RAGE_DURATION = 30 * 20;     // 30 s
	public static final int RAGE_COOLDOWN = 60 * 20;     // 1 minute
	public static final double RAGE_DAMAGE_BONUS = 0.4;  // x1.4

	// ---- Adrenaline ----
	public static final int ADR_FILL_TICKS = 60 * 20;
	public static final int ADR_DURATION = RAGE_DURATION / 2;   // 15 s
	public static final int ADR_COOLDOWN = 2 * 60 * 20;         // 2 minutes
	public static final double ADR_DAMAGE_BONUS = 1.0;          // x2
	public static final double ADR_SPEED_BONUS = 0.3;           // x1.3
	public static final int ADR_PROTECTED_AFTER_FILLS = 2;      // after this many full fills, player hits can't reset it while full

	// ---- Modes ----
	public static final double SWIFT_DAMAGE_ATTR = 4.0;   // total 5 damage
	public static final double SWIFT_SPEED_MOD = -1.4;    // total 2.6 attack speed
	public static final double HEAVY_DAMAGE_ATTR = 12.0;  // total 13 damage
	public static final double HEAVY_SPEED_MOD = -3.1;    // total 0.9 attack speed

	// ---- Progression (stored on the sword) ----
	public static final int KILLS_STAGE_1 = 50;     // Stage I   Stirring:   50 hostile kills
	public static final int KILLS_STAGE_2 = 200;    // Stage II  Roused:     200 hostile kills
	// Stage III Ascendant: defeat the Elder Guardian + the Wither
	// Stage IV  Awakened:  defeat the Warden + the Ender Dragon
	public static final int BOSS_ELDER_GUARDIAN = 1;
	public static final int BOSS_WITHER = 2;
	public static final int BOSS_WARDEN = 4;
	public static final int BOSS_DRAGON = 8;

	// ---- Passives ----
	public static final float IGNITE_SECONDS = 3f;        // Stage I+: hits ignite
	public static final float LIFESTEAL = 0.08f;          // Stage II+: heal 8% of damage dealt
	public static final float STAGE3_RAGE_MULT = 1.5f;    // Stage III+: rage fills 50% faster (adrenaline too)
	public static final double AURA_RADIUS = 4.0;         // Stage IV: soul-fire aura
	public static final float AURA_DAMAGE = 1.0f;         // per second to nearby hostiles

	// ---- Abilities ----
	public static final int DASH_CD = 8 * 20;
	public static final double DASH_SPEED = 1.7;
	public static final int DASH_TICKS = 8;
	public static final float DASH_DAMAGE_MULT = 1.0f;

	public static final int NOVA_CD = 30 * 20;
	public static final double NOVA_RADIUS = 7.0;
	public static final float NOVA_DAMAGE_MULT = 2.0f;
	public static final double NOVA_KNOCKBACK = 1.4;

	public static final int SPIRIT_CD = 60 * 20;
	public static final int SPIRIT_DURATION = 15 * 20;
	public static final double SPIRIT_RANGE = 8.0;
	public static final float SPIRIT_DAMAGE_MULT = 0.6f;
	public static final int SPIRIT_INTERVAL_SWIFT = 8;    // ghost copies your mode: fast hits...
	public static final int SPIRIT_INTERVAL_HEAVY = 20;   // ...or slow hits

	public static final int ASC_CD = 180 * 20;            // 3 minutes
	public static final int ASC_DURATION = 20 * 20;       // 20 s
	public static final double ASC_DAMAGE_BONUS = 1.2;    // x2.2 (does not stack with rage/adrenaline)
	public static final double ASC_SPEED_BONUS = 0.4;     // x1.4
	public static final double WAVE_RANGE = 5.0;          // flame wave on every hit while ascended
	public static final float WAVE_DAMAGE_MULT = 0.5f;

	public static final String[] ABILITY_NAMES = {"Soul Dash", "Wrath Nova", "Spirit Warrior", "Soul Ascension"};
	public static final int[] ABILITY_COOLDOWNS = {DASH_CD, NOVA_CD, SPIRIT_CD, ASC_CD};
}
