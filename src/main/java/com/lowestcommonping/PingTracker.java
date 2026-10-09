package com.lowestcommonping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.http.api.worlds.World;

/**
 * Holds the local ping measurements and decides which world to ping next.
 * <p>
 * Worlds are pinged in two lanes so the total ping rate stays low:
 * <ul>
 *     <li>focus worlds (the current best candidates for the party) every {@link #FOCUS_INTERVAL_MS}, which keeps
 *     their rolling value accurate</li>
 *     <li>every other candidate every {@link #SWEEP_INTERVAL_MS}, which is enough to notice a better world</li>
 * </ul>
 * When both lanes have a world due, focus worlds get up to {@link #MAX_FOCUS_STREAK} of every
 * {@code MAX_FOCUS_STREAK + 1} pings, so a low ping rate slows the sweep down before it slows the focus worlds,
 * without ever starving the sweep. Should the sweep still fall a full interval behind, the shares flip so no
 * value expires. Until every world has been measured once, focus worlds are pinged at half their usual rate so
 * the first full picture is quick. Worlds that fail to answer back off exponentially.
 */
final class PingTracker
{
	static final long FOCUS_INTERVAL_MS = 5_000;
	static final long SWEEP_INTERVAL_MS = 180_000;
	static final long BASE_BACKOFF_MS = 30_000;
	static final long MAX_BACKOFF_MS = 6 * 60_000;
	static final int MAX_FOCUS_STREAK = 2;

	private final Map<Integer, WorldPingStats> stats = new HashMap<>();
	private final Set<Integer> inFlight = new HashSet<>();
	private List<World> candidates = Collections.emptyList();
	private Set<Integer> candidateIds = Collections.emptySet();
	private Set<Integer> focus = Collections.emptySet();
	private int focusStreak;
	private int sweepStreak;
	private long discoveringSince = WorldPingStats.NEVER;

	synchronized void setCandidates(Collection<World> worlds)
	{
		candidates = Collections.unmodifiableList(new ArrayList<>(worlds));
		Set<Integer> ids = new HashSet<>();
		for (World world : candidates)
		{
			ids.add(world.getId());
		}
		candidateIds = ids;
		stats.keySet().retainAll(ids);
	}

	synchronized List<World> getCandidates()
	{
		return candidates;
	}

	synchronized Set<Integer> getFocus()
	{
		return focus;
	}

	synchronized void setFocus(Set<Integer> worldIds)
	{
		focus = Collections.unmodifiableSet(new HashSet<>(worldIds));
	}

	/**
	 * Picks the world to ping next and marks it in flight.
	 *
	 * @return the world to ping, or null if no world is due yet
	 */
	synchronized World next(long now)
	{
		World bestFocus = null;
		long focusDue = Long.MAX_VALUE;
		World bestOther = null;
		long otherDue = Long.MAX_VALUE;
		for (World world : candidates)
		{
			int id = world.getId();
			if (inFlight.contains(id))
			{
				continue;
			}

			long due = dueTime(id);
			if (due > now)
			{
				continue;
			}

			if (focus.contains(id))
			{
				if (bestFocus == null || due < focusDue)
				{
					bestFocus = world;
					focusDue = due;
				}
			}
			else if (bestOther == null || due < otherDue)
			{
				bestOther = world;
				otherDue = due;
			}
		}

		// while worlds have never been measured, a quick first full picture matters more than precise focus values,
		// so focus worlds only go first once they are a full interval overdue
		boolean discovering = bestOther != null && otherDue == WorldPingStats.NEVER;
		if (!discovering)
		{
			discoveringSince = WorldPingStats.NEVER;
		}
		else if (discoveringSince == WorldPingStats.NEVER)
		{
			discoveringSince = now;
		}

		// when the ping rate cannot keep up with both lanes, the sweep lane must not fall so far behind that its
		// values expire: once it is a full sweep interval late, it gets the larger share instead
		boolean sweepLate = bestOther != null && (discovering
			? now - discoveringSince > SWEEP_INTERVAL_MS
			: otherDue < now - SWEEP_INTERVAL_MS);

		boolean focusFirst;
		if (bestFocus == null)
		{
			focusFirst = false;
		}
		else if (bestOther == null)
		{
			focusFirst = true;
		}
		else if (sweepLate)
		{
			focusFirst = sweepStreak >= MAX_FOCUS_STREAK;
		}
		else
		{
			focusFirst = focusStreak < MAX_FOCUS_STREAK && (!discovering || focusDue <= now - FOCUS_INTERVAL_MS);
		}

		World pick;
		if (focusFirst)
		{
			pick = bestFocus;
			focusStreak++;
			sweepStreak = 0;
		}
		else
		{
			pick = bestOther;
			focusStreak = 0;
			if (pick != null)
			{
				sweepStreak++;
			}
		}

		if (pick != null)
		{
			inFlight.add(pick.getId());
		}
		return pick;
	}

	private long dueTime(int worldId)
	{
		WorldPingStats s = stats.get(worldId);
		if (s == null || s.getLastAttempt() == WorldPingStats.NEVER)
		{
			return WorldPingStats.NEVER;
		}

		int failures = s.getConsecutiveFailures();
		if (failures > 0)
		{
			return s.getLastAttempt() + backoff(failures);
		}
		return s.getLastAttempt() + (focus.contains(worldId) ? FOCUS_INTERVAL_MS : SWEEP_INTERVAL_MS);
	}

	static long backoff(int failures)
	{
		if (failures <= 0)
		{
			return 0;
		}
		int shift = Math.min(failures - 1, 10);
		return Math.min(MAX_BACKOFF_MS, BASE_BACKOFF_MS << shift);
	}

	/**
	 * Records the result of a ping started by {@link #next(long)}.
	 *
	 * @param ms round trip time, or a negative value if the ping failed
	 */
	synchronized void complete(int worldId, long now, int ms)
	{
		inFlight.remove(worldId);
		if (candidateIds.contains(worldId))
		{
			stats.computeIfAbsent(worldId, k -> new WorldPingStats()).record(now, ms);
		}
	}

	/**
	 * Ends a ping started by {@link #next(long)} without recording a sample.
	 */
	synchronized void skipped(int worldId, long now)
	{
		inFlight.remove(worldId);
		if (candidateIds.contains(worldId))
		{
			stats.computeIfAbsent(worldId, k -> new WorldPingStats()).recordSkipped(now);
		}
	}

	synchronized int getInFlightCount()
	{
		return inFlight.size();
	}

	/**
	 * @return estimates for every candidate world that has a usable measurement
	 */
	synchronized Map<Integer, PingEstimate> estimates(long now, long windowMs, long maxAgeMs)
	{
		Map<Integer, PingEstimate> result = new HashMap<>();
		for (Map.Entry<Integer, WorldPingStats> entry : stats.entrySet())
		{
			PingEstimate estimate = entry.getValue().estimate(now, windowMs, maxAgeMs);
			if (estimate.isKnown())
			{
				result.put(entry.getKey(), estimate);
			}
		}
		return result;
	}

	synchronized void clear()
	{
		stats.clear();
		inFlight.clear();
		focus = Collections.emptySet();
		focusStreak = 0;
		sweepStreak = 0;
		discoveringSince = WorldPingStats.NEVER;
	}
}
