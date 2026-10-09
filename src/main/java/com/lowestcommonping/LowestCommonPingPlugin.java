package com.lowestcommonping;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.events.WorldsFetch;
import net.runelite.client.game.WorldService;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.worldhopper.ping.Ping;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ExecutorServiceExceptionLogger;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.OSType;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;

@Slf4j
@PluginDescriptor(
	name = "Lowest Common Ping",
	description = "Find the world with the lowest ping for everyone in your party",
	tags = {"party", "ping", "latency", "world", "hop", "lag", "group"}
)
public class LowestCommonPingPlugin extends Plugin
{
	private static final long TICK_MS = 1_000;
	private static final long MAX_ESTIMATE_AGE_MS = 10 * 60_000;
	/**
	 * Number of top ranked worlds that are pinged often enough for an accurate rolling value.
	 */
	private static final int FOCUS_WORLDS = 8;
	/**
	 * A focus world stays in focus while it ranks within this many worlds, so a single slow sample does not
	 * immediately demote it to the slow lane.
	 */
	private static final int FOCUS_KEEP_RANK = 12;
	private static final int REPLY_DELAY_MS = 500;
	private static final int REPLY_JITTER_MS = 2_000;
	private static final String UNKNOWN_DISPLAY_NAME = "<unknown>";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PartyService partyService;

	@Inject
	private WSClient wsClient;

	@Inject
	private WorldService worldService;

	@Inject
	private LowestCommonPingConfig config;

	@Inject
	private BestWorldOverlay overlay;

	@Inject
	private WorldHopper worldHopper;

	private final PingTracker tracker = new PingTracker();
	private final PartyPingSync sync = new PartyPingSync();
	// serializes party sends with the final stop message sent on shutdown
	private final Object sendLock = new Object();

	private ScheduledExecutorService scheduler;
	private ScheduledFuture<?> tickFuture;
	private PingService pingService;
	private LowestCommonPingPanel panel;
	private NavigationButton navButton;

	private volatile boolean messageRegistered;
	private boolean sendClosed; // guarded by sendLock
	private volatile boolean panelActive;
	private volatile int connectedWorld;
	private volatile String localName;
	private volatile List<World> allWorlds = Collections.emptyList();
	private volatile Ranking latestRanking = Ranking.EMPTY;

	@Override
	protected void startUp()
	{
		messageRegistered = registerMessage();
		synchronized (sendLock)
		{
			sendClosed = false;
		}

		sync.reset();
		sync.initSharing(config.sharePings());
		if (partyService.isInParty())
		{
			sync.requestHello();
		}

		panel = new LowestCommonPingPanel(this, config);
		navButton = NavigationButton.builder()
			.tooltip("Lowest Common Ping")
			.icon(ImageUtil.loadImageResource(getClass(), "panel_icon.png"))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);

		// pings block for up to several seconds, so they get their own threads instead of the shared executor
		scheduler = new ExecutorServiceExceptionLogger(Executors.newSingleThreadScheduledExecutor(
			new ThreadFactoryBuilder().setNameFormat("lowest-common-ping-%d").setDaemon(true).build()));
		pingService = new PingService(tracker,
			// ICMP, falling back to a TCP connect for worlds and networks that block ICMP, like the World Hopper
			world -> Ping.ping(world, true),
			System::currentTimeMillis,
			this::isPingingEnabled,
			OSType.getOSType() == OSType.Windows ? 3 : 2);
		pingService.start(scheduler, config.maxPingsPerSecond());

		// WorldService.getWorlds() can block while the world list is first fetched
		scheduler.execute(this::loadWorlds);
		tickFuture = scheduler.scheduleWithFixedDelay(this::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);

