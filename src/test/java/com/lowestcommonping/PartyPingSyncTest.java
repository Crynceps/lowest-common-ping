package com.lowestcommonping;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class PartyPingSyncTest
{
	private static final long OTHER = 42;

	private PartyPingSync sync;
	private Map<Integer, Integer> local;

	@Before
	public void before()
	{
		sync = new PartyPingSync();
		local = new HashMap<>();
		local.put(301, 20);
		local.put(302, 100);
	}

	private static LowestCommonPingUpdate update(int flags, Map<Integer, Integer> pings)
	{
		return new LowestCommonPingUpdate("Friend", flags, PingCodec.encode(pings));
	}

	private void receiveFrom(long memberId, long now)
	{
		Map<Integer, Integer> pings = new HashMap<>();
		pings.put(301, 200);
		assertTrue(sync.onUpdate(memberId, update(LowestCommonPingUpdate.FLAG_FULL, pings), now, now));
	}

	private LowestCommonPingUpdate next(long now)
	{
		return next(now, 2);
	}

	private LowestCommonPingUpdate next(long now, int partySize)
	{
		PartyPingSync.Outgoing outgoing = sync.nextMessage(now, local, partySize, "Me");
		return outgoing == null ? null : outgoing.getMessage();
	}

	@Test
	public void helloSendsFullUpdateAskingForReplies()
	{
		sync.requestHello();

		LowestCommonPingUpdate message = next(0);
		assertNotNull(message);
		assertTrue(message.isFull());
		assertTrue(message.isReplyRequested());
		assertEquals("Me", message.getName());
		assertEquals(local, PingCodec.decode(message.getData()));

		assertNull(next(1));
	}

	@Test
	public void sendsNothingWhileAlone()
	{
		sync.requestHello();
		assertNull(next(0, 1));

		// the hello is dropped: whoever joins later asks for replies themselves
		assertNull(next(1, 2));
	}

	@Test
	public void sendsNothingPeriodicWithoutOtherUsers()
	{
		assertNull(next(0));
		assertNull(next(PartyPingSync.FULL_INTERVAL_MS * 2));
	}

	@Test
	public void repliesToRequestsAfterTheDelay()
	{
		sync.requestFull(1_000);

		assertNull(next(999));
		LowestCommonPingUpdate message = next(1_000);
		assertNotNull(message);
		assertTrue(message.isFull());
		assertFalse(message.isReplyRequested());
	}

	@Test
	public void spacesOutFullUpdates()
	{
		sync.requestHello();
		assertNotNull(next(0));

		sync.requestFull(1_000);
		assertNull(next(1_000));
		assertTrue(next(PartyPingSync.MIN_FULL_SPACING_MS).isFull());
	}

	@Test
	public void firstContactWithASharerSendsFullUpdate()
	{
		receiveFrom(OTHER, 0);
		assertTrue(next(0).isFull());
	}

	@Test
	public void sendsOnlySignificantChanges()
	{
		receiveFrom(OTHER, 0);
		assertTrue(next(0).isFull());

		local.put(301, 21);
		local.put(302, 120);
		local.put(303, PingEstimate.TIMEOUT);

		assertNull(next(PartyPingSync.DELTA_INTERVAL_MS - 1));

		LowestCommonPingUpdate delta = next(PartyPingSync.DELTA_INTERVAL_MS);
		assertFalse(delta.isFull());
		Map<Integer, Integer> expected = new HashMap<>();
		expected.put(302, 120);
		expected.put(303, PingEstimate.TIMEOUT);
		assertEquals(expected, PingCodec.decode(delta.getData()));

		// nothing changed since
		assertNull(next(PartyPingSync.DELTA_INTERVAL_MS * 2));
	}

	@Test
	public void sendsPeriodicFullUpdates()
	{
		receiveFrom(OTHER, 0);
		assertTrue(next(0).isFull());

		receiveFrom(OTHER, PartyPingSync.FULL_INTERVAL_MS);
		assertTrue(next(PartyPingSync.FULL_INTERVAL_MS).isFull());
	}

	@Test
	public void slowsDownInLargeParties()
	{
		assertEquals(1, PartyPingSync.scale(2));
		assertEquals(1, PartyPingSync.scale(7));
		assertEquals(4, PartyPingSync.scale(10));

		receiveFrom(OTHER, 0);
		assertTrue(next(0, 10).isFull());
		local.put(302, 200);

		assertNull(next(PartyPingSync.DELTA_INTERVAL_MS, 10));
		assertNotNull(next(PartyPingSync.DELTA_INTERVAL_MS * 4, 10));
	}

	@Test
	public void mergesDeltasAndReplacesOnFull()
	{
		Map<Integer, Integer> first = new HashMap<>();
		first.put(301, 20);
		first.put(302, 30);
		sync.onUpdate(OTHER, update(LowestCommonPingUpdate.FLAG_FULL, first), 0, 0);

		Map<Integer, Integer> delta = new HashMap<>();
		delta.put(302, 40);
		delta.put(303, 50);
		sync.onUpdate(OTHER, update(0, delta), 1, 1);

		Map<Integer, Integer> expected = new HashMap<>();
		expected.put(301, 20);
		expected.put(302, 40);
		expected.put(303, 50);
		assertEquals(expected, sync.getMembers().get(0).getPings());

		sync.onUpdate(OTHER, update(LowestCommonPingUpdate.FLAG_FULL, Collections.singletonMap(304, 1)), 2, 2);
		assertEquals(Collections.singletonMap(304, 1), sync.getMembers().get(0).getPings());
	}

	@Test
	public void ignoresDeltasWithoutAFullUpdate()
	{
		assertTrue(sync.onUpdate(OTHER, update(0, Collections.singletonMap(301, 20)), 0, 0));
		assertFalse(sync.hasOtherUsers());
	}

	@Test
	public void stopMessageRemovesMember()
	{
		receiveFrom(OTHER, 0);
		assertTrue(sync.hasOtherUsers());

		sync.onUpdate(OTHER, new LowestCommonPingUpdate("Friend", LowestCommonPingUpdate.FLAG_STOPPED, null), 1, 1);
		assertFalse(sync.hasOtherUsers());
	}

	@Test
	public void ignoresMalformedUpdates()
	{
		assertFalse(sync.onUpdate(OTHER, new LowestCommonPingUpdate("Friend", LowestCommonPingUpdate.FLAG_FULL, "%%%"), 0, 0));
		assertFalse(sync.hasOtherUsers());
	}

	@Test
	public void ignoresDataFromNewerProtocolVersions()
	{
		// world 301 at 20 ms in the current encoding, but declared as a newer, incompatible version
		int future = LowestCommonPingUpdate.PROTOCOL_VERSION + 1;
		assertTrue(sync.onUpdate(OTHER, parse("{\"v\":" + future + ",\"k\":1,\"d\":\"AS0AFA\"}"), 0, 0));
		assertFalse(sync.hasOtherUsers());

		// the stop flag is still honoured
		receiveFrom(OTHER, 1);
		sync.onUpdate(OTHER, parse("{\"v\":" + future + ",\"k\":4}"), 2, 2);
		assertFalse(sync.hasOtherUsers());
	}

	private static LowestCommonPingUpdate parse(String json)
	{
		return new Gson().fromJson(json, LowestCommonPingUpdate.class);
	}

	@Test
	public void sanitizesNames()
	{
		sync.onUpdate(OTHER, new LowestCommonPingUpdate("<html><img src=x>", LowestCommonPingUpdate.FLAG_FULL, null), 0, 0);
		assertEquals("htmlimg srcx", sync.getMembers().get(0).getName());
	}

	@Test
	public void keepsPreviousNameWhenMissing()
	{
		receiveFrom(OTHER, 0);
		sync.onUpdate(OTHER, new LowestCommonPingUpdate(null, 0, null), 1, 1);
		assertEquals("Friend", sync.getMembers().get(0).getName());
	}

	@Test
	public void replyRequestSchedulesFullUpdate()
	{
		sync.requestHello();
		assertTrue(next(0).isFull());

		sync.onUpdate(OTHER, update(LowestCommonPingUpdate.FLAG_FULL | LowestCommonPingUpdate.FLAG_REPLY_REQUESTED,
			Collections.singletonMap(301, 10)), 10_000, 12_000);

		assertNull(next(11_999));
		assertTrue(next(12_000).isFull());
	}

	@Test
	public void rateLimitsReplyRequestsPerMember()
	{
		sync.requestHello();
		assertTrue(next(0).isFull());

		int hello = LowestCommonPingUpdate.FLAG_FULL | LowestCommonPingUpdate.FLAG_REPLY_REQUESTED;
		sync.onUpdate(OTHER, update(hello, Collections.singletonMap(301, 10)), 10_000, 10_000);
		assertTrue(next(10_000).isFull());

		// a second request soon after is deferred until the limit, not dropped
		sync.onUpdate(OTHER, update(hello, Collections.singletonMap(301, 10)), 16_000, 16_000);
		long limit = 10_000 + PartyPingSync.REQUEST_LIMIT_MS;
		assertNull(next(16_000));
		assertNull(next(limit - 1));
		assertTrue(next(limit).isFull());

		// a member that stopped forgot everything, so its next request is not held back by the limit
		sync.onUpdate(OTHER, new LowestCommonPingUpdate("Friend", LowestCommonPingUpdate.FLAG_STOPPED, null),
			limit + 1, limit + 1);
		long restart = limit + 2;
		sync.onUpdate(OTHER, update(hello, Collections.singletonMap(301, 10)), restart, restart);
		assertTrue(next(limit + PartyPingSync.MIN_FULL_SPACING_MS).isFull());
	}

	@Test
	public void floodOfRequestsIsBounded()
	{
		sync.requestHello();
		assertTrue(next(0).isFull());

		int hello = LowestCommonPingUpdate.FLAG_FULL | LowestCommonPingUpdate.FLAG_REPLY_REQUESTED;
		int fulls = 0;
		for (long t = 10_000; t < 70_000; t += 1_000)
		{
			sync.onUpdate(OTHER, update(hello, Collections.singletonMap(301, 10)), t, t);
			LowestCommonPingUpdate message = next(t);
			if (message != null && message.isFull())
			{
				fulls++;
			}
		}

		// one request a second for a minute is answered about once per limit
		assertTrue(fulls + " fulls", fulls <= 60_000 / PartyPingSync.REQUEST_LIMIT_MS + 1);
		assertTrue(fulls + " fulls", fulls >= 2);
	}

	@Test
	public void prunesMembersThatLeftOrWentSilent()
	{
		receiveFrom(OTHER, 0);
		receiveFrom(7, 0);

		sync.prune(new HashSet<>(Collections.singletonList(OTHER)), 1, 3);
		assertEquals(1, sync.getMembers().size());
		assertEquals(OTHER, sync.getMembers().get(0).getMemberId());

		sync.prune(Collections.singleton(OTHER), PartyPingSync.MEMBER_TIMEOUT_MS + 1, 3);
		assertFalse(sync.hasOtherUsers());
	}

	@Test
	public void expiresSilentMembersWithoutAMemberList()
	{
		receiveFrom(OTHER, 0);
		receiveFrom(7, PartyPingSync.MEMBER_TIMEOUT_MS);

		sync.expire(PartyPingSync.MEMBER_TIMEOUT_MS + 1);
		assertEquals(1, sync.getMembers().size());
		assertEquals(7, sync.getMembers().get(0).getMemberId());
	}

	@Test
	public void stopMessageOnlyAfterAnnouncing()
	{
		assertNull(sync.stopMessage("Me"));

		sync.requestHello();
		next(0);
		int epoch = sync.getEpoch();
		LowestCommonPingUpdate stop = sync.stopMessage("Me");
		assertNotNull(stop);
		assertTrue(stop.isStopped());
		// anything built before the stop must not be sent after it
		assertFalse(sync.isEpoch(epoch));

		assertNull(sync.stopMessage("Me"));
	}

	@Test
	public void resetForgetsEverything()
	{
		receiveFrom(OTHER, 0);
		sync.requestHello();
		int epoch = sync.getEpoch();
		sync.reset();

		assertFalse(sync.hasOtherUsers());
		assertNull(next(0));
		assertFalse(sync.isEpoch(epoch));
	}

	@Test
	public void listenerAnnouncesItselfWithoutData()
	{
		sync.initSharing(false);
		sync.requestHello();

		LowestCommonPingUpdate hello = next(0);
		assertTrue(hello.isListener());
		assertTrue(hello.isReplyRequested());
		assertFalse(hello.isFull());
		assertNull(hello.getData());

		// keeps announcing itself to sharers, but never sends deltas
		receiveFrom(OTHER, 1);
		local.put(301, 500);
		assertNull(next(PartyPingSync.DELTA_INTERVAL_MS * 2));
		LowestCommonPingUpdate keepAlive = next(PartyPingSync.FULL_INTERVAL_MS);
		assertTrue(keepAlive.isListener());
		assertFalse(keepAlive.isReplyRequested());
	}

	@Test
	public void sharersKeepSendingToListeners()
	{
		sync.onUpdate(OTHER, new LowestCommonPingUpdate("Friend",
			LowestCommonPingUpdate.FLAG_LISTENER | LowestCommonPingUpdate.FLAG_REPLY_REQUESTED, null), 0, 1_000);

		assertTrue(sync.hasOtherUsers());
		assertTrue(sync.getMembers().isEmpty());
		assertEquals("Friend", sync.getListeners().get(0).getName());

		// replies to the listener, then keeps it updated
		assertTrue(next(1_000).isFull());
		local.put(302, 150);
		assertFalse(next(1_000 + PartyPingSync.DELTA_INTERVAL_MS).isFull());
	}

	@Test
	public void turningSharingOffTellsOthersToDropOurData()
	{
		receiveFrom(OTHER, 0);
		assertTrue(next(0).isFull());
		int epoch = sync.getEpoch();

		sync.setSharing(false);
		assertFalse(sync.isEpoch(epoch));

		LowestCommonPingUpdate message = next(1);
		assertTrue(message.isListener());
		assertTrue(message.isStopped());
		assertNull(message.getData());
	}

	@Test
	public void turningSharingOnSendsHello()
	{
		sync.initSharing(false);
		sync.setSharing(true);

		LowestCommonPingUpdate message = next(0);
		assertTrue(message.isFull());
		assertTrue(message.isReplyRequested());
	}

	@Test
	public void listenersKeepEachOtherListed()
	{
		sync.initSharing(false);
		sync.onUpdate(OTHER, new LowestCommonPingUpdate("Friend",
			LowestCommonPingUpdate.FLAG_LISTENER | LowestCommonPingUpdate.FLAG_REPLY_REQUESTED, null), 0, 1_000);

		assertTrue(next(1_000).isListener());
		assertTrue(next(1_000 + PartyPingSync.FULL_INTERVAL_MS).isListener());
	}

	@Test
	public void localPingsAreNeededOnlyWhenSomeoneUsesThem()
	{
		assertFalse(sync.needsLocalPings());

		// sharing with a listener: they want our pings
		sync.onUpdate(OTHER, new LowestCommonPingUpdate("Friend", LowestCommonPingUpdate.FLAG_LISTENER, null), 0, 0);
		assertTrue(sync.needsLocalPings());

		// only watching, with nobody sharing: our pings are of no use to anyone
		sync.initSharing(false);
		assertFalse(sync.needsLocalPings());

		// only watching, with a sharer: our pings complete the ranking we see
		receiveFrom(7, 1);
		assertTrue(sync.needsLocalPings());
	}

	@Test
	public void listenerWithDataStartsSharingWithoutAskingForReplies()
	{
		sync.initSharing(false);
		receiveFrom(OTHER, 0);
		sync.setSharing(true);

		// the sharer keeps sending to listeners, so we already have its pings
		LowestCommonPingUpdate message = next(1);
		assertTrue(message.isFull());
		assertFalse(message.isReplyRequested());
	}

	@Test
	public void memberSwitchingToListenerKeepsItsName()
	{
		receiveFrom(OTHER, 0);
		sync.onUpdate(OTHER, new LowestCommonPingUpdate(null,
			LowestCommonPingUpdate.FLAG_LISTENER | LowestCommonPingUpdate.FLAG_STOPPED, null), 1, 1);

		assertTrue(sync.getMembers().isEmpty());
		assertEquals("Friend", sync.getListeners().get(0).getName());

		// and back to sharing
		receiveFrom(OTHER, 2);
		assertTrue(sync.getListeners().isEmpty());
		assertEquals(1, sync.getMembers().size());
	}

	@Test
	public void rejoinAnnouncesAgainOnlyAfterAnnouncing()
	{
		sync.onRejoined();
		assertNull(next(0));

		receiveFrom(OTHER, 0);
		assertTrue(next(0).isFull());

		sync.onRejoined();
		LowestCommonPingUpdate hello = next(1);
		assertTrue(hello.isFull());
		assertTrue(hello.isReplyRequested());
	}

	@Test
	public void detectsSignificantChanges()
	{
		assertFalse(PartyPingSync.isSignificantChange(20, 22));
		assertTrue(PartyPingSync.isSignificantChange(20, 23));
		assertFalse(PartyPingSync.isSignificantChange(300, 314));
		assertTrue(PartyPingSync.isSignificantChange(300, 315));
		assertTrue(PartyPingSync.isSignificantChange(20, PingEstimate.TIMEOUT));
		assertTrue(PartyPingSync.isSignificantChange(PingEstimate.TIMEOUT, 20));
		assertFalse(PartyPingSync.isSignificantChange(PingEstimate.TIMEOUT, PingEstimate.TIMEOUT));
	}
}
