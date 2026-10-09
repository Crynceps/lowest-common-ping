package com.lowestcommonping;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PingCodecTest
{
	@Test
	public void roundTrip()
	{
		Map<Integer, Integer> pings = new HashMap<>();
		pings.put(301, 25);
		pings.put(302, PingEstimate.TIMEOUT);
		pings.put(420, 150);
		pings.put(700, 0);

		assertEquals(pings, PingCodec.decode(PingCodec.encode(pings)));
	}

	@Test
	public void skipsUnknownAndInvalidEntries()
	{
		Map<Integer, Integer> pings = new HashMap<>();
		pings.put(301, PingEstimate.UNKNOWN);
		pings.put(0, 5);
		pings.put(70_000, 5);

		assertNull(PingCodec.encode(pings));
	}

	@Test
	public void clampsHighPings()
	{
		Map<Integer, Integer> pings = new HashMap<>();
		pings.put(301, 100_000);

		assertEquals(Integer.valueOf(PingCodec.MAX_MS), PingCodec.decode(PingCodec.encode(pings)).get(301));
	}

	@Test
	public void decodesEmptyData()
	{
		assertTrue(PingCodec.decode(null).isEmpty());
		assertTrue(PingCodec.decode("").isEmpty());
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsInvalidCharacters()
	{
		PingCodec.decode("<html>");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsTruncatedData()
	{
		PingCodec.decode(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[]{1, 45, 0}));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsTooManyEntries()
	{
		byte[] bytes = new byte[(PingCodec.MAX_ENTRIES + 1) * 4];
		PingCodec.decode(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
	}

	@Test
	public void acceptsMaximumEntries()
	{
		Map<Integer, Integer> pings = new HashMap<>();
		for (int i = 1; i <= PingCodec.MAX_ENTRIES; i++)
		{
			pings.put(i, i % 500);
		}

		assertEquals(pings, PingCodec.decode(PingCodec.encode(pings)));
	}

	@Test
	public void ignoresReservedValues()
	{
		// world 301 with 60001 ms, which no sender produces
		byte[] bytes = {0x01, 0x2D, (byte) 0xEA, 0x61};
		assertTrue(PingCodec.decode(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)).isEmpty());
	}

	@Test
	public void encodingIsCompact()
	{
		Map<Integer, Integer> pings = new HashMap<>();
		for (int world = 301; world < 601; world++)
		{
			pings.put(world, world % 400);
		}

		String encoded = PingCodec.encode(pings);
		assertEquals(1600, encoded.length());
		// no padding, so Gson's HTML escaping never expands the payload
		assertTrue(encoded.indexOf('=') < 0);
	}
}
