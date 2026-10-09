package com.lowestcommonping;

import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;

/**
 * Hops worlds the same way the core World Hopper plugin does: directly on the login screen, otherwise through the
 * in-game world switcher.
 */
class WorldHopper
{
	private static final int DISPLAY_SWITCHER_MAX_ATTEMPTS = 3;

	private final Client client;
	private final ClientThread clientThread;
	private final ChatMessageManager chatMessageManager;

	// only accessed on the client thread
	private net.runelite.api.World targetWorld;
	private int displaySwitcherAttempts;

	@Inject
	WorldHopper(Client client, ClientThread clientThread, ChatMessageManager chatMessageManager)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.chatMessageManager = chatMessageManager;
	}

	/**
	 * Starts hopping to a world. Safe to call from any thread.
	 */
	void hop(World world)
	{
		clientThread.invoke(() -> startHop(world));
	}

	private void startHop(World world)
	{
		GameState state = client.getGameState();
		if (state != GameState.LOGIN_SCREEN && state != GameState.LOGGED_IN)
		{
			return;
		}

		if (state == GameState.LOGGED_IN && client.getWorld() == world.getId())
		{
			return;
		}

		net.runelite.api.World rsWorld = client.createWorld();
		rsWorld.setActivity(world.getActivity());
		rsWorld.setAddress(world.getAddress());
		rsWorld.setId(world.getId());
		rsWorld.setPlayerCount(world.getPlayers());
		rsWorld.setLocation(world.getLocation());
		rsWorld.setTypes(WorldUtil.toWorldTypes(world.getTypes()));

		if (state == GameState.LOGIN_SCREEN)
		{
			// on the login screen the world can be changed directly
			client.changeWorld(rsWorld);
			return;
		}

		sendMessage(new ChatMessageBuilder()
			.append(ChatColorType.NORMAL)
			.append("Hopping to party world ")
			.append(ChatColorType.HIGHLIGHT)
			.append(Integer.toString(world.getId()))
			.append(ChatColorType.NORMAL)
			.append("..")
			.build());

		targetWorld = rsWorld;
		displaySwitcherAttempts = 0;
	}

	/**
	 * Must be called from the plugin's GameTick subscriber.
	 */
	void onGameTick()
	{
		if (targetWorld == null)
		{
			return;
		}

		if (client.getWidget(InterfaceID.Worldswitcher.BUTTONS) == null)
		{
			client.openWorldHopper();

			if (++displaySwitcherAttempts >= DISPLAY_SWITCHER_MAX_ATTEMPTS)
			{
				sendMessage(new ChatMessageBuilder()
					.append(ChatColorType.NORMAL)
					.append("Failed to hop after ")
					.append(ChatColorType.HIGHLIGHT)
					.append(Integer.toString(displaySwitcherAttempts))
					.append(ChatColorType.NORMAL)
					.append(" attempts.")
					.build());
				reset();
			}
		}
		else
		{
			client.hopToWorld(targetWorld);
			reset();
		}
	}

	/**
	 * Must be called from the plugin's ChatMessage subscriber.
	 */
	void onChatMessage(ChatMessage event)
	{
		if (event.getType() == ChatMessageType.GAMEMESSAGE
			&& event.getMessage().equals("Please finish what you're doing before using the World Switcher."))
		{
			reset();
		}
	}

	/**
	 * Cancels a pending hop. Must be called on the client thread.
	 */
	void reset()
	{
		targetWorld = null;
		displaySwitcherAttempts = 0;
	}

	private void sendMessage(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
	}
}
