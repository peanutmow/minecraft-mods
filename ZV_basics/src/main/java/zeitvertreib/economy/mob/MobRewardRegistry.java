package zeitvertreib.economy.mob;

import net.minecraft.world.entity.EntityType;

import java.util.Map;

public final class MobRewardRegistry {
	public static final Map<EntityType<?>, Integer> REWARDS = Map.ofEntries(
		// Tier 1 — common mobs (1 coin)
		Map.entry(EntityType.ZOMBIE, 1),
		Map.entry(EntityType.SKELETON, 1),
		Map.entry(EntityType.SPIDER, 1),
		Map.entry(EntityType.CAVE_SPIDER, 1),
		Map.entry(EntityType.DROWNED, 1),
		Map.entry(EntityType.HUSK, 1),
		Map.entry(EntityType.STRAY, 1),
		Map.entry(EntityType.SLIME, 1),
		Map.entry(EntityType.SILVERFISH, 1),
		Map.entry(EntityType.ZOMBIE_VILLAGER, 1),
		Map.entry(EntityType.ZOMBIFIED_PIGLIN, 1),

		// Tier 2 — uncommon mobs (2 coins)
		Map.entry(EntityType.CREEPER, 2),
		Map.entry(EntityType.PHANTOM, 2),
		Map.entry(EntityType.PILLAGER, 2),
		Map.entry(EntityType.GUARDIAN, 2),
		Map.entry(EntityType.MAGMA_CUBE, 2),
		Map.entry(EntityType.HOGLIN, 2),
		Map.entry(EntityType.PIGLIN, 2),
		Map.entry(EntityType.SHULKER, 2),

		// Tier 3 — dangerous mobs (3 coins)
		Map.entry(EntityType.ENDERMAN, 3),
		Map.entry(EntityType.WITCH, 3),
		Map.entry(EntityType.BLAZE, 3),
		Map.entry(EntityType.GHAST, 3),
		Map.entry(EntityType.VINDICATOR, 3),
		Map.entry(EntityType.WITHER_SKELETON, 3),

		// Tier 4 — elite mobs (4-5 coins)
		Map.entry(EntityType.PIGLIN_BRUTE, 4),
		Map.entry(EntityType.EVOKER, 5),
		Map.entry(EntityType.RAVAGER, 5),

		// Tier 5 — mini-bosses (10-25 coins)
		Map.entry(EntityType.ELDER_GUARDIAN, 10),
		Map.entry(EntityType.WARDEN, 25),

		// Tier 6 — bosses (50-100 coins)
		Map.entry(EntityType.WITHER, 50),
		Map.entry(EntityType.ENDER_DRAGON, 100)
	);

	private MobRewardRegistry() {}
}
