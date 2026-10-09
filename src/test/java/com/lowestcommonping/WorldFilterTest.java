package com.lowestcommonping;

import java.util.Collections;
import java.util.EnumSet;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldType;
import static com.lowestcommonping.TestWorlds.AU;
import static com.lowestcommonping.TestWorlds.DE;
import static com.lowestcommonping.TestWorlds.UK;
import static com.lowestcommonping.TestWorlds.US;
import static com.lowestcommonping.TestWorlds.world;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class WorldFilterTest
{
	private static final WorldFilter ALL = new WorldFilter(MembershipFilter.ANY, Collections.emptySet(), false);

	@Test
	public void acceptsNormalWorlds()
	{
		assertTrue(ALL.matches(world(302, UK, EnumSet.of(WorldType.MEMBERS))));
		assertTrue(ALL.matches(world(301, US, EnumSet.noneOf(WorldType.class))));
		assertTrue(ALL.matches(world(345, US, EnumSet.of(WorldType.MEMBERS, WorldType.LAST_MAN_STANDING))));
	}

	@Test
	public void rejectsSpecialWorlds()
	{
		for (WorldType type : EnumSet.of(WorldType.PVP, WorldType.HIGH_RISK, WorldType.DEADMAN, WorldType.SEASONAL,
			WorldType.BETA_WORLD, WorldType.TOURNAMENT, WorldType.FRESH_START_WORLD, WorldType.QUEST_SPEEDRUNNING,
			WorldType.NOSAVE_MODE, WorldType.PVP_ARENA, WorldType.BOUNTY))
		{
			assertFalse(type.name(), ALL.matches(world(400, US, EnumSet.of(WorldType.MEMBERS, type))));
		}
	}

	@Test
	public void skillTotalWorldsAreOptional()
	{
		World skillTotal = world(353, UK, EnumSet.of(WorldType.MEMBERS, WorldType.SKILL_TOTAL));
		assertFalse(ALL.matches(skillTotal));
		assertTrue(new WorldFilter(MembershipFilter.ANY, Collections.emptySet(), true).matches(skillTotal));
	}

	@Test
	public void filtersMembership()
	{
		World members = world(302, UK, EnumSet.of(WorldType.MEMBERS));
		World free = world(301, US, EnumSet.noneOf(WorldType.class));

		WorldFilter membersOnly = new WorldFilter(MembershipFilter.MEMBERS, Collections.emptySet(), false);
		assertTrue(membersOnly.matches(members));
		assertFalse(membersOnly.matches(free));

		WorldFilter freeOnly = new WorldFilter(MembershipFilter.FREE, Collections.emptySet(), false);
		assertFalse(freeOnly.matches(members));
		assertTrue(freeOnly.matches(free));
	}

	@Test
	public void filtersRegions()
	{
		WorldFilter ukAndAu = new WorldFilter(MembershipFilter.ANY,
			EnumSet.of(RegionFilter.UNITED_KINGDOM, RegionFilter.AUSTRALIA), false);

		assertTrue(ukAndAu.matches(world(302, UK, EnumSet.of(WorldType.MEMBERS))));
		assertTrue(ukAndAu.matches(world(303, AU, EnumSet.of(WorldType.MEMBERS))));
		assertFalse(ukAndAu.matches(world(304, DE, EnumSet.of(WorldType.MEMBERS))));
		assertFalse(ukAndAu.matches(world(305, 99, EnumSet.of(WorldType.MEMBERS))));
	}

	@Test
	public void onlyNormalWorldsAreHopTargets()
	{
		assertTrue(WorldFilter.isNormalWorld(world(302, UK, EnumSet.of(WorldType.MEMBERS))));
		assertTrue(WorldFilter.isNormalWorld(world(353, UK, EnumSet.of(WorldType.MEMBERS, WorldType.SKILL_TOTAL))));
		assertFalse(WorldFilter.isNormalWorld(world(325, UK, EnumSet.of(WorldType.MEMBERS, WorldType.PVP))));
		assertFalse(WorldFilter.isNormalWorld(world(365, UK, EnumSet.of(WorldType.MEMBERS, WorldType.HIGH_RISK))));
	}

	@Test
	public void rejectsOfflineWorlds()
	{
		World offline = World.builder().id(302).address("oldschool2.runescape.com").activity("-").location(UK)
			.players(-1).types(EnumSet.of(WorldType.MEMBERS)).build();
		assertFalse(ALL.matches(offline));
	}
}
