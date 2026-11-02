package com.wrathsoul.client;

import com.wrathsoul.Balance;
import com.wrathsoul.ModComponents;
import com.wrathsoul.Progression;
import com.wrathsoul.SyncPayload;
import com.wrathsoul.WrathSoulItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

/** Stylish side panel: stage emblem, mode badge, icon rows with glowing bars, ability slots. */
public final class MeterHud {
	private MeterHud() {}

	private static final int PW = 150;       // panel width
	private static final int PAD = 7;
	private static final int ROW_H = 21;

	// stage accent colours (RGB)
	private static final int[] ACCENT = {0x9A88C8, 0xFF8A2A, 0xE8433A, 0xE8B848, 0x5CF2F0};
	// ability colours: dash, nova, spirit, ascension
	private static final int[] ABILITY_COLOR = {0x4FC3FF, 0xFF6A3A, 0x5CE0C8, 0xFFE680};

	// 8x8 pixel icons: 0 rage, 1 adrenaline, 2 dash, 3 nova, 4 spirit, 5 ascension
	private static final String[][] ICONS = {
			{"...##...", "..###...", "..####.#", ".#######", ".#######", "########", ".######.", "..####.."},
			{"....###.", "...###..", "..###...", ".######.", "..#####.", "...###..", "...##...", "..#....."},
			{"#...#...", "##..##..", ".##..##.", "..##..##", "..##..##", ".##..##.", "##..##..", "#...#..."},
			{"#..##..#", ".#.##.#.", "..####..", "########", "########", "..####..", ".#.##.#.", "#..##..#"},
			{"..####..", ".######.", ".#.##.#.", ".######.", ".######.", ".######.", ".######.", ".#.##.#."},
			{"...##...", "...##...", "#..##..#", ".######.", "########", ".######.", "#..##..#", "...##..."}
	};

