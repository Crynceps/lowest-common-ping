package com.lowestcommonping;

import java.util.Map;

/**
 * A party member (or the local player) that contributes pings to the ranking.
 */
final class Participant
{
	private final long memberId;
	private final String name;
	private final boolean local;
	private final Map<Integer, Integer> pings;

	Participant(long memberId, String name, boolean local, Map<Integer, Integer> pings)
	{
		this.memberId = memberId;
		this.name = name;
		this.local = local;
		this.pings = pings;
	}

	long getMemberId()
	{
		return memberId;
	}

	/**
	 * @return a sanitized display name, never null
	 */
	String getName()
	{
		return name;
	}

	boolean isLocal()
	{
		return local;
	}

	/**
	 * @return the ping for a world: round trip time, {@link PingEstimate#TIMEOUT} or {@link PingEstimate#UNKNOWN}
	 */
	int getPing(int worldId)
	{
		Integer ms = pings.get(worldId);
		return ms == null ? PingEstimate.UNKNOWN : ms;
	}
}
