package com.lowestcommonping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldType;

/**
 * Decides which worlds are pinged and listed.
 */
final class WorldFilter
{
	/**
	 * Only worlds whose types are all in this set are considered, so PvP, high risk, Deadman, seasonal, beta,
	 * speedrunning, tournament worlds and any future special world type are never suggested.
	 */
	private static final Set<WorldType> NORMAL_TYPES = EnumSet.of(WorldType.MEMBERS, WorldType.LAST_MAN_STANDING);

	private final MembershipFilter membership;
	private final Set<RegionFilter> regions;
	private final boolean skillTotalWorlds;

	WorldFilter(MembershipFilter membership, Set<RegionFilter> regions, boolean skillTotalWorlds)
	{
		this.membership = membership;
		this.regions = regions;
		this.skillTotalWorlds = skillTotalWorlds;
	}

	static WorldFilter fromConfig(LowestCommonPingConfig config)
	{
		return new WorldFilter(config.membership(), config.regions(), config.skillTotalWorlds());
	}

	boolean matches(World world)
	{
		if (world.getPlayers() < 0)
		{
			// offline
			return false;
		}

		Set<WorldType> types = world.getTypes();
		boolean members = false;
		if (types != null)
		{
			for (WorldType type : types)
			{
				if (type == WorldType.SKILL_TOTAL)
				{
					if (!skillTotalWorlds)
					{
						return false;
					}
				}
				else if (!NORMAL_TYPES.contains(type))
				{
					return false;
				}
			}
			members = types.contains(WorldType.MEMBERS);
		}

		if ((membership == MembershipFilter.MEMBERS && !members) || (membership == MembershipFilter.FREE && members))
		{
			return false;
		}

		if (regions != null && !regions.isEmpty())
		{
			RegionFilter region = RegionFilter.of(world.getRegion());
			return region != null && regions.contains(region);
		}
		return true;
	}

	List<World> filter(Collection<World> worlds)
	{
		List<World> result = new ArrayList<>();
		for (World world : worlds)
		{
			if (matches(world))
			{
				result.add(world);
			}
		}
		return result;
	}
}
