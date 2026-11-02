package com.wrathsoul.client;

import com.wrathsoul.ActivateAbilityPayload;
import com.wrathsoul.SyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class WrathSoulClient implements ClientModInitializer {
	public static KeyBinding RAGE_KEY;
	public static KeyBinding ADRENALINE_KEY;
	public static KeyBinding CAST_KEY;
	public static KeyBinding CYCLE_KEY;
	public static KeyBinding MODE_KEY;

	private static KeyBinding register(String name, int key) {
		return KeyBindingHelper.registerKeyBinding(
				new KeyBinding(name, InputUtil.Type.KEYSYM, key, "category.wrathsoul"));
	}

	private static void poll(MinecraftClient client, KeyBinding key, int ability) {
		while (key.wasPressed()) {
			if (client.player != null) {
				ClientPlayNetworking.send(new ActivateAbilityPayload(ability));
			}
		}
	}

	@Override
	public void onInitializeClient() {
		RAGE_KEY = register("key.wrathsoul.rage", GLFW.GLFW_KEY_R);
		ADRENALINE_KEY = register("key.wrathsoul.adrenaline", GLFW.GLFW_KEY_G);
		CAST_KEY = register("key.wrathsoul.cast", GLFW.GLFW_KEY_V);
		CYCLE_KEY = register("key.wrathsoul.cycle", GLFW.GLFW_KEY_B);
		MODE_KEY = register("key.wrathsoul.mode", GLFW.GLFW_KEY_Z);

		ClientPlayNetworking.registerGlobalReceiver(SyncPayload.ID,
				(payload, context) -> ClientMeters.current = payload);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			poll(client, RAGE_KEY, ActivateAbilityPayload.RAGE);
			poll(client, ADRENALINE_KEY, ActivateAbilityPayload.ADRENALINE);
			poll(client, CAST_KEY, ActivateAbilityPayload.CAST);
			poll(client, CYCLE_KEY, ActivateAbilityPayload.CYCLE);
			poll(client, MODE_KEY, ActivateAbilityPayload.MODE);
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
				ClientMeters.current = SyncPayload.EMPTY);

		HudRenderCallback.EVENT.register(MeterHud::render);
	}
}
