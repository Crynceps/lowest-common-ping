package com.lowestcommonping;

import net.runelite.http.api.worlds.World;

/**
 * Measures the round trip time to a world. Implementations may block for several seconds.
 */
interface Pinger
{
	/**
	 * @return the round trip time in milliseconds, or a negative value on failure
	 */
	int ping(World world);
}
