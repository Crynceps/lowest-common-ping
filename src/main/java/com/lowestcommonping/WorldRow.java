package com.lowestcommonping;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import net.runelite.client.ui.FontManager;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldType;

/**
 * A row of the world table. Rows are reused between refreshes; only their contents and order change.
 */
class WorldRow extends JPanel
{
	static final int WORLD_WIDTH = 50;
	static final int SCORE_WIDTH = 44;

	private static final Color CURRENT_WORLD = new Color(66, 227, 17);
	private static final Color MEMBERS_WORLD = new Color(210, 193, 53);
	private static final Color FREE_WORLD = new Color(200, 200, 200);

	private final JLabel worldLabel = new JLabel();
	private final JLabel scoreLabel = new JLabel();
	private final JPanel memberPanel = new JPanel(new GridLayout(1, 1));
	private final JMenuItem hopItem = new JMenuItem();
	private JLabel[] memberLabels = new JLabel[0];

	private WorldRank rank;
	private Ranking ranking;
	private Color baseBackground;
	// rows show a different world after a re-sort, so remember which world a menu or click was for
	private World menuWorld;
	private World pressedWorld;

	/**
	 * @param onHop         called when the hop menu option is used
	 * @param onDoubleClick called when the row is double-clicked
	 */
	WorldRow(Consumer<World> onHop, Consumer<World> onDoubleClick)
	{
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(2, 0, 2, 0));

		// fixed width; the height follows the labels so the row keeps its size without member columns
		JPanel left = new JPanel(new BorderLayout())
		{
			@Override
			public Dimension getPreferredSize()
			{
				return new Dimension(WORLD_WIDTH + SCORE_WIDTH, super.getPreferredSize().height);
			}
		};
		left.setOpaque(false);

		worldLabel.setFont(FontManager.getRunescapeSmallFont());
		worldLabel.setBorder(new EmptyBorder(0, 5, 0, 0));
		worldLabel.setPreferredSize(new Dimension(WORLD_WIDTH, 0));
		left.add(worldLabel, BorderLayout.WEST);

		scoreLabel.setFont(FontManager.getRunescapeSmallFont());
		scoreLabel.setHorizontalAlignment(SwingConstants.CENTER);
		left.add(scoreLabel, BorderLayout.CENTER);

		memberPanel.setOpaque(false);

		add(left, BorderLayout.WEST);
		add(memberPanel, BorderLayout.CENTER);

