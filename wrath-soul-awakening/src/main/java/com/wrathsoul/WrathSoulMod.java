package com.wrathsoul;

import net.fabricmc.api.ModInitializer;

public class WrathSoulMod implements ModInitializer {
	public static final String MOD_ID = "wrathsoul";

	@Override
	public void onInitialize() {
		ModComponents.init();
		ModItems.init();
		MeterManager.init();
	}
}
