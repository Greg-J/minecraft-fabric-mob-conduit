package io.github.gregj.mobconduit.fabric;

import io.github.gregj.mobconduit.MobConduit;
import net.fabricmc.api.ModInitializer;

/** Fabric entrypoint; everything else lives in the loader-neutral core. */
public final class MobConduitFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		MobConduit.init(new FabricPlatform());
	}
}
