package io.github.gregj.mobconduit.neoforge;

import io.github.gregj.mobconduit.MobConduit;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge entrypoint; everything else lives in the loader-neutral core. The modid has no
 * hyphen because NeoForge modids cannot contain one.
 */
@Mod("mobconduit")
public final class MobConduitNeoForge {
	public MobConduitNeoForge() {
		MobConduit.init(new NeoForgePlatform());
	}
}
