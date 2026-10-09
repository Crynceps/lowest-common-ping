package com.lowestcommonping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Party side of the plugin: tracks the other members that use the plugin and decides what to send.
 * <p>
 * Members either share their pings ({@link #getMembers()}) or only listen ({@link #getListeners()}). To keep load
 * on RuneLite's party server low:
 * <ul>
 *     <li>a full update (or, for listeners, a presence message) is sent when joining, when asked, and every
 *     {@link #FULL_INTERVAL_MS} as a keep-alive</li>
 *     <li>in between, sharers only send worlds whose ping changed noticeably, at most every
 *     {@link #DELTA_INTERVAL_MS}</li>
 *     <li>nothing periodic is sent until another member is known to use the plugin, and nothing at all while
 *     alone in the party</li>
 *     <li>intervals grow in large parties, like the core party plugin does, and requests for full updates are
 *     rate limited per member</li>
 * </ul>
 * All methods are synchronized: messages arrive on the websocket thread while updates are built on the plugin's
 * scheduler thread.
 */
final class PartyPingSync
{
	static final long DELTA_INTERVAL_MS = 10_000;
	static final long FULL_INTERVAL_MS = 120_000;
	static final long MIN_FULL_SPACING_MS = 5_000;
	static final long MEMBER_TIMEOUT_MS = 3 * FULL_INTERVAL_MS;
	static final long REQUEST_LIMIT_MS = 15_000;

	private static final int CHANGE_MIN_MS = 3;
	private static final int CHANGE_PERCENT = 5;
	private static final long NEVER = Long.MIN_VALUE;

	/**
	 * A message to send, tagged with the state it was built in.
	 */
	static final class Outgoing
	{
		private final LowestCommonPingUpdate message;
		private final int epoch;

		private Outgoing(LowestCommonPingUpdate message, int epoch)
		{
			this.message = message;
			this.epoch = epoch;
		}

		LowestCommonPingUpdate getMessage()
		{
			return message;
		}

		int getEpoch()
		{
			return epoch;
		}
	}

	// insertion ordered so member columns keep a stable order
	private final Map<Long, MemberPings> members = new LinkedHashMap<>();
	private final Map<Long, MemberPings> listeners = new LinkedHashMap<>();
	private final Map<Long, Long> lastRequestHonored = new HashMap<>();
	private final Map<Integer, Integer> lastSent = new HashMap<>();
	private long lastFullAt = NEVER;
	private long lastDeltaAt = NEVER;
	private boolean helloPending;
	// whether the pending hello asks the others to reply with their pings
	private boolean helloReply;
	private long fullRequestedAt = NEVER;
	// a presence message was sent since the last reset, so others may hold state about us
	private boolean announced;
	private boolean sharing = true;
	// sharing was turned off after our pings were sent: tell the others to drop them
	private boolean stopPending;
	private int partySize = 1;
	// changes whenever messages built earlier must no longer be sent
	private int epoch;

	static int scale(int partySize)
	{
		return Math.max(1, partySize - 6);
	}

	/**
	 * Forgets the party, e.g. after joining or leaving one. Keeps the sharing setting.
	 */
	synchronized void reset()
	{
		members.clear();
		listeners.clear();
		lastRequestHonored.clear();
		resetSendState();
		epoch++;
	}

	private void resetSendState()
	{
		lastSent.clear();
		lastFullAt = NEVER;
		lastDeltaAt = NEVER;
		helloPending = false;
		helloReply = false;
		fullRequestedAt = NEVER;
		announced = false;
		stopPending = false;
	}

	/**
	 * Sets the sharing mode without announcing the change, for plugin start up.
	 */
	synchronized void initSharing(boolean share)
	{
		sharing = share;
	}

	/**
	 * Switches between sharing pings and only listening, and announces the change on the next opportunity.
	 */
	synchronized void setSharing(boolean share)
	{
		if (share == sharing)
		{
			return;
		}

		boolean sharedData = sharing && announced;
		sharing = share;
		lastSent.clear();
		lastFullAt = NEVER;
		lastDeltaAt = NEVER;
		fullRequestedAt = NEVER;
		stopPending = !share && sharedData;
		helloPending = true;
		// sharers keep sending to listeners, so only ask for replies if we have nobody's pings yet
		helloReply = members.isEmpty();
		epoch++;
	}

	synchronized boolean isSharing()
	{
		return sharing;
	}

	synchronized int getEpoch()
	{
		return epoch;
	}

	synchronized boolean isEpoch(int epoch)
	{
		return this.epoch == epoch;
	}

	/**
	 * Announces us and asks everyone to reply, on the next opportunity.
	 */
	synchronized void requestHello()
	{
		helloPending = true;
		helloReply = true;
	}

	/**
	 * Called when the party server (re)confirmed our membership. After a silent reconnect the others may have
	 * dropped our data, so announce again if we had announced before.
	 */
	synchronized void onRejoined()
	{
		if (announced)
		{
			helloPending = true;
			helloReply = true;
		}
	}

	/**
	 * Sends a full update no earlier than {@code sendAt}.
	 */
	synchronized void requestFull(long sendAt)
	{
		if (fullRequestedAt == NEVER || sendAt < fullRequestedAt)
		{
			fullRequestedAt = sendAt;
		}
	}

	/**
	 * Applies a message from another member.
	 *
	 * @param replyAt when to send our full update if the sender asked for one
	 * @return false if the message was malformed and ignored
	 */
	synchronized boolean onUpdate(long memberId, LowestCommonPingUpdate update, long now, long replyAt)
	{
		int version = update.getVersion();
		if (version < 1)
		{
			return false;
		}

		MemberPings previous = members.get(memberId);
		MemberPings previousListener = listeners.get(memberId);
		String name = Names.sanitize(update.getName());
		if (name == null)
		{
			// updates may omit the name, e.g. when the sender is not logged in
			name = previous != null ? previous.getName()
				: previousListener != null ? previousListener.getName() : null;
		}

		if (update.isStopped())
		{
			// the member forgot everything, so its next request starts fresh
			members.remove(memberId);
			listeners.remove(memberId);
			lastRequestHonored.remove(memberId);
		}

		if (version > LowestCommonPingUpdate.PROTOCOL_VERSION)
		{
			// a newer, incompatible format: only the stop flag is understood
			return true;
		}

		if (update.isListener())
		{
			members.remove(memberId);
			listeners.put(memberId, new MemberPings(memberId, name, Collections.emptyMap(), now));
			if (update.isReplyRequested())
			{
				honorRequest(memberId, now, replyAt);
			}
			return true;
		}

		if (update.isStopped())
		{
			return true;
		}

		Map<Integer, Integer> pings;
		try
		{
			pings = PingCodec.decode(update.getData());
		}
		catch (IllegalArgumentException e)
		{
			return false;
		}

		if (!update.isFull())
		{
			if (previous == null)
			{
				// a delta is meaningless without the full update it is based on; wait for the next full one
				return true;
			}

			Map<Integer, Integer> merged = new HashMap<>(previous.getPings());
			merged.putAll(pings);
			if (merged.size() > PingCodec.MAX_ENTRIES)
			{
				return false;
			}
			pings = merged;
		}

		listeners.remove(memberId);
		members.put(memberId, new MemberPings(memberId, name, pings, now));
		if (update.isFull() && update.isReplyRequested())
		{
			honorRequest(memberId, now, replyAt);
		}
		return true;
	}

	/**
	 * Schedules a reply to a member's request, at most one per {@link #REQUEST_LIMIT_MS} per member. A request that
	 * comes too soon is deferred rather than dropped, since a member that restarted needs the reply.
	 */
	private void honorRequest(long memberId, long now, long replyAt)
	{
		long limit = REQUEST_LIMIT_MS * scale(partySize);
		Long last = lastRequestHonored.get(memberId);
		long sendAt = last == null ? replyAt : Math.min(Math.max(replyAt, last + limit), now + limit);
		lastRequestHonored.put(memberId, sendAt);
		requestFull(sendAt);
	}

	synchronized void removeMember(long memberId)
	{
		members.remove(memberId);
		listeners.remove(memberId);
		lastRequestHonored.remove(memberId);
	}

	/**
	 * Drops members that left the party or went silent.
	 */
	synchronized void prune(Set<Long> partyMemberIds, long now, int partySize)
	{
		long timeout = MEMBER_TIMEOUT_MS * scale(partySize);
		prune(members, partyMemberIds, now, timeout);
		prune(listeners, partyMemberIds, now, timeout);
		lastRequestHonored.keySet().retainAll(partyMemberIds);
	}

	/**
	 * Drops members that went silent, without checking membership, for while the party's member list is incomplete.
	 */
	synchronized void expire(long now)
	{
		long timeout = MEMBER_TIMEOUT_MS * scale(partySize);
		members.values().removeIf(member -> now - member.getLastHeard() > timeout);
		listeners.values().removeIf(member -> now - member.getLastHeard() > timeout);
	}

	private static void prune(Map<Long, MemberPings> map, Set<Long> partyMemberIds, long now, long timeout)
	{
		Iterator<MemberPings> it = map.values().iterator();
		while (it.hasNext())
		{
			MemberPings member = it.next();
			if (!partyMemberIds.contains(member.getMemberId()) || now - member.getLastHeard() > timeout)
			{
				it.remove();
			}
		}
	}

	/**
	 * @return the members sharing their pings
	 */
	synchronized List<MemberPings> getMembers()
	{
		return new ArrayList<>(members.values());
	}

	/**
	 * @return the members using the plugin without sharing their pings
	 */
	synchronized List<MemberPings> getListeners()
	{
		return new ArrayList<>(listeners.values());
	}

	/**
	 * @return true if another party member uses the plugin
	 */
	synchronized boolean hasOtherUsers()
	{
		return !members.isEmpty() || !listeners.isEmpty();
	}

	/**
	 * @return true if our own pings are of use to someone: we share them with another plugin user, or we only
	 * watch but another member shares, so our pings complete the ranking we see
	 */
	synchronized boolean needsLocalPings()
	{
		return sharing ? hasOtherUsers() : !members.isEmpty();
	}

	/**
	 * Builds the next message to send, if one is due. The caller must send the returned message, unless
	 * {@link #isEpoch(int)} is false for its epoch by then.
	 *
	 * @param local     our current pings: world id to round trip time or {@link PingEstimate#TIMEOUT}
	 * @param partySize number of members in the party, including us
	 * @param name      our display name, may be null
	 * @return the message to send, or null if nothing is due
	 */
	synchronized Outgoing nextMessage(long now, Map<Integer, Integer> local, int partySize, String name)
	{
		this.partySize = Math.max(1, partySize);
		if (partySize <= 1)
		{
			// nobody would receive it; whoever joins next announces themselves and asks for replies
			helloPending = false;
			helloReply = false;
			fullRequestedAt = NEVER;
			stopPending = false;
			return null;
		}

		int scale = scale(partySize);
		// keep-alives go out while anyone else uses the plugin, so listeners also keep each other listed
		boolean others = !members.isEmpty() || !listeners.isEmpty();
		boolean requested = fullRequestedAt != NEVER && now >= fullRequestedAt;
		boolean periodic = others && (lastFullAt == NEVER || now - lastFullAt >= FULL_INTERVAL_MS * scale);

		if (helloPending || requested || periodic)
		{
			if (!helloPending && lastFullAt != NEVER && now - lastFullAt < MIN_FULL_SPACING_MS * scale)
			{
				// too soon after the previous full update, try again on a later tick
				return null;
			}

			int reply = helloPending && helloReply ? LowestCommonPingUpdate.FLAG_REPLY_REQUESTED : 0;
			LowestCommonPingUpdate message;
			if (sharing)
			{
				lastSent.clear();
				lastSent.putAll(local);
				message = new LowestCommonPingUpdate(name, LowestCommonPingUpdate.FLAG_FULL | reply,
					PingCodec.encode(local));
			}
			else
			{
				int flags = LowestCommonPingUpdate.FLAG_LISTENER | reply;
				if (stopPending)
				{
					flags |= LowestCommonPingUpdate.FLAG_STOPPED;
				}
				message = new LowestCommonPingUpdate(name, flags, null);
			}

			lastFullAt = now;
			lastDeltaAt = now;
			helloPending = false;
			helloReply = false;
			fullRequestedAt = NEVER;
			stopPending = false;
			announced = true;
			return new Outgoing(message, epoch);
		}

		if (!sharing || !others || lastFullAt == NEVER || now - lastDeltaAt < DELTA_INTERVAL_MS * scale)
		{
			return null;
		}

		Map<Integer, Integer> changes = new HashMap<>();
		for (Map.Entry<Integer, Integer> entry : local.entrySet())
		{
			Integer sent = lastSent.get(entry.getKey());
			if (sent == null || isSignificantChange(sent, entry.getValue()))
			{
				changes.put(entry.getKey(), entry.getValue());
			}
		}

		if (changes.isEmpty())
		{
			return null;
		}

		lastSent.putAll(changes);
		lastDeltaAt = now;
		return new Outgoing(new LowestCommonPingUpdate(name, 0, PingCodec.encode(changes)), epoch);
	}

	static boolean isSignificantChange(int previous, int current)
	{
		if (previous < 0 || current < 0)
		{
			return previous != current;
		}
		return Math.abs(current - previous) >= Math.max(CHANGE_MIN_MS, previous * CHANGE_PERCENT / 100);
	}

	/**
	 * Builds the message telling other members to forget us, and invalidates every message built before.
	 *
	 * @return the message to send, or null if nothing was sent since the last reset
	 */
	synchronized LowestCommonPingUpdate stopMessage(String name)
	{
		boolean wasAnnounced = announced;
		resetSendState();
		epoch++;
		return wasAnnounced ? new LowestCommonPingUpdate(name, LowestCommonPingUpdate.FLAG_STOPPED, null) : null;
	}
}
