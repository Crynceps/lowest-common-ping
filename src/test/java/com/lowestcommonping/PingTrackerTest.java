package com.lowestcommonping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.http.api.worlds.World;
import static com.lowestcommonping.TestWorlds.world;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class PingTrackerTest
{
	private static final long WINDOW = 30_000;
	private static final long MAX_AGE = 600_000;

	private PingTracker tracker;

	@Before
	public void before()
	{
		tracker = new PingTracker();
		tracker.setCandidates(Arrays.asList(world(301), world(302)));
	}

	@Test
	public void pingsUnmeasuredWorldsFirst()
	{
		assertEquals(301, tracker.next(0).getId());
		tracker.complete(301, 0, 20);
		assertEquals(302, tracker.next(1).getId());
		tracker.complete(302, 1, 30);

		assertNull(tracker.next(2));
		assertEquals(301, tracker.next(PingTracker.SWEEP_INTERVAL_MS).getId());
	}

	@Test
	public void doesNotPingAWorldTwiceAtOnce()
	{
		assertEquals(301, tracker.next(0).getId());
		assertEquals(302, tracker.next(0).getId());
		assertNull(tracker.next(0));
		assertEquals(2, tracker.getInFlightCount());

		tracker.complete(301, 10, 20);
		assertEquals(1, tracker.getInFlightCount());
	}

	@Test
	public void pingsFocusWorldsMoreOften()
	{
		tracker.setFocus(Collections.singleton(302));
		assertEquals(302, tracker.next(0).getId());
		tracker.complete(302, 0, 30);
		assertEquals(301, tracker.next(0).getId());
		tracker.complete(301, 0, 20);

		assertNull(tracker.next(PingTracker.FOCUS_INTERVAL_MS - 1));
		assertEquals(302, tracker.next(PingTracker.FOCUS_INTERVAL_MS).getId());
	}

	@Test
	public void prefersFocusWorldsWhenEquallyDue()
	{
		tracker.setFocus(Collections.singleton(302));
		assertEquals(302, tracker.next(0).getId());
	}

	@Test
	public void focusWorldsGetPriorityWithoutStarvingTheSweep()
	{
		List<World> worlds = new ArrayList<>();
		for (int id = 301; id <= 320; id++)
		{
			worlds.add(world(id));
		}
		tracker.setCandidates(worlds);
		tracker.setFocus(new HashSet<>(Arrays.asList(301, 302, 303)));

		// two focus pings, then one other world, while focus worlds remain due
		assertEquals(Arrays.asList(301, 302, 304, 303, 305, 306), pick(0, 6));
		// while worlds were never measured, a focus world that is just due waits for them
		assertEquals(Arrays.asList(307, 308, 309, 310, 311, 312), pick(PingTracker.FOCUS_INTERVAL_MS, 6));
		// once a full interval overdue, focus worlds go first again
		assertEquals(Arrays.asList(301, 302, 313, 303, 314, 315), pick(PingTracker.FOCUS_INTERVAL_MS * 2, 6));
	}

	@Test
	public void focusWorldsKeepTheirCadenceOnceEverythingIsMeasured()
	{
		tracker.setFocus(Collections.singleton(301));
		pick(0, 2);

		// 302 is in the slow lane; 301 is due every focus interval
		assertEquals(Collections.singletonList(301), pick(PingTracker.FOCUS_INTERVAL_MS, 1));
		assertNull(tracker.next(PingTracker.FOCUS_INTERVAL_MS));
		assertEquals(Collections.singletonList(301), pick(PingTracker.FOCUS_INTERVAL_MS * 2, 1));
	}

	@Test
	public void lowestPingRateNeverLetsValuesExpire()
	{
		List<World> worlds = new ArrayList<>();
		for (int id = 301; id < 551; id++)
		{
			worlds.add(world(id));
		}
		tracker.setCandidates(worlds);
		Set<Integer> focus = new HashSet<>(Arrays.asList(301, 302, 303, 304, 305, 306, 307, 308));
		tracker.setFocus(focus);
		int dead = 400;

		// one ping per second for an hour, results arriving immediately
		Map<Integer, Long> lastPinged = new HashMap<>();
		long maxSweepGap = 0;
		long maxFocusGap = 0;
		long deadFirstFailure = -1;
		int deadNotTimeout = 0;
		for (long now = 0; now < 3_600_000; now += 1_000)
		{
			World world = tracker.next(now);
			if (world != null)
			{
				Long last = lastPinged.put(world.getId(), now);
				if (last != null)
				{
					if (focus.contains(world.getId()))
					{
						maxFocusGap = Math.max(maxFocusGap, now - last);
					}
					else
					{
						maxSweepGap = Math.max(maxSweepGap, now - last);
					}
				}
				tracker.complete(world.getId(), now, world.getId() == dead ? -1 : 20);
				if (world.getId() == dead && deadFirstFailure < 0)
				{
					deadFirstFailure = now;
				}
			}

			if (deadFirstFailure >= 0 && now % 10_000 == 0)
			{
				PingEstimate estimate = tracker.estimates(now, WINDOW, MAX_AGE).get(dead);
				if (estimate == null || estimate.getMs() != PingEstimate.TIMEOUT)
				{
					deadNotTimeout++;
				}
			}
		}

		assertEquals(worlds.size(), lastPinged.size());
		assertTrue("sweep gap " + maxSweepGap, maxSweepGap < MAX_AGE);
		assertTrue("focus gap " + maxFocusGap, maxFocusGap <= 60_000);
		assertEquals(0, deadNotTimeout);
	}

	private List<Integer> pick(long now, int count)
	{
		List<Integer> picks = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			World world = tracker.next(now);
			picks.add(world.getId());
			tracker.complete(world.getId(), now, 20);
		}
		return picks;
	}

	@Test
	public void backsOffAfterFailures()
	{
		tracker.setCandidates(Collections.singletonList(world(301)));
		tracker.next(0);
		tracker.complete(301, 0, -1);

		assertNull(tracker.next(PingTracker.BASE_BACKOFF_MS - 1));
		assertEquals(301, tracker.next(PingTracker.BASE_BACKOFF_MS).getId());
		tracker.complete(301, PingTracker.BASE_BACKOFF_MS, -1);

		assertNull(tracker.next(PingTracker.BASE_BACKOFF_MS * 3 - 1));
		assertEquals(301, tracker.next(PingTracker.BASE_BACKOFF_MS * 3).getId());
	}

	@Test
	public void capsBackoff()
	{
		assertEquals(PingTracker.BASE_BACKOFF_MS, PingTracker.backoff(1));
		assertEquals(PingTracker.BASE_BACKOFF_MS * 2, PingTracker.backoff(2));
		assertEquals(PingTracker.MAX_BACKOFF_MS, PingTracker.backoff(50));
	}

	@Test
	public void dropsWorldsThatAreNoLongerCandidates()
	{
		tracker.next(0);
		tracker.complete(301, 0, 20);
		tracker.setCandidates(Collections.singletonList(world(302)));

		assertTrue(tracker.estimates(0, WINDOW, MAX_AGE).isEmpty());

		// a ping that was in flight while the filter changed is not recorded
		tracker.complete(301, 1, 20);
		assertFalse(tracker.estimates(1, WINDOW, MAX_AGE).containsKey(301));
	}

	@Test
	public void skippedPingDelaysTheWorld()
	{
		tracker.setCandidates(Collections.singletonList(world(301)));
		tracker.next(0);
		tracker.skipped(301, 0);

		assertTrue(tracker.estimates(0, WINDOW, MAX_AGE).isEmpty());
		assertNull(tracker.next(1));
		assertEquals(0, tracker.getInFlightCount());
	}

	@Test
	public void estimatesOnlyKnownWorlds()
	{
		tracker.next(0);
		tracker.complete(301, 0, 20);

		assertEquals(Collections.singleton(301), tracker.estimates(0, WINDOW, MAX_AGE).keySet());
		assertEquals(20, tracker.estimates(0, WINDOW, MAX_AGE).get(301).getMs());
	}
}