		clientThread.invokeLater(this::updateClientState);
	}

	@Override
	protected void shutDown()
	{
		// tell the others to forget us; nothing queued earlier may be sent after this
		synchronized (sendLock)
		{
			try
			{
				LowestCommonPingUpdate stop = sync.stopMessage(localName);
				if (stop != null)
				{
					sendNow(stop);
				}
			}
			finally
			{
				sendClosed = true;
			}
		}

		if (tickFuture != null)
		{
			tickFuture.cancel(false);
			tickFuture = null;
		}
		if (pingService != null)
		{
			pingService.stop();
			pingService = null;
		}
		if (scheduler != null)
		{
			scheduler.shutdownNow();
			scheduler = null;
		}

		messageRegistered = false;
		unregisterMessage();

		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		panelActive = false;
		clientThread.invokeLater(worldHopper::reset);

		tracker.clear();
		sync.reset();
		allWorlds = Collections.emptyList();
		latestRanking = Ranking.EMPTY;
	}

	@Provides
	LowestCommonPingConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LowestCommonPingConfig.class);
	}

	private boolean registerMessage()
	{
		try
		{
			wsClient.registerMessage(LowestCommonPingUpdate.class);
			return true;
		}
		catch (IllegalArgumentException e)
		{
			log.warn("Unable to register the party message, party sharing is disabled", e);
			// a failed registration stays in the client's message set and would break registrations of other plugins
			unregisterMessage();
			return false;
		}
	}

	private void unregisterMessage()
	{
		try
		{
			wsClient.unregisterMessage(LowestCommonPingUpdate.class);
		}
		catch (IllegalArgumentException e)
		{
			log.debug("Unable to unregister the party message", e);
		}
	}

	Ranking getLatestRanking()
	{
		return latestRanking;
	}

	void setPanelActive(boolean active)
	{
		panelActive = active;
		if (active)
		{
			requestTick();
		}
	}

	/**
	 * Hops to a world. Called from the panel.
	 */
	void hopTo(World world)
	{
		worldHopper.hop(world);
	}

	private boolean isPingingEnabled()
	{
		if (panelActive)
		{
			return true;
		}
		return config.backgroundPinging() && messageRegistered && partyService.isInParty() && sync.needsLocalPings();
	}

	private void loadWorlds()
	{
		WorldResult result = worldService.getWorlds();
		if (result != null)
		{
			setWorlds(result.getWorlds());
		}
	}

	private void setWorlds(List<World> worlds)
	{
		allWorlds = new ArrayList<>(worlds);
		applyFilter();
	}

	private void applyFilter()
	{
		tracker.setCandidates(WorldFilter.fromConfig(config).filter(allWorlds));
	}

	private void requestTick()
	{
		ScheduledExecutorService executor = scheduler;
		if (executor == null)
		{
			return;
		}

		try
		{
			executor.execute(this::tick);
		}
		catch (RejectedExecutionException e)
		{
			// shutting down
		}
	}

	private void tick()
	{
		try
		{
			update();
		}
		catch (RuntimeException e)
		{
			// an exception would cancel the periodic tick
			log.warn("Lowest Common Ping update failed", e);
		}
	}

	/**
	 * Runs every second on the scheduler thread: sends party updates, ranks the worlds and refreshes the panel.
	 */
	private void update()
	{
		long now = System.currentTimeMillis();
		boolean inParty = partyService.isInParty();
		boolean sharingAvailable = messageRegistered;
		PartyMember local = inParty ? partyService.getLocalMember() : null;
		List<PartyMember> members = inParty ? copyMembers() : Collections.emptyList();

		Set<Long> memberIds = new HashSet<>();
		for (PartyMember member : members)
		{
			memberIds.add(member.getMemberId());
		}
		if (inParty && (local == null || !memberIds.contains(local.getMemberId())))
		{
			// while (re)joining, the member list is incomplete: keep members until the server replays the joins,
			// but still let silent ones expire
			sync.expire(now);
		}
		else
		{
			sync.prune(memberIds, now, members.size());
		}

		Map<Integer, PingEstimate> estimates = tracker.estimates(now, config.averageWindow() * 1000L, MAX_ESTIMATE_AGE_MS);
		Map<Integer, Integer> localPings = new HashMap<>();
		for (Map.Entry<Integer, PingEstimate> entry : estimates.entrySet())
		{
			localPings.put(entry.getKey(), entry.getValue().getMs());
		}

		if (sharingAvailable && local != null)
		{
			PartyPingSync.Outgoing outgoing = sync.nextMessage(now, localPings, members.size(), localName);
			if (outgoing != null)
			{
				// all regular sends happen on the client thread, in the order they were built
				clientThread.invokeLater(() -> send(outgoing));
			}
		}

		String name = localName;
		List<Participant> participants = new ArrayList<>();
		participants.add(new Participant(local == null ? 0 : local.getMemberId(), name == null ? "You" : name, true,
			localPings));

		Set<Long> pluginUsers = new HashSet<>();
		List<String> waitingFor = new ArrayList<>();
		List<String> listeners = new ArrayList<>();
		if (sharingAvailable && inParty)
		{
			for (MemberPings member : sync.getMembers())
			{
				pluginUsers.add(member.getMemberId());
				String memberName = displayName(member, members);
				if (member.getPings().isEmpty())
				{
					// a member that has not measured anything yet would make every world incomplete
					waitingFor.add(memberName);
					continue;
				}
				participants.add(new Participant(member.getMemberId(), memberName, false, member.getPings()));
			}

			for (MemberPings listener : sync.getListeners())
			{
				pluginUsers.add(listener.getMemberId());
				listeners.add(displayName(listener, members));
			}
		}

		List<World> worlds = tracker.getCandidates();
		Ranking ranking = RankingCalculator.compute(worlds, participants, config.rankBy(), estimates);
		latestRanking = ranking;
		tracker.setFocus(focusWorlds(ranking, tracker.getFocus()));

		if (panelActive)
		{
			List<String> withoutPlugin = new ArrayList<>();
			for (PartyMember member : members)
			{
				if ((local == null || member.getMemberId() != local.getMemberId())
					&& !pluginUsers.contains(member.getMemberId()))
				{
					String memberName = partyName(member);
					withoutPlugin.add(memberName == null ? "unknown member" : memberName);
				}
			}

			PanelState state = new PanelState(ranking, inParty, inParty && local == null, sharingAvailable,
				config.sharePings(), members.size(), waitingFor, listeners, withoutPlugin, worlds.size(),
				connectedWorld);
			SwingUtilities.invokeLater(() ->
			{
				LowestCommonPingPanel p = panel;
				if (p != null)
				{
					p.render(state);
				}
			});
		}
	}

	/**
	 * @return the top ranked worlds, keeping previous focus worlds while they still rank near the top
	 */
	static Set<Integer> focusWorlds(Ranking ranking, Set<Integer> previous)
	{
		Set<Integer> focus = new HashSet<>();
		List<WorldRank> rows = ranking.getRows();
		for (int i = 0; i < rows.size() && i < FOCUS_KEEP_RANK; i++)
		{
			WorldRank rank = rows.get(i);
			if (rank.getTier() > WorldRank.TIER_MISSING)
			{
				break;
			}

			if (i < FOCUS_WORLDS || previous.contains(rank.getWorldId()))
			{
				focus.add(rank.getWorldId());
			}
		}
		return focus;
	}

	private List<PartyMember> copyMembers()
	{
		// the list is modified on the websocket thread, so take a copy before iterating
		List<PartyMember> copy = new ArrayList<>(partyService.getMembers());
		copy.removeIf(member -> member == null);
		return copy;
	}

	private static String displayName(MemberPings member, List<PartyMember> members)
	{
		if (member.getName() != null)
		{
			return member.getName();
		}

		for (PartyMember partyMember : members)
		{
			if (partyMember.getMemberId() == member.getMemberId())
			{
				String name = partyName(partyMember);
				if (name != null)
				{
					return name;
				}
			}
		}
		return "Member " + (member.getMemberId() % 10_000);
	}

	/**
	 * @return the name the core Party plugin knows for a member, or null
	 */
	private static String partyName(PartyMember member)
	{
		String name = member.getDisplayName();
		if (name == null || name.equals(UNKNOWN_DISPLAY_NAME))
		{
			return null;
		}
		return Names.sanitize(name);
	}

	/**
	 * Sends a message built by the sync, unless the plugin is stopping or the sync state changed since it was
	 * built (e.g. sharing was turned off or the party changed).
	 */
	private void send(PartyPingSync.Outgoing outgoing)
	{
		synchronized (sendLock)
		{
			if (sendClosed || !sync.isEpoch(outgoing.getEpoch()))
			{
				return;
			}
			sendNow(outgoing.getMessage());
		}
	}

	private void sendNow(LowestCommonPingUpdate message)
	{
		try
		{
			// the party service's member list is modified on the websocket thread, so even the checks can throw
			if (messageRegistered && partyService.isInParty() && partyService.getLocalMember() != null)
			{
				partyService.send(message);
			}
		}
		catch (RuntimeException e)
		{
			log.debug("Unable to send party world pings", e);
		}
	}

	private static long replyAt(long now)
	{
		// spread replies out so a join does not make every member answer at the same moment
		return now + REPLY_DELAY_MS + ThreadLocalRandom.current().nextInt(REPLY_JITTER_MS);
	}

	@Subscribe
	public void onLowestCommonPingUpdate(LowestCommonPingUpdate update)
	{
		// runs on the websocket thread
		PartyMember local = partyService.getLocalMember();
		if (local != null && local.getMemberId() == update.getMemberId())
		{
			// our own message, echoed back by the party server
			return;
		}

		if (partyService.getMemberById(update.getMemberId()) == null)
		{
			return;
		}

		long now = System.currentTimeMillis();
		if (!sync.onUpdate(update.getMemberId(), update, now, replyAt(now)))
		{
			log.debug("Ignored malformed party world ping update from {}", update.getMemberId());
		}
	}

	@Subscribe
	public void onUserJoin(UserJoin event)
	{
		// the party service has already processed the join, so the local member is set for our own join
		PartyMember local = partyService.getLocalMember();
		if (local != null && local.getMemberId() == event.getMemberId()
			&& event.getPartyId() == partyService.getPartyId())
		{
			// also fires after a silent reconnect, when the others may have dropped our data
			sync.onRejoined();
		}
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		sync.removeMember(event.getMemberId());
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		sync.reset();
		if (event.getPartyId() != null)
		{
			sync.requestHello();
		}
		requestTick();
	}

	@Subscribe
	public void onWorldsFetch(WorldsFetch event)
	{
		setWorlds(event.getWorldResult().getWorlds());
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!LowestCommonPingConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		switch (event.getKey())
		{
			case "membership":
			case "regions":
			case "skillTotalWorlds":
				applyFilter();
				break;
			case "maxPingsPerSecond":
				if (pingService != null)
				{
					pingService.setRate(config.maxPingsPerSecond());
				}
				break;
			case "sharePings":
				// the change is announced by the next update, after anything already queued is discarded
				sync.setSharing(config.sharePings());
				break;
			default:
				break;
		}
		requestTick();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		updateClientState();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		updateLocalName();
		worldHopper.onGameTick();
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		worldHopper.onChatMessage(event);
	}

	private void updateClientState()
	{
		GameState state = client.getGameState();
		connectedWorld = state == GameState.LOGGED_IN || state == GameState.LOADING ? client.getWorld() : 0;
		updateLocalName();
	}

	private void updateLocalName()
	{
		Player player = client.getLocalPlayer();
		if (player != null)
		{
			String name = Names.sanitize(player.getName());
			if (name != null)
			{
				localName = name;
			}
		}
	}
}
