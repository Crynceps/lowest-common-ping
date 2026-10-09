package com.lowestcommonping;

import net.runelite.http.api.worlds.World;

/**
 * One row of the ranking: a world with every participant's ping to it.
 */
final class WorldRank
{
	/**
	 * Every participant has a ping for this world.
	 */
	static final int TIER_COMPLETE = 0;
	/**
	 * Some participants have not measured this world yet.
	 */
	static final int TIER_MISSING = 1;
	/**
	 * At least one participant cannot reach this world.
	 */
	static final int TIER_TIMEOUT = 2;
	/**
	 * Worlds with this many players are full, see the core World Hopper.
	 */
	static final int FULL_WORLD_PLAYERS = 1950;
	/**
	 * Nobody has a ping for this world.
	 */
	static final int TIER_NO_DATA = 3;

	private final World world;
	private final int[] pings;
	private final int worst;
	private final int average;
	private final int score;
	private final int tier;

	WorldRank(World world, int[] pings, int worst, int average, int score, int tier)
	{
		this.world = world;
		this.pings = pings;
		this.worst = worst;
		this.average = average;
		this.score = score;
		this.tier = tier;
	}

	World getWorld()
	{
		return world;
	}

	int getWorldId()
	{
		return world.getId();
	}

	/**
	 * @return the ping of the participant at {@code index} in {@link Ranking#getParticipants()}
	 */
	int getPing(int index)
	{
		return pings[index];
	}

	/**
	 * @return the highest known ping, or {@link PingEstimate#UNKNOWN}
	 */
	int getWorst()
	{
		return worst;
	}

	/**
	 * @return the mean of the known pings, or {@link PingEstimate#UNKNOWN}
	 */
	int getAverage()
	{
		return average;
	}

	/**
	 * @return the value the world is ranked by, or {@link PingEstimate#UNKNOWN}
	 */
	int getScore()
	{
		return score;
	}

	int getTier()
	{
		return tier;
	}

	/**
	 * @return true if every participant has a ping for this world and it is not full
	 */
	boolean isRecommendable()
	{
		return tier == TIER_COMPLETE && world.getPlayers() < FULL_WORLD_PLAYERS;
	}
}
