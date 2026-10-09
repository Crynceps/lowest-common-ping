package com.lowestcommonping;

import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of everything the side panel displays.
 */
final class PanelState
{
	static final PanelState EMPTY = new PanelState(Ranking.EMPTY, false, false, true, true, 0,
		Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), 0, 0);

	private final Ranking ranking;
	private final boolean inParty;
	private final boolean joining;
	private final boolean sharingAvailable;
	private final boolean sharingEnabled;
	private final int partySize;
	private final List<String> waitingFor;
	private final List<String> listeners;
	private final List<String> membersWithoutPlugin;
	private final int candidateWorlds;
	private final int connectedWorld;

	PanelState(Ranking ranking, boolean inParty, boolean joining, boolean sharingAvailable, boolean sharingEnabled,
		int partySize, List<String> waitingFor, List<String> listeners, List<String> membersWithoutPlugin,
		int candidateWorlds, int connectedWorld)
	{
		this.ranking = ranking;
		this.inParty = inParty;
		this.joining = joining;
		this.sharingAvailable = sharingAvailable;
		this.sharingEnabled = sharingEnabled;
		this.partySize = partySize;
		this.waitingFor = Collections.unmodifiableList(waitingFor);
		this.listeners = Collections.unmodifiableList(listeners);
		this.membersWithoutPlugin = Collections.unmodifiableList(membersWithoutPlugin);
		this.candidateWorlds = candidateWorlds;
		this.connectedWorld = connectedWorld;
	}

	Ranking getRanking()
	{
		return ranking;
	}

	boolean isInParty()
	{
		return inParty;
	}

	/**
	 * @return true while the party join has not been confirmed by the server yet
	 */
	boolean isJoining()
	{
		return joining;
	}

	/**
	 * @return false if the party message could not be registered
	 */
	boolean isSharingAvailable()
	{
		return sharingAvailable;
	}

	boolean isSharingEnabled()
	{
		return sharingEnabled;
	}

	int getPartySize()
	{
		return partySize;
	}

	/**
	 * @return sanitized names of members that share pings but have not measured any world yet
	 */
	List<String> getWaitingFor()
	{
		return waitingFor;
	}

	/**
	 * @return sanitized names of members that use the plugin without sharing their pings
	 */
	List<String> getListeners()
	{
		return listeners;
	}

	/**
	 * @return sanitized names of party members that do not use the plugin
	 */
	List<String> getMembersWithoutPlugin()
	{
		return membersWithoutPlugin;
	}

	int getCandidateWorlds()
	{
		return candidateWorlds;
	}

	/**
	 * @return the world the client is logged in to, or 0
	 */
	int getConnectedWorld()
	{
		return connectedWorld;
	}
}
