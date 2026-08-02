package io.github.gregj.mobconduit.bukkit;

import org.bukkit.entity.Enderman;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.PigZombie;

import java.util.Set;

/**
 * What the conduit suppresses: hostile mobs, in the spec's sense — vanilla's {@code Enemy}
 * marker minus {@code NeutralMob}, plus the undead mounts.
 *
 * <p>The Bukkit {@link Enemy} marker lines up with vanilla's {@code Enemy} (Ghast, Slime,
 * Hoglin, Shulker and Phantom all carry it on both sides). There is no Bukkit equivalent of
 * vanilla's {@code NeutralMob} interface, but in 26.2 the only {@code Enemy & NeutralMob}
 * types are endermen and zombified piglins, so the two are excluded by class: they stay
 * spawnable and unswept. Spiders and piglins are {@code Enemy} without {@code NeutralMob} and
 * stay suppressed.
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
	/**
	 * The non-{@code Enemy} mobs the conduit still treats as hostile spawns. 26.2 has exactly
	 * four MONSTER-category types that do not implement {@code Enemy}: these three undead
	 * mounts, which exist to carry a hostile rider in on their backs, and the sulfur cube,
	 * which is a passive, farmable resource mob and is deliberately not listed.
	 */
	private static final Set<EntityType> UNDEAD_MOUNTS = Set.of(
			EntityType.ZOMBIE_HORSE,
			EntityType.CAMEL_HUSK,
			EntityType.ZOMBIE_NAUTILUS);

	private Hostiles() {
	}

	public static boolean isSuppressible(Entity entity) {
		return (entity instanceof Enemy || UNDEAD_MOUNTS.contains(entity.getType()))
				&& !(entity instanceof Enderman)
				&& !(entity instanceof PigZombie);
	}
}