		hopItem.addActionListener(e ->
		{
			if (menuWorld != null)
			{
				onHop.accept(menuWorld);
			}
		});
		JPopupMenu popupMenu = new JPopupMenu();
		popupMenu.setBorder(new EmptyBorder(5, 5, 5, 5));
		popupMenu.add(hopItem);
		popupMenu.addPopupMenuListener(new PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e)
			{
				menuWorld = rank == null ? null : rank.getWorld();
				hopItem.setText(menuWorld == null ? "Hop" : "Hop to world " + menuWorld.getId());
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e)
			{
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e)
			{
			}
		});
		setComponentPopupMenu(popupMenu);

		// registers the row with the tooltip manager; the text is built on demand in getToolTipText
		setToolTipText("");

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.getClickCount() == 1)
				{
					pressedWorld = rank == null ? null : rank.getWorld();
				}
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				// only if the row still shows the world the double-click started on
				if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && rank != null
					&& pressedWorld != null && rank.getWorldId() == pressedWorld.getId())
				{
					onDoubleClick.accept(rank.getWorld());
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (baseBackground != null)
				{
					setBackground(baseBackground.brighter());
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				if (baseBackground != null)
				{
					setBackground(baseBackground);
				}
			}
		});
	}

	void setRowBackground(Color color)
	{
		baseBackground = color;
		setBackground(getMousePosition() != null ? color.brighter() : color);
	}

	void update(WorldRank rank, Ranking ranking, int memberColumns, int connectedWorld, int goodPing, int badPing)
	{
		this.rank = rank;
		this.ranking = ranking;

		World world = rank.getWorld();
		RegionFilter region = RegionFilter.of(world.getRegion());
		worldLabel.setText(world.getId() + " " + (region == null ? "" : region.getCode()));
		if (world.getId() == connectedWorld)
		{
			worldLabel.setForeground(CURRENT_WORLD);
		}
		else
		{
			worldLabel.setForeground(isMembers(world) ? MEMBERS_WORLD : FREE_WORLD);
		}

		scoreLabel.setText(PingColors.text(scoreOf(rank)));
		scoreLabel.setForeground(PingColors.of(scoreOf(rank), goodPing, badPing));

		if (memberLabels.length != memberColumns)
		{
			memberPanel.removeAll();
			memberPanel.setLayout(new GridLayout(1, Math.max(1, memberColumns)));
			memberLabels = new JLabel[memberColumns];
			for (int i = 0; i < memberColumns; i++)
			{
				JLabel label = new JLabel();
				label.setFont(FontManager.getRunescapeSmallFont());
				label.setHorizontalAlignment(SwingConstants.CENTER);
				memberLabels[i] = label;
				memberPanel.add(label);
			}
			revalidate();
		}

		for (int i = 0; i < memberColumns; i++)
		{
			int ms = rank.getPing(i);
			memberLabels[i].setText(PingColors.text(ms));
			memberLabels[i].setForeground(PingColors.of(ms, goodPing, badPing));
		}
	}

	/**
	 * Shows the worst ping when a world is unreachable for someone, so the score never hides a timeout.
	 */
	private static int scoreOf(WorldRank rank)
	{
		return rank.getTier() == WorldRank.TIER_TIMEOUT ? PingEstimate.TIMEOUT : rank.getScore();
	}

	private static boolean isMembers(World world)
	{
		return world.getTypes() != null && world.getTypes().contains(WorldType.MEMBERS);
	}

	@Override
	public String getToolTipText(MouseEvent event)
	{
		if (rank == null || ranking == null)
		{
			return null;
		}

		World world = rank.getWorld();
		RegionFilter region = RegionFilter.of(world.getRegion());
		StringBuilder sb = new StringBuilder("<html><b>World ").append(world.getId()).append("</b>");
		if (region != null)
		{
			sb.append(" - ").append(region.getDisplayName());
		}

		String activity = world.getActivity();
		if (activity != null && !activity.isEmpty() && !activity.equals("-"))
		{
			sb.append("<br>").append(Names.escapeHtml(activity));
		}
		if (world.getPlayers() >= 0)
		{
			sb.append("<br>").append(world.getPlayers()).append(" players");
		}
		sb.append("<br>");

		List<Participant> participants = ranking.getParticipants();
		for (int i = 0; i < participants.size(); i++)
		{
			Participant participant = participants.get(i);
			int ms = rank.getPing(i);
			sb.append("<br>").append(Names.escapeHtml(participant.getName())).append(": ")
				.append(PingColors.longText(ms));
			if (participant.isLocal())
			{
				sb.append(describeLocal(ranking.getLocalEstimate(world.getId())));
			}
		}

		if (participants.size() > 1)
		{
			switch (rank.getTier())
			{
				case WorldRank.TIER_COMPLETE:
					sb.append("<br><br>Worst ").append(rank.getWorst()).append(" ms, average ")
						.append(rank.getAverage()).append(" ms");
					break;
				case WorldRank.TIER_MISSING:
					sb.append("<br><br>Worst of those measured: ").append(rank.getWorst()).append(" ms");
					break;
				case WorldRank.TIER_TIMEOUT:
					sb.append("<br><br>Unreachable for some members");
					break;
				default:
					break;
			}
		}
		if (world.getPlayers() >= WorldRank.FULL_WORLD_PLAYERS)
		{
			sb.append("<br>World is full");
		}
		sb.append("</html>");
		return sb.toString();
	}

	private static String describeLocal(PingEstimate estimate)
	{
		if (estimate.getMs() < 0)
		{
			return "";
		}

		String loss = estimate.isLoss() ? ", some lost" : "";
		if (estimate.isFresh() && estimate.getSamples() > 1)
		{
			return " (median of " + estimate.getSamples() + " pings" + loss + ")";
		}
		long seconds = Math.max(0, estimate.getAgeMs() / 1000);
		return seconds < 60
			? " (" + seconds + "s ago" + loss + ")"
			: " (" + (seconds / 60) + "m ago" + loss + ")";
	}
}
