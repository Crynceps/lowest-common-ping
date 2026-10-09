package com.lowestcommonping;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable result of ranking the worlds for the current participants, best world first.
 */
final class Ranking
{
	static final Ranking EMPTY = new Ranking(Collections.emptyList(), Collections.emptyList(),
		RankMetric.WORST_MEMBER, Collections.emptyMap());

	private final List<Participant> participants;
	private final List<WorldRank> rows;
	private final RankMetric metric;
	private final Map<Integer, PingEstimate> localEstimates;

	Ranking(List<Participant> participants, List<WorldRank> rows, RankMetric metric,
		Map<Integer, PingEstimate> localEstimates)
	{
		this.participants = Collections.unmodifiableList(participants);
		this.rows = Collections.unmodifiableList(rows);
		this.metric = metric;
		this.localEstimates = Collections.unmodifiableMap(localEstimates);
	}

	/**
	 * @return the participants; the local player, if present, is first
	 */
	List<Participant> getParticipants()
	{
		return participants;
	}

	List<WorldRank> getRows()
	{
		return rows;
	}

	RankMetric getMetric()
	{
		return metric;
	}

	/**
	 * @return details of the local measurements, used for tooltips
	 */
	PingEstimate getLocalEstimate(int worldId)
	{
		PingEstimate estimate = localEstimates.get(worldId);
		return estimate == null ? PingEstimate.NONE : estimate;
	}

	/**
	 * @return the best world that every participant has a ping for and that is not full, or null
	 */
	WorldRank getBest()
	{
		for (WorldRank row : rows)
		{
			if (row.getTier() != WorldRank.TIER_COMPLETE)
			{
				break;
			}
			if (row.isRecommendable())
			{
				return row;
			}
		}
		return null;
	}
}
