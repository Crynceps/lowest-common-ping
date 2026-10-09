package com.lowestcommonping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldType;
import static com.lowestcommonping.TestWorlds.world;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class RankingCalculatorTest
{
	private static final List<World> WORLDS = Arrays.asList(world(301), world(302), world(303), world(304), world(305));

	private static Participant participant(long id, boolean local, int... worldPingPairs)
	{
		Map<Integer, Integer> pings = new HashMap<>();
		for (int i = 0; i < worldPingPairs.length; i += 2)
		{
			pings.put(worldPingPairs[i], worldPingPairs[i + 1]);
		}
		return new Participant(id, "P" + id, local, pings);
	}

	private static List<Integer> order(Ranking ranking)
	{
		List<Integer> ids = new ArrayList<>();
		for (WorldRank rank : ranking.getRows())
		{
			ids.add(rank.getWorldId());
		}
		return ids;
	}

	@Test
	public void ranksByWorstMember()
	{
		List<Participant> participants = Arrays.asList(
			participant(1, true, 301, 10, 302, 60),
			participant(2, false, 301, 100, 302, 60));

		Ranking ranking = RankingCalculator.compute(WORLDS.subList(0, 2), participants, RankMetric.WORST_MEMBER,
			Collections.emptyMap());

		assertEquals(Arrays.asList(302, 301), order(ranking));
		assertEquals(60, ranking.getBest().getScore());
	}

	@Test
	public void ranksByAverage()
	{
		List<Participant> participants = Arrays.asList(
			participant(1, true, 301, 10, 302, 60),
			participant(2, false, 301, 100, 302, 60));

		Ranking ranking = RankingCalculator.compute(WORLDS.subList(0, 2), participants, RankMetric.AVERAGE,
			Collections.emptyMap());

		assertEquals(Arrays.asList(301, 302), order(ranking));
		assertEquals(55, ranking.getBest().getScore());
	}

	@Test
	public void incompleteWorldsRankAfterCompleteOnes()
	{
		List<Participant> participants = Arrays.asList(
			// 301 complete, 302 missing for member 2, 303 unreachable for member 2, 304 measured by nobody,
			// 305 complete but slow
			participant(1, true, 301, 90, 302, 5, 303, 5, 305, 300),
			participant(2, false, 301, 80, 303, PingEstimate.TIMEOUT, 305, 200));

		Ranking ranking = RankingCalculator.compute(WORLDS, participants, RankMetric.WORST_MEMBER,
			Collections.emptyMap());

		assertEquals(Arrays.asList(301, 305, 302, 303, 304), order(ranking));
		assertEquals(WorldRank.TIER_COMPLETE, ranking.getRows().get(1).getTier());
		assertEquals(WorldRank.TIER_MISSING, ranking.getRows().get(2).getTier());
		assertEquals(WorldRank.TIER_TIMEOUT, ranking.getRows().get(3).getTier());
		assertEquals(WorldRank.TIER_NO_DATA, ranking.getRows().get(4).getTier());
	}

	@Test
	public void keepsEveryParticipantsPing()
	{
		List<Participant> participants = Arrays.asList(
			participant(1, true, 301, 10),
			participant(2, false, 301, PingEstimate.TIMEOUT),
			participant(3, false));

		WorldRank rank = RankingCalculator.rank(world(301), participants, RankMetric.WORST_MEMBER);

		assertEquals(10, rank.getPing(0));
		assertEquals(PingEstimate.TIMEOUT, rank.getPing(1));
		assertEquals(PingEstimate.UNKNOWN, rank.getPing(2));
		assertEquals(10, rank.getWorst());
		assertEquals(10, rank.getAverage());
	}

	@Test
	public void breaksTiesByAverageThenWorld()
	{
		List<Participant> participants = Arrays.asList(
			participant(1, true, 301, 50, 302, 50, 303, 40),
			participant(2, false, 301, 50, 302, 50, 303, 50));

		Ranking ranking = RankingCalculator.compute(WORLDS.subList(0, 3), participants, RankMetric.WORST_MEMBER,
			Collections.emptyMap());

		assertEquals(Arrays.asList(303, 301, 302), order(ranking));
	}

	@Test
	public void noBestWorldWithoutCompleteData()
	{
		List<Participant> participants = Arrays.asList(
			participant(1, true, 301, 10),
			participant(2, false));

		Ranking ranking = RankingCalculator.compute(WORLDS.subList(0, 1), participants, RankMetric.WORST_MEMBER,
			Collections.emptyMap());

		assertNull(ranking.getBest());
	}

	@Test
	public void unreachableForEveryoneIsATimeoutNotMissingData()
	{
		List<Participant> participants = Arrays.asList(
			participant(1, true, 301, PingEstimate.TIMEOUT),
			participant(2, false, 301, PingEstimate.TIMEOUT));

		WorldRank rank = RankingCalculator.rank(world(301), participants, RankMetric.WORST_MEMBER);
		assertEquals(WorldRank.TIER_TIMEOUT, rank.getTier());

		WorldRank solo = RankingCalculator.rank(world(301),
			Collections.singletonList(participant(1, true, 301, PingEstimate.TIMEOUT)), RankMetric.WORST_MEMBER);
		assertEquals(WorldRank.TIER_TIMEOUT, solo.getTier());
	}

	@Test
	public void breaksEqualPingsByPlayerCount()
	{
		World busy = World.builder().id(302).address("a").activity("-").location(TestWorlds.UK).players(1100)
			.types(EnumSet.of(WorldType.MEMBERS)).build();
		World quiet = World.builder().id(330).address("b").activity("-").location(TestWorlds.UK).players(400)
			.types(EnumSet.of(WorldType.MEMBERS)).build();

		Ranking ranking = RankingCalculator.compute(Arrays.asList(busy, quiet),
			Collections.singletonList(participant(1, true, 302, 12, 330, 12)), RankMetric.WORST_MEMBER,
			Collections.emptyMap());

		assertEquals(Arrays.asList(330, 302), order(ranking));
	}

	@Test
	public void neverRecommendsFullWorlds()
	{
		World full = World.builder().id(302).address("a").activity("-").location(TestWorlds.UK)
			.players(WorldRank.FULL_WORLD_PLAYERS).types(EnumSet.of(WorldType.MEMBERS)).build();

		Ranking ranking = RankingCalculator.compute(Arrays.asList(full, world(303)),
			Collections.singletonList(participant(1, true, 302, 5, 303, 50)), RankMetric.WORST_MEMBER,
			Collections.emptyMap());

		assertEquals(302, ranking.getRows().get(0).getWorldId());
		assertEquals(303, ranking.getBest().getWorldId());
	}

	@Test
	public void focusKeepsPreviousWorldsNearTheTop()
	{
		List<World> worlds = new ArrayList<>();
		int[] pairs = new int[40];
		for (int i = 0; i < 20; i++)
		{
			worlds.add(world(301 + i));
			pairs[2 * i] = 301 + i;
			pairs[2 * i + 1] = 10 + i;
		}
		Ranking ranking = RankingCalculator.compute(worlds,
			Collections.singletonList(participant(1, true, pairs)), RankMetric.WORST_MEMBER, Collections.emptyMap());

		// top 8 by ping, plus previous focus worlds still within the top 12; world 315 ranks too low to stay
		Set<Integer> focus = LowestCommonPingPlugin.focusWorlds(ranking, new HashSet<>(Arrays.asList(310, 312, 315)));
		Set<Integer> expected = new HashSet<>(Arrays.asList(301, 302, 303, 304, 305, 306, 307, 308, 310, 312));
		assertEquals(expected, focus);
	}

	@Test
	public void soloRankingUsesOwnPings()
	{
		List<Participant> participants = Collections.singletonList(participant(0, true, 301, 30, 302, 20));

		Ranking ranking = RankingCalculator.compute(WORLDS.subList(0, 2), participants, RankMetric.WORST_MEMBER,
			Collections.emptyMap());

		assertEquals(302, ranking.getBest().getWorldId());
	}
}
