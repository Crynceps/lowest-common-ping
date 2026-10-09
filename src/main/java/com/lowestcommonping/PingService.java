package com.lowestcommonping;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.http.api.worlds.World;

/**
 * Runs pings on a small worker pool, starting at most one ping per dispatch period.
 * Pings block (up to several seconds for unreachable worlds), so they never run on the client thread,
 * the Swing thread or RuneLite's shared executor.
 */
@Slf4j
final class PingService
{
	private final PingTracker tracker;
	private final Pinger pinger;
	private final LongSupplier clock;
	private final BooleanSupplier enabled;
	private final int maxConcurrent;
	private final AtomicInteger running = new AtomicInteger();

	private ScheduledExecutorService scheduler;
	private ExecutorService workers;
	private ScheduledFuture<?> dispatchFuture;

	PingService(PingTracker tracker, Pinger pinger, LongSupplier clock, BooleanSupplier enabled, int maxConcurrent)
	{
		this.tracker = tracker;
		this.pinger = pinger;
		this.clock = clock;
		this.enabled = enabled;
		this.maxConcurrent = maxConcurrent;
	}

	synchronized void start(ScheduledExecutorService scheduler, int pingsPerSecond)
	{
		this.scheduler = scheduler;
		this.workers = Executors.newFixedThreadPool(maxConcurrent, new ThreadFactoryBuilder()
			.setNameFormat("lowest-common-ping-worker-%d")
			.setDaemon(true)
			.build());
		setRate(pingsPerSecond);
	}

	synchronized void setRate(int pingsPerSecond)
	{
		if (scheduler == null)
		{
			return;
		}

		if (dispatchFuture != null)
		{
			dispatchFuture.cancel(false);
		}

		long period = Math.max(100, 1000L / Math.max(1, pingsPerSecond));
		dispatchFuture = scheduler.scheduleWithFixedDelay(this::dispatch, period, period, TimeUnit.MILLISECONDS);
	}

	synchronized void stop()
	{
		if (dispatchFuture != null)
		{
			dispatchFuture.cancel(false);
			dispatchFuture = null;
		}

		if (workers != null)
		{
			workers.shutdownNow();
			workers = null;
		}
		scheduler = null;
	}

	void dispatch()
	{
		try
		{
			if (!enabled.getAsBoolean() || running.get() >= maxConcurrent)
			{
				return;
			}

			ExecutorService pool;
			synchronized (this)
			{
				pool = workers;
			}
			if (pool == null)
			{
				return;
			}

			World world = tracker.next(clock.getAsLong());
			if (world == null)
			{
				return;
			}

			running.incrementAndGet();
			try
			{
				pool.execute(() -> ping(world));
			}
			catch (RejectedExecutionException e)
			{
				running.decrementAndGet();
				tracker.skipped(world.getId(), clock.getAsLong());
			}
		}
		catch (RuntimeException e)
		{
			// an exception would cancel the periodic dispatch task
			log.warn("error dispatching ping", e);
		}
	}

	private void ping(World world)
	{
		int ms = -1;
		try
		{
			ms = pinger.ping(world);
		}
		catch (RuntimeException e)
		{
			log.debug("error pinging world {}", world.getId(), e);
		}
		finally
		{
			tracker.complete(world.getId(), clock.getAsLong(), ms);
			running.decrementAndGet();
		}
	}
}
