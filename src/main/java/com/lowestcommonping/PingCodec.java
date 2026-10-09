package com.lowestcommonping;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Compact encoding of a world to ping map: four bytes per world (unsigned 16 bit world id, unsigned 16 bit ms),
 * sorted by world id and encoded as unpadded base64url. About 1.4 KB of JSON for 260 worlds.
 */
final class PingCodec
{
	static final int MAX_ENTRIES = 1024;
	static final int MAX_MS = 60_000;

	private static final int TIMEOUT_CODE = 0xFFFF;
	private static final int MAX_WORLD_ID = 0xFFFF;

	private PingCodec()
	{
	}

	/**
	 * @param pings world id to round trip time, or {@link PingEstimate#TIMEOUT}; other negative values are skipped
	 * @return the encoded string, or null if there is nothing to encode
	 */
	static String encode(Map<Integer, Integer> pings)
	{
		Map<Integer, Integer> sorted = new TreeMap<>();
		for (Map.Entry<Integer, Integer> entry : pings.entrySet())
		{
			int world = entry.getKey();
			int ms = entry.getValue();
			if (world <= 0 || world > MAX_WORLD_ID)
			{
				continue;
			}

			if (ms == PingEstimate.TIMEOUT)
			{
				sorted.put(world, TIMEOUT_CODE);
			}
			else if (ms >= 0)
			{
				sorted.put(world, Math.min(ms, MAX_MS));
			}

			if (sorted.size() >= MAX_ENTRIES)
			{
				break;
			}
		}

		if (sorted.isEmpty())
		{
			return null;
		}

		ByteBuffer buffer = ByteBuffer.allocate(sorted.size() * 4);
		for (Map.Entry<Integer, Integer> entry : sorted.entrySet())
		{
			buffer.putShort((short) entry.getKey().intValue());
			buffer.putShort((short) entry.getValue().intValue());
		}
		return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
	}

	/**
	 * @return world id to round trip time or {@link PingEstimate#TIMEOUT}
	 * @throws IllegalArgumentException if the data is malformed
	 */
	static Map<Integer, Integer> decode(String data)
	{
		Map<Integer, Integer> result = new HashMap<>();
		if (data == null || data.isEmpty())
		{
			return result;
		}

		// 4 bytes per entry encode to 16/3 characters
		if (data.length() > (MAX_ENTRIES * 16 + 2) / 3)
		{
			throw new IllegalArgumentException("too many entries");
		}

		byte[] bytes = Base64.getUrlDecoder().decode(data);
		if (bytes.length % 4 != 0)
		{
			throw new IllegalArgumentException("truncated data");
		}

		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		while (buffer.remaining() >= 4)
		{
			int world = buffer.getShort() & 0xFFFF;
			int ms = buffer.getShort() & 0xFFFF;
			if (world == 0)
			{
				continue;
			}

			if (ms == TIMEOUT_CODE)
			{
				result.put(world, PingEstimate.TIMEOUT);
			}
			else if (ms <= MAX_MS)
			{
				result.put(world, ms);
			}
		}
		return result;
	}
}
