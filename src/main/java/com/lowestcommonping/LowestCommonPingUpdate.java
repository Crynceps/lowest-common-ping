package com.lowestcommonping;

import com.google.gson.annotations.SerializedName;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Party message carrying one member's world pings.
 * <p>
 * The class name is the message type on the wire, so it must never be renamed. Field names are kept short
 * because every message is relayed to every party member by RuneLite's party server.
 * <p>
 * {@link #PROTOCOL_VERSION} is only raised for changes older clients cannot understand (they then ignore
 * everything but {@link #FLAG_STOPPED}); compatible additions use new fields or flags instead.
 */
public class LowestCommonPingUpdate extends PartyMemberMessage
{
	static final int PROTOCOL_VERSION = 1;

	/**
	 * The data replaces everything previously received from this member, instead of being merged.
	 */
	static final int FLAG_FULL = 1;
	/**
	 * The sender asks every other member to reply with a full update.
	 */
	static final int FLAG_REPLY_REQUESTED = 2;
	/**
	 * The sender stopped sharing; drop its data.
	 */
	static final int FLAG_STOPPED = 4;
	/**
	 * The sender uses the plugin but does not share its pings. Carries no data; tells sharers that someone is
	 * listening, and is repeated as a keep-alive.
	 */
	static final int FLAG_LISTENER = 8;

	@SerializedName("v")
	private int version = PROTOCOL_VERSION;

	@SerializedName("n")
	private String name;

	@SerializedName("k")
	private int flags;

	/**
	 * Encoded pings, see {@link PingCodec}.
	 */
	@SerializedName("d")
	private String data;

	public LowestCommonPingUpdate()
	{
	}

	LowestCommonPingUpdate(String name, int flags, String data)
	{
		this.name = name;
		this.flags = flags;
		this.data = data;
	}

	int getVersion()
	{
		return version;
	}

	String getName()
	{
		return name;
	}

	int getFlags()
	{
		return flags;
	}

	String getData()
	{
		return data;
	}

	boolean isFull()
	{
		return (flags & FLAG_FULL) != 0;
	}

	boolean isReplyRequested()
	{
		return (flags & FLAG_REPLY_REQUESTED) != 0;
	}

	boolean isStopped()
	{
		return (flags & FLAG_STOPPED) != 0;
	}

	boolean isListener()
	{
		return (flags & FLAG_LISTENER) != 0;
	}

	@Override
	public String toString()
	{
		// WSClient logs every message at debug level; keep it short
		return "LowestCommonPingUpdate{member=" + getMemberId()
			+ ", flags=" + flags
			+ ", bytes=" + (data == null ? 0 : data.length())
			+ "}";
	}
}
