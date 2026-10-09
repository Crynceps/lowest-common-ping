package com.lowestcommonping;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.party.messages.PartyChatMessage;
import net.runelite.client.party.messages.UserSync;
import net.runelite.client.party.messages.WebsocketMessage;
import net.runelite.client.util.RuntimeTypeAdapterFactory;
import net.runelite.http.api.RuneLiteAPI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Checks the message against the same Gson setup the RuneLite party client (WSClient) uses.
 */
public class PartyMessageTest
{
	private static final Gson GSON = RuneLiteAPI.GSON.newBuilder()
		.registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(WebsocketMessage.class)
			.registerSubtype(UserSync.class)
			.registerSubtype(PartyChatMessage.class)
			.registerSubtype(LowestCommonPingUpdate.class))
		.create();

	@Test
	public void roundTripsThroughPartyGson()
	{
		Map<Integer, Integer> pings = new HashMap<>();
		pings.put(301, 25);
		pings.put(330, PingEstimate.TIMEOUT);
		LowestCommonPingUpdate update = new LowestCommonPingUpdate("Zezima",
			LowestCommonPingUpdate.FLAG_FULL | LowestCommonPingUpdate.FLAG_REPLY_REQUESTED, PingCodec.encode(pings));
		update.setMemberId(42);

		String json = GSON.toJson(update, WebsocketMessage.class);
		assertTrue(json, json.contains("\"type\":\"LowestCommonPingUpdate\""));
		// the member id comes from the server, never from the payload
		assertFalse(json, json.contains("memberId"));

		LowestCommonPingUpdate received = (LowestCommonPingUpdate) GSON.fromJson(json, WebsocketMessage.class);
		assertEquals(LowestCommonPingUpdate.PROTOCOL_VERSION, received.getVersion());
		assertEquals("Zezima", received.getName());
		assertTrue(received.isFull());
		assertTrue(received.isReplyRequested());
		assertFalse(received.isStopped());
		assertEquals(pings, PingCodec.decode(received.getData()));
		assertEquals(0, received.getMemberId());
	}

	@Test
	public void toleratesMissingAndUnknownFields()
	{
		LowestCommonPingUpdate received = (LowestCommonPingUpdate) GSON.fromJson(
			"{\"type\":\"LowestCommonPingUpdate\",\"zz\":5}", WebsocketMessage.class);

		assertEquals(LowestCommonPingUpdate.PROTOCOL_VERSION, received.getVersion());
		assertEquals(0, received.getFlags());
		assertNull(received.getName());
		assertNull(received.getData());
	}

	@Test
	public void fullUpdateStaysSmall()
	{
		Map<Integer, Integer> pings = new HashMap<>();
		for (int world = 301; world <= 600; world++)
		{
			pings.put(world, 250);
		}

		String json = GSON.toJson(new LowestCommonPingUpdate("Abcdefghijkl", LowestCommonPingUpdate.FLAG_FULL,
			PingCodec.encode(pings)), WebsocketMessage.class);
		assertTrue(json.length() + " bytes", json.length() < 1700);
	}

	@Test
	public void subscribersFollowEventBusRules()
	{
		// throws if a @Subscribe method is not named on<EventName> or has a bad signature
		new EventBus().register(new LowestCommonPingPlugin());
	}
}
