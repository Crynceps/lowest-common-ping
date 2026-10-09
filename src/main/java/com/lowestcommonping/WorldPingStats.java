package com.lowestcommonping;

import java.util.Arrays;

/**
 * Ring buffer of recent ping samples for one world. Not thread safe; {@link PingTracker} guards access.
 * <p>
 * Estimates use the median rather than the mean, so a single lag spike does not distort a world's ping.
 */
final class WorldPingStats
{
	static final long NEVER = Long.MIN_VALUE;

	private static final int CAPACITY = 64;
	/**
	 * Number of most recent samples used when no sample falls inside the averaging window.
	 */
	private static final int FALLBACK_SAMPLES = 3;

	private final long[] times = new long[CAPACITY];
	// >= 0 is a round trip time in ms, -1 is a failed ping
	private final int[] values = new int[CAPACITY];
	private int next;
	private int size;

	private long lastAttempt = NEVER;
	private int consecutiveFailures;
	private long lastSuccessAt = NEVER;

	void record(long now, int ms)
	{
		times[next] = now;
		values[next] = Math.max(-1, ms);
		next = (next + 1) % CAPACITY;
		if (size < CAPACITY)
		{
			size++;
		}

		lastAttempt = now;
		if (ms < 0)
		{
			consecutiveFailures++;
		}
		else
		{
			consecutiveFailures = 0;
			lastSuccessAt = now;
		}
	}

	/**
	 * Marks an attempt that produced no sample, so the world is not rescheduled immediately.
	 */
	void recordSkipped(long now)
	{
		lastAttempt = now;
	}

	long getLastAttempt()
	{
		return lastAttempt;
	}

	int getConsecutiveFailures()
	{
		return consecutiveFailures;
	}

	/**
	 * Computes the estimate for this world.
	 *
	 * @param now      current time in ms
	 * @param windowMs successful samples newer than this are combined
	 * @param maxAgeMs older measurements are discarded entirely
	 */
	PingEstimate estimate(long now, long windowMs, long maxAgeMs)
	{
		int[] window = new int[size];
		int count = 0;
		boolean loss = false;
		for (int i = 0; i < size; i++)
		{
			int idx = index(i);
			if (now - times[idx] > windowMs)
			{
				break;
			}

			if (values[idx] < 0)
			{
				loss = true;
			}
			else
			{
				window[count++] = values[idx];
			}
		}

		if (count > 0)
		{
			return new PingEstimate(median(window, count), count, loss, now - lastAttempt, true);
		}

		boolean recentSuccess = lastSuccessAt != NEVER && now - lastSuccessAt <= maxAgeMs;
		// an unreachable world keeps showing as a timeout until well after its next retry is due
		if (consecutiveFailures > 0 && now - lastAttempt <= Math.max(maxAgeMs,
			PingTracker.backoff(consecutiveFailures) + 2 * PingTracker.SWEEP_INTERVAL_MS))
		{
			// a single failure after a recent success is treated as packet loss, not as an unreachable world
			if (consecutiveFailures == 1 && recentSuccess)
			{
				return recent(now, maxAgeMs, true);
			}
			return new PingEstimate(PingEstimate.TIMEOUT, 0, true, now - lastAttempt, false);
		}

		if (recentSuccess)
		{
			return recent(now, maxAgeMs, false);
		}

		return PingEstimate.NONE;
	}

	/**
	 * Median of the most recent successful samples, for worlds not pinged within the window.
	 */
	private PingEstimate recent(long now, long maxAgeMs, boolean loss)
	{
		int[] recent = new int[FALLBACK_SAMPLES];
		int count = 0;
		for (int i = 0; i < size && count < FALLBACK_SAMPLES; i++)
		{
			int idx = index(i);
			if (now - times[idx] > maxAgeMs)
			{
				break;
			}

			if (values[idx] >= 0)
			{
				recent[count++] = values[idx];
			}
		}

		if (count == 0)
		{
			return PingEstimate.NONE;
		}
		return new PingEstimate(median(recent, count), count, loss, now - lastSuccessAt, false);
	}

	/**
	 * @return the buffer index of the i-th newest sample
	 */
	private int index(int i)
	{
		return Math.floorMod(next - 1 - i, CAPACITY);
	}

	/**
	 * Lower median, so with two samples a spike never wins.
	 */
	static int median(int[] values, int count)
	{
		int[] sorted = Arrays.copyOf(values, count);
		Arrays.sort(sorted);
		return sorted[(count - 1) / 2];
	}
}