	public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.options.hudHidden || !WrathSoulItem.carriesBlade(mc.player)) return;

		SyncPayload m = ClientMeters.current;
		TextRenderer tr = mc.textRenderer;
		int stage = Progression.stageOf(mc.player);
		int accent = ACCENT[stage];
		float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 170.0);

		boolean asc = m.get(SyncPayload.ASC_ACTIVE) > 0;
		boolean spirit = m.get(SyncPayload.SPIRIT_ACTIVE) > 0;
		boolean showAwakening = stage < 4;
		boolean showAbilities = stage >= 1;

		int h = PAD + 14 + 5 + ROW_H * 2;
		if (showAwakening) h += ROW_H;
		if (showAbilities) h += 20 + ROW_H;
		if (asc || spirit) h += ROW_H;
		h += PAD - 2;

		int x = ctx.getScaledWindowWidth() - PW - 8;
		int y = Math.max(4, ctx.getScaledWindowHeight() / 2 - h / 2);

		panel(ctx, x, y, PW, h, accent, pulse, stage >= 4);

		int cy = y + PAD;
		header(ctx, tr, mc, x, cy, stage, accent, pulse);
		cy += 14;
		fill(ctx, x + PAD, cy + 2, x + PW - PAD, cy + 3, argb(0x44, accent));
		cy += 5;

		// ---- Rage ----
		{
			int rage = m.get(SyncPayload.RAGE);
			int active = m.get(SyncPayload.RAGE_ACTIVE);
			int cd = m.get(SyncPayload.RAGE_CD);
			String key = key(WrathSoulClient.RAGE_KEY);
			if (active > 0) {
				row(ctx, tr, x, cy, 0, "RAGING", secs(active) + "s", active / (float) Balance.RAGE_DURATION,
						0xFF7A1C, false, true, key, pulse);
			} else if (cd > 0) {
				row(ctx, tr, x, cy, 0, "Rage", secs(cd) + "s", 1f - cd / (float) Balance.RAGE_COOLDOWN,
						0x6A6A78, false, false, key, pulse);
			} else if (rage >= Balance.RAGE_MAX) {
				row(ctx, tr, x, cy, 0, "RAGE", "READY", 1f, 0xFF3030, true, false, key, pulse);
			} else {
				row(ctx, tr, x, cy, 0, "Rage", rage + "%", rage / (float) Balance.RAGE_MAX,
						0xC02020, false, false, key, pulse);
			}
			cy += ROW_H;
		}

		// ---- Adrenaline ----
		{
			int adr = m.get(SyncPayload.ADR);
			int active = m.get(SyncPayload.ADR_ACTIVE);
			int cd = m.get(SyncPayload.ADR_CD);
			String key = key(WrathSoulClient.ADRENALINE_KEY);
			if (active > 0) {
				row(ctx, tr, x, cy, 1, "ADRENALINE", secs(active) + "s", active / (float) Balance.ADR_DURATION,
						0xFFE04A, false, true, key, pulse);
			} else if (cd > 0) {
				row(ctx, tr, x, cy, 1, "Adrenaline", secs(cd) + "s", 1f - cd / (float) Balance.ADR_COOLDOWN,
						0x6A6A78, false, false, key, pulse);
			} else if (adr >= Balance.ADR_FILL_TICKS) {
				row(ctx, tr, x, cy, 1, "ADRENALINE", "READY", 1f, 0xFFD21A, true, false, key, pulse);
			} else {
				row(ctx, tr, x, cy, 1, "Adrenaline", (adr * 100 / Balance.ADR_FILL_TICKS) + "%",
						adr / (float) Balance.ADR_FILL_TICKS, 0xE0A010, false, false, key, pulse);
			}
			cy += ROW_H;
		}

		// ---- Awakening progress ----
		if (showAwakening) {
			int slot = Progression.findBladeSlot(mc.player);
			ItemStack blade = slot >= 0 ? mc.player.getInventory().getStack(slot) : ItemStack.EMPTY;
			int kills = blade.getOrDefault(ModComponents.KILLS, 0);
			int bosses = blade.getOrDefault(ModComponents.BOSSES, 0);
			String label;
			String status;
			float frac;
			switch (stage) {
				case 0 -> {
					label = "Awakening: hostile kills";
					status = Math.min(kills, Balance.KILLS_STAGE_1) + "/" + Balance.KILLS_STAGE_1;
					frac = kills / (float) Balance.KILLS_STAGE_1;
				}
				case 1 -> {
					label = "Awakening: hostile kills";
					status = Math.min(kills, Balance.KILLS_STAGE_2) + "/" + Balance.KILLS_STAGE_2;
					frac = kills / (float) Balance.KILLS_STAGE_2;
				}
				case 2 -> {
					int n = (has(bosses, Balance.BOSS_ELDER_GUARDIAN) ? 1 : 0) + (has(bosses, Balance.BOSS_WITHER) ? 1 : 0);
					label = "Slay: Elder Guardian, Wither";
					status = n + "/2";
					frac = n / 2f;
				}
				default -> {
					int n = (has(bosses, Balance.BOSS_WARDEN) ? 1 : 0) + (has(bosses, Balance.BOSS_DRAGON) ? 1 : 0);
					label = "Slay: Warden, Ender Dragon";
					status = n + "/2";
					frac = n / 2f;
				}
			}
			row(ctx, tr, x, cy, 5, label, status, frac, ACCENT[stage + 1], false, false, null, pulse);
			cy += ROW_H;
		}

		// ---- Ability slots + selected ability ----
		if (showAbilities) {
			int sel = Math.min(m.get(SyncPayload.SELECTED), stage - 1);
			for (int i = 0; i < 4; i++) {
				int sx = x + PAD + i * 18;
				boolean unlocked = i < stage;
				int cd = m.get(SyncPayload.CD0 + i);
				float cdFrac = cd > 0 ? cd / (float) Balance.ABILITY_COOLDOWNS[i] : 0f;
				slot(ctx, sx, cy, ICONS[2 + i], ABILITY_COLOR[i], unlocked, i == sel, cdFrac, accent, pulse);
			}
			text(ctx, tr, "[" + key(WrathSoulClient.CYCLE_KEY) + "] next", x + PAD + 4 * 18 + 4, cy + 3, 0xFF8A809C, 0.7f);
			cy += 20;

			int cd = m.get(SyncPayload.CD0 + sel);
			String name = Balance.ABILITY_NAMES[sel];
			String key = key(WrathSoulClient.CAST_KEY);
			if (cd > 0) {
				row(ctx, tr, x, cy, 2 + sel, name, secs(cd) + "s", 1f - cd / (float) Balance.ABILITY_COOLDOWNS[sel],
						0x6A6A78, false, false, key, pulse);
			} else {
				row(ctx, tr, x, cy, 2 + sel, name.toUpperCase(), "READY", 1f, ABILITY_COLOR[sel], true, false, key, pulse);
			}
			cy += ROW_H;
		}

		// ---- Timed abilities currently active ----
		if (asc) {
			row(ctx, tr, x, cy, 5, "SOUL ASCENSION", secs(m.get(SyncPayload.ASC_ACTIVE)) + "s",
					m.get(SyncPayload.ASC_ACTIVE) / (float) Balance.ASC_DURATION, 0x9FFFFF, false, true, null, pulse);
		} else if (spirit) {
			row(ctx, tr, x, cy, 4, "SPIRIT WARRIOR", secs(m.get(SyncPayload.SPIRIT_ACTIVE)) + "s",
					m.get(SyncPayload.SPIRIT_ACTIVE) / (float) Balance.SPIRIT_DURATION, 0x39D7D7, false, true, null, pulse);
		}
	}

	// ------------------------------------------------------------------
	// building blocks
	// ------------------------------------------------------------------

	private static void panel(DrawContext ctx, int x, int y, int w, int h, int accent, float pulse, boolean awakened) {
		// soft shadow
		fill(ctx, x - 1, y - 1, x + w + 1, y + h + 1, 0x55000000);
		// body
		ctx.fillGradient(x, y, x + w, y + h, 0xCC171024, 0xCC09060F);
		// border
		int borderA = awakened ? (int) (0x80 + 0x70 * pulse) : 0x88;
		outline(ctx, x, y, x + w, y + h, argb(borderA, accent));
		// top highlight + left accent strip
		fill(ctx, x + 1, y + 1, x + w - 1, y + 2, argb(0x40, 0xFFFFFF));
		ctx.fillGradient(x + 1, y + 1, x + 3, y + h - 1, argb(0xFF, accent), argb(0x30, accent));
		// corner studs
		int stud = argb(0xFF, accent);
		fill(ctx, x - 1, y - 1, x + 2, y, stud);
		fill(ctx, x - 1, y - 1, x, y + 2, stud);
		fill(ctx, x + w - 2, y + h, x + w + 1, y + h + 1, stud);
		fill(ctx, x + w, y + h - 2, x + w + 1, y + h + 1, stud);
	}

	private static void header(DrawContext ctx, TextRenderer tr, MinecraftClient mc, int x, int y,
							   int stage, int accent, float pulse) {
		// four stage pips (diamonds)
		for (int i = 0; i < 4; i++) {
			int cx = x + PAD + 3 + i * 9;
			boolean on = stage > i;
			int c = on ? argb(0xFF, i == stage - 1 && stage < 4 ? lerp(accent, 0xFFFFFF, pulse * 0.5f) : accent)
					: 0xFF2B2438;
			diamond(ctx, cx, y + 5, c);
			if (!on) diamondOutline(ctx, cx, y + 5, 0xFF4A4060);
		}
		text(ctx, tr, Progression.STAGE_NAMES[stage].toUpperCase(), x + PAD + 4 * 9 + 4, y + 1,
				argb(0xFF, accent), 0.95f);

		// mode badge (only while the sword is in hand)
		ItemStack held = mc.player.getMainHandStack();
		if (held.getItem() instanceof WrathSoulItem) {
			int mode = WrathSoulItem.modeOf(held);
			boolean heavy = mode == WrathSoulItem.MODE_HEAVY;
			String label = (heavy ? "HEAVY" : "SWIFT") + " " + key(WrathSoulClient.MODE_KEY);
			float s = 0.7f;
			int tw = (int) (tr.getWidth(label) * s);
			int bw = tw + 8;
			int bx = x + PW - PAD - bw;
			int col = heavy ? 0xFF5555 : 0x55FFFF;
			ctx.fillGradient(bx, y, bx + bw, y + 11, argb(0xCC, shade(col, 0.35f)), argb(0xCC, shade(col, 0.15f)));
			outline(ctx, bx, y, bx + bw, y + 11, argb(0xAA, col));
			text(ctx, tr, label, bx + 4, y + 2.5f, argb(0xFF, col), s);
		}
	}

	/** One icon + label + status + glowing bar (+ optional key chip). */
	private static void row(DrawContext ctx, TextRenderer tr, int x, int y, int icon, String label, String status,
							float frac, int color, boolean ready, boolean active, String keyLabel, float pulse) {
		frac = Math.max(0f, Math.min(1f, frac));
		boolean dim = color == 0x6A6A78;

		// icon box
		int ix = x + PAD;
		int iy = y + 2;
		ctx.fillGradient(ix, iy, ix + 13, iy + 13, 0xFF1E1730, 0xFF0E0A18);
		int boxA = ready ? (int) (0x70 + 0x8F * pulse) : (active ? 0xCC : 0x77);
		outline(ctx, ix, iy, ix + 13, iy + 13, argb(boxA, color));
		drawIcon(ctx, ICONS[icon], ix + 2, iy + 2, dim ? argb(0xFF, 0x77778A) : argb(0xFF, color));

		// geometry
		int chipW = keyLabel != null ? 13 : 0;
		int right = x + PW - PAD;
		int bx = ix + 13 + 5;
		int bw = right - bx - (chipW > 0 ? chipW + 4 : 0);
		int by = y + 11;

		// label + status
		int labelColor = dim ? 0xFFA0A0B0 : (ready || active ? 0xFFFFFFFF : 0xFFDCD6E8);
		text(ctx, tr, label, bx, y + 1, labelColor, 0.75f);
		int sw = (int) (tr.getWidth(status) * 0.75f);
		int statusColor = ready ? argb(0xFF, lerp(color, 0xFFFFFF, pulse * 0.6f)) : (dim ? 0xFFA0A0B0 : argb(0xFF, color));
		text(ctx, tr, status, bx + bw - sw, y + 1, statusColor, 0.75f);

		// bar
		fill(ctx, bx - 1, by - 1, bx + bw + 1, by + 6, 0xFF05030A);
		ctx.fillGradient(bx, by, bx + bw, by + 5, 0xFF2A2138, 0xFF140F1E);
		int fw = (int) (bw * frac);
		if (fw > 0) {
			ctx.fillGradient(bx, by, bx + fw, by + 5, argb(0xFF, shade(color, 1.25f)), argb(0xFF, shade(color, 0.7f)));
			fill(ctx, bx, by, bx + fw, by + 1, 0x66FFFFFF);
			if (active || ready) {
				// bright leading edge
				fill(ctx, Math.max(bx, bx + fw - 2), by, bx + fw, by + 5, argb((int) (0x90 + 0x6F * pulse), 0xFFFFFF));
			}
		}
		// quarter ticks
		for (int k = 1; k <= 3; k++) {
			fill(ctx, bx + bw * k / 4, by, bx + bw * k / 4 + 1, by + 5, 0x55000000);
		}
		if (ready) {
			outline(ctx, bx - 1, by - 1, bx + bw + 1, by + 6, argb((int) (0x50 + 0xB0 * pulse), color));
		}

		// key chip
		if (keyLabel != null) {
			int cx = right - chipW;
			ctx.fillGradient(cx, y + 2, cx + chipW, y + 15, 0xFF251D36, 0xFF120D1C);
			outline(ctx, cx, y + 2, cx + chipW, y + 15, 0xFF5B4E7A);
			float s = 0.75f;
			int kw = (int) (tr.getWidth(keyLabel) * s);
			text(ctx, tr, keyLabel, cx + (chipW - kw) / 2f, y + 5, 0xFFFFFFFF, s);
		}
	}

	/** Small square ability slot with cooldown sweep. */
	private static void slot(DrawContext ctx, int x, int y, String[] icon, int color, boolean unlocked,
							 boolean selected, float cdFrac, int accent, float pulse) {
		ctx.fillGradient(x, y, x + 15, y + 15, unlocked ? 0xFF1E1730 : 0xFF0C0914, unlocked ? 0xFF0E0A18 : 0xFF08060E);
		if (unlocked) {
			drawIcon(ctx, icon, x + 3, y + 3, argb(0xFF, color));
			if (cdFrac > 0f) {
				int oh = (int) (13 * cdFrac);
				fill(ctx, x + 1, y + 1, x + 14, y + 1 + oh, 0xB0000000);
			}
		} else {
			drawIcon(ctx, icon, x + 3, y + 3, 0xFF2A2436);
		}
		if (selected && unlocked) {
			outline(ctx, x - 1, y - 1, x + 16, y + 16, argb((int) (0xA0 + 0x5F * pulse), accent));
			outline(ctx, x, y, x + 15, y + 15, argb(0xFF, color));
		} else {
			outline(ctx, x, y, x + 15, y + 15, unlocked ? 0xFF4A4060 : 0xFF241E32);
		}
	}

	// ------------------------------------------------------------------
	// primitives
	// ------------------------------------------------------------------

	private static void drawIcon(DrawContext ctx, String[] rows, int x, int y, int color) {
		for (int r = 0; r < rows.length; r++) {
			String line = rows[r];
			for (int c = 0; c < line.length(); c++) {
				if (line.charAt(c) == '#') fill(ctx, x + c, y + r, x + c + 1, y + r + 1, color);
			}
		}
	}

	private static void diamond(DrawContext ctx, int cx, int cy, int color) {
		fill(ctx, cx, cy - 2, cx + 1, cy - 1, color);
		fill(ctx, cx - 1, cy - 1, cx + 2, cy, color);
		fill(ctx, cx - 2, cy, cx + 3, cy + 1, color);
		fill(ctx, cx - 1, cy + 1, cx + 2, cy + 2, color);
		fill(ctx, cx, cy + 2, cx + 1, cy + 3, color);
	}

	private static void diamondOutline(DrawContext ctx, int cx, int cy, int color) {
		fill(ctx, cx, cy - 3, cx + 1, cy - 2, color);
		fill(ctx, cx - 2, cy - 1, cx - 1, cy, color);
		fill(ctx, cx + 2, cy - 1, cx + 3, cy, color);
		fill(ctx, cx - 3, cy, cx - 2, cy + 1, color);
		fill(ctx, cx + 3, cy, cx + 4, cy + 1, color);
		fill(ctx, cx, cy + 3, cx + 1, cy + 4, color);
	}

	private static void outline(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
		fill(ctx, x1, y1, x2, y1 + 1, color);
		fill(ctx, x1, y2 - 1, x2, y2, color);
		fill(ctx, x1, y1 + 1, x1 + 1, y2 - 1, color);
		fill(ctx, x2 - 1, y1 + 1, x2, y2 - 1, color);
	}

	private static void fill(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
		ctx.fill(x1, y1, x2, y2, color);
	}

	private static void text(DrawContext ctx, TextRenderer tr, String s, float x, float y, int color, float scale) {
		MatrixStack m = ctx.getMatrices();
		m.push();
		m.translate(x, y, 0f);
		m.scale(scale, scale, 1f);
		ctx.drawTextWithShadow(tr, s, 0, 0, color);
		m.pop();
	}

	private static int argb(int a, int rgb) {
		return ((a & 0xFF) << 24) | (rgb & 0xFFFFFF);
	}

	private static int shade(int rgb, float f) {
		int r = Math.min(255, (int) (((rgb >> 16) & 255) * f));
		int g = Math.min(255, (int) (((rgb >> 8) & 255) * f));
		int b = Math.min(255, (int) ((rgb & 255) * f));
		return (r << 16) | (g << 8) | b;
	}

	private static int lerp(int a, int b, float t) {
		int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
		int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
		int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
		return (r << 16) | (g << 8) | bl;
	}

	private static boolean has(int mask, int bit) {
		return (mask & bit) != 0;
	}

	private static String key(KeyBinding k) {
		return k.getBoundKeyLocalizedText().getString();
	}

	private static int secs(int ticks) {
		return (ticks + 19) / 20;
	}
}
