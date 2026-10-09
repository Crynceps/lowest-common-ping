package com.lowestcommonping;

/**
 * Immutable summary of the local measurements for a single world.
 */
final class PingEstimate
{
	/**
	 * Ping value meaning "no usable measurement".
	 */
	static final int UNKNOWN = -1;

	/**
	 * Ping value meaning "the world did not answer".
	 */
	static final int TIMEOUT = -2;

	static final PingEstimate NONE = new PingEstimate(UNKNOWN, 0, false, -1, false);

	private final int ms;
	private final int samples;
	private final boolean loss;
	private final long ageMs;
	private final boolean fresh;

	PingEstimate(int ms, int samples, boolean loss, long ageMs, boolean fresh)
	{
		this.ms = ms;
		this.samples = samples;
		this.loss = loss;
		this.ageMs = ageMs;
		this.fresh = fresh;
	}

	/**
	 * @return the round trip time in milliseconds, or {@link #UNKNOWN} / {@link #TIMEOUT}
	 */
	int getMs()
	{
		return ms;
	}

	/**
	 * @return the number of successful samples {@link #getMs()} is the median of
	 */
	int getSamples()
	{
		return samples;
	}

	/**
	 * @return true if a recent ping failed
	 */
	boolean isLoss()
	{
		return loss;
	}

	/**
	 * @return milliseconds since the newest measurement used, or -1 if there is none
	 */
	long getAgeMs()
	{
		return ageMs;
	}

	/**
	 * @return true if the value comes from samples inside the averaging window
	 */
	boolean isFresh()
	{
		return fresh;
	}

	boolean isKnown()
	{
		return ms != UNKNOWN;
	}
}
