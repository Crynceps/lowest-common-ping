package com.lowestcommonping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.runelite.http.api.worlds.World;

final class RankingCalculator
{
	/**
	 * Best world first: worlds everyone has measured, then worlds with missing data, then worlds someone cannot
	 * reach. Within a tier the lowest score wins; ties go to the lower average, then to the less crowded world
	 * (worlds in one data center often have identical pings), then to the lower world number.
	 */
	static final Comparator<WorldRank> ORDER = Comparator
		.comparingInt(WorldRank::getTier)
		.thenComparingInt(r -> unknownLast(r.getScore()))
		.thenComparingInt(r -> unknownLast(r.getAverage()))
		.thenComparingInt(r -> r.getWorld().getPlayers())
		.thenComparingInt(WorldRank::getWorldId);

	private RankingCalculator()
	{
	}

	static Ranking compute(List<World> worlds, List<Participant> participants, RankMetric metric,
		Map<Integer, PingEstimate> localEstimates)
	{
		List<WorldRank> rows = new ArrayList<>(worlds.size());
		for (World world : worlds)
		{
			rows.add(rank(world, participants, metric));
		}
		rows.sort(ORDER);
		return new Ranking(new ArrayList<>(participants), rows, metric, localEstimates);
	}

	static WorldRank rank(World world, List<Participant> participants, RankMetric metric)
	{
		int[] pings = new int[participants.size()];
		int known = 0;
		int timeouts = 0;
		int worst = PingEstimate.UNKNOWN;
		long sum = 0;
		for (int i = 0; i < pings.length; i++)
		{
			int ms = participants.get(i).getPing(world.getId());
			pings[i] = ms;
			if (ms >= 0)
			{
				known++;
				worst = Math.max(worst, ms);
				sum += ms;
			}
			else if (ms == PingEstimate.TIMEOUT)
			{
				timeouts++;
			}
		}

		int average = known > 0 ? (int) Math.round((double) sum / known) : PingEstimate.UNKNOWN;
		int score = metric == RankMetric.AVERAGE ? average : worst;

		int tier;
		if (timeouts > 0)
		{
			tier = WorldRank.TIER_TIMEOUT;
		}
		else if (known == 0)
		{
			tier = WorldRank.TIER_NO_DATA;
		}
		else if (known < pings.length)
		{
			tier = WorldRank.TIER_MISSING;
		}
		else
		{
			tier = WorldRank.TIER_COMPLETE;
		}

		return new WorldRank(world, pings, worst, average, score, tier);
	}

	private static int unknownLast(int ms)
	{
		return ms < 0 ? Integer.MAX_VALUE : ms;
	}
}
