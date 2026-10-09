package com.lowestcommonping;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable snapshot of the pings received from one remote party member.
 */
final class MemberPings
{
	private final long memberId;
	private final String name;
	private final Map<Integer, Integer> pings;
	private final long lastHeard;

	MemberPings(long memberId, String name, Map<Integer, Integer> pings, long lastHeard)
	{
		this.memberId = memberId;
		this.name = name;
		this.pings = Collections.unmodifiableMap(new HashMap<>(pings));
		this.lastHeard = lastHeard;
	}

	long getMemberId()
	{
		return memberId;
	}

	/**
	 * @return the sanitized name the member sent, or null
	 */
	String getName()
	{
		return name;
	}

	/**
	 * @return world id to round trip time or {@link PingEstimate#TIMEOUT}
	 */
	Map<Integer, Integer> getPings()
	{
		return pings;
	}

	long getLastHeard()
	{
		return lastHeard;
	}
}
