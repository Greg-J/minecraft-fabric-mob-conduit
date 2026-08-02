package io.github.gregj.mobconduit;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * What the conduit suppresses: hostile mobs, in the spec's sense — {@code Enemy} minus
 * {@code NeutralMob}, plus the undead mounts.
 *
 * <p>{@code Enemy} alone catches endermen and zombified piglins, which are neutral until
 * provoked; the spec leaves neutral spawns alone, so they stay spawnable and unswept. In 26.2
 * they are the only two {@code Enemy & NeutralMob} types (wolf, iron golem, polar bear and bee
 * implement {@code NeutralMob} but are not {@code Enemy}; spiders and piglins are {@code Enemy}
 * without {@code NeutralMob} and stay suppressed).
 *
 * <p>{@code Enemy} alone also misses vanilla's mounted spawns: a zombie horse is
 * {@code MobCategory.MONSTER} but extends {@code AbstractHorse}, and vetoing only its rider
 * would leave riderless undead mounts accumulating inside the radius. An explicit mount set
 * rather than the MONSTER category, because the category also holds the sulfur cube — passive
 * and farmable.
 *
 * <p>One predicate, used by both the spawn guard and the erasure sweep, so the two can never
 * drift: anything the guard would veto, the sweep would erase, and vice versa.
 */
public final class Hostiles {
	private Hostiles() {
	}

	public static boolean isSuppressible(Entity entity) {
		return (entity instanceof Enemy || SpawnOrigin.UNDEAD_MOUNTS.contains(entity.getType()))
				&& !(entity instanceof NeutralMob);
	}
}
