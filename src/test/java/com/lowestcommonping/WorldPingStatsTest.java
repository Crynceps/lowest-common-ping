package com.lowestcommonping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class WorldPingStatsTest
{
	private static final long WINDOW = 30_000;
	private static final long MAX_AGE = 600_000;

	@Test
	public void averagesSamplesInWindow()
	{
		WorldPingStats stats = new WorldPingStats();
		stats.record(0, 20);
		stats.record(1_000, 30);
		stats.record(2_000, 41);

		PingEstimate estimate = stats.estimate(2_000, WINDOW, MAX_AGE);
		assertEquals(30, estimate.getMs());
		assertEquals(3, estimate.getSamples());
		assertFalse(estimate.isLoss());
	}

	@Test
	public void ignoresSamplesOutsideWindow()
	{
		WorldPingStats stats = new WorldPingStats();
		stats.record(0, 100);
		stats.record(40_000, 20);

		PingEstimate estimate = stats.estimate(40_000, WINDOW, MAX_AGE);
		assertEquals(20, estimate.getMs());
		assertEquals(1, estimate.getSamples());
	}

	@Test
	public void fallsBackToLastSample()
	{
		WorldPingStats stats = new WorldPingStats();
		stats.record(0, 50);

		PingEstimate estimate = stats.estimate(100_000, WINDOW, MAX_AGE);
		assertEquals(50, estimate.getMs());
		assertEquals(100_000, estimate.getAgeMs());
	}

	@Test
	public void expiresOldMeasurements()
	{
		WorldPingStats stats = new WorldPingStats();
		stats.record(0, 50);

		assertSame(PingEstimate.NONE, stats.estimate(MAX_AGE + 1, WINDOW, MAX_AGE));
	}

	@Test
	public void singleFailureAfterSuccessIsLoss()
	{
		WorldPingStats stats = new WorldPingStats();
		stats.record(0, 50);
		stats.record(100_000, -1);

		PingEstimate estimate = stats.estimate(100_000, WINDOW, MAX_AGE);
		assertEquals(50, estimate.getMs());
		assertTrue(estimate.isLoss());
	}

	@Test
	public void repeatedFailuresAreTimeout()
	{
		WorldPingStats stats = new WorldPingStats();
		stats.record(0, 50);
		stats.record(100_000, -1);
		stats.record(130_000, -1);

		assertEquals(PingEstimate.TIMEOUT, stats.estimate(130_000, WINDOW, MAX_AGE).getMs());
	}

	@Test
	public void failureWithoutSuccessIsTimeout()
	{
		WorldPingStats stats = new WorldPingStats();
		stats.record(0, -1);

		assertEquals(PingEstimate.TIMEOUT, stats.estimate(0, WINDOW, MAX_AGE).getMs());
	}

	@Test
	public void flagsLossInsideWindow()
	{
		WorldPingStats stats = new WorldPingStats();
		stats.record(0, 20);
		stats.record(1_000, -1);
		stats.record(2_000, 30);

		PingEstimate estimate = stats.estimate(2_000, WINDOW, MAX_AGE);
		// lower median of 20 and 30
		assertEquals(20, estimate.getMs());
		assertTrue(estimate.isLoss());
	}

	@Test
	public void ignoresASingleSpike()
	{
		WorldPingStats stats = new WorldPingStats();
		stats.record(0, 20);
		stats.record(4_000, 320);
		stats.record(8_000, 21);

		PingEstimate estimate = stats.estimate(8_000, WINDOW, MAX_AGE);
		assertEquals(21, estimate.getMs());
		assertTrue(estimate.isFresh());
	}

	@Test
	public void spikeDoesNotStickAfterTheWindow()
	{
		WorldPingStats stats = new WorldPingStats();
		stats.record(0, 20);
		stats.record(4_000, 22);
		stats.record(8_000, 320);

		// window empty: median of the last three samples, not the last sample
		PingEstimate estimate = stats.estimate(100_000, WINDOW, MAX_AGE);
		assertEquals(22, estimate.getMs());
		assertFalse(estimate.isFresh());
		assertEquals(92_000, estimate.getAgeMs());
	}

	@Test
	public void medianOfTwoPrefersTheLowerValue()
	{
		assertEquals(20, WorldPingStats.median(new int[]{320, 20}, 2));
		assertEquals(5, WorldPingStats.median(new int[]{5}, 1));
		assertEquals(30, WorldPingStats.median(new int[]{50, 10, 30, 40, 20}, 5));
	}

	@Test
	public void timeoutLastsUntilWellAfterTheNextRetry()
	{
		WorldPingStats stats = new WorldPingStats();
		for (int i = 0; i < 6; i++)
		{
			stats.record(i * 1_000L, -1);
		}

		// six failures: the next retry is 6 minutes out, plus slack for a busy schedule
		long valid = PingTracker.backoff(6) + 2 * PingTracker.SWEEP_INTERVAL_MS;
		assertEquals(PingEstimate.TIMEOUT, stats.estimate(5_000 + valid, WINDOW, MAX_AGE).getMs());
		assertSame(PingEstimate.NONE, stats.estimate(5_000 + valid + 1, WINDOW, MAX_AGE));
	}

	@Test
	public void recoversAfterTimeout()
	{
		WorldPingStats stats = new WorldPingStats();
		stats.record(0, -1);
		stats.record(1_000, -1);
		stats.record(2_000, 40);

		assertEquals(0, stats.getConsecutiveFailures());
		assertEquals(40, stats.estimate(2_000, WINDOW, MAX_AGE).getMs());
	}

	@Test
	public void keepsNewestSamplesWhenFull()
	{
		WorldPingStats stats = new WorldPingStats();
		for (int i = 0; i < 200; i++)
		{
			stats.record(i * 100L, i < 150 ? 500 : 10);
		}

		// the last 50 samples (all 10 ms) cover 5 seconds
		assertEquals(10, stats.estimate(19_900, 4_900, MAX_AGE).getMs());
	}

	@Test
	public void skippedAttemptRecordsNoSample()
	{
		WorldPingStats stats = new WorldPingStats();
		stats.recordSkipped(1_000);

		assertEquals(1_000, stats.getLastAttempt());
		assertSame(PingEstimate.NONE, stats.estimate(1_000, WINDOW, MAX_AGE));
	}
}
