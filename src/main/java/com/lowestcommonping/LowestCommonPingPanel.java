package com.lowestcommonping;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.http.api.worlds.World;

/**
 * Side panel listing the worlds with every party member's ping. Only touched on the Swing thread.
 */
class LowestCommonPingPanel extends PluginPanel
{
	static final int MAX_MEMBER_COLUMNS = 4;

	private static final int MEMBER_NAME_LENGTH = 6;
	private static final int INFO_PADDING = 8;
	// Swing's HTML renderer scales CSS pixels by 1.3, so this wraps within the panel's padded width
	private static final int TEXT_WIDTH = (int) ((PANEL_WIDTH + SCROLLBAR_WIDTH - 2 * INFO_PADDING) / 1.3) - 4;
	private static final Color ODD_ROW = new Color(44, 44, 44);
	private static final ImageIcon ARROW_UP = arrow(true);
	private static final ImageIcon ARROW_DOWN = arrow(false);

	private enum SortColumn
	{
		WORLD,
		SCORE,
		MEMBER
	}

	private final LowestCommonPingPlugin plugin;
	private final LowestCommonPingConfig config;

	private final JLabel statusLabel = new JLabel();
	private final JLabel bestLabel = new JLabel();
	private final JPanel headerRow = new ViewportWidthPanel();
	private final JPanel listContainer = new JPanel(new GridLayout(0, 1));
	// rows stay in place; a re-sort only changes what each row shows
	private final List<WorldRow> rows = new ArrayList<>();

	private SortColumn sortColumn = SortColumn.SCORE;
	private long sortMemberId;
	private boolean ascending = true;

	private PanelState state = PanelState.EMPTY;
	private List<String> headerKey = Collections.emptyList();

	/**
	 * A panel that never asks for more width than its viewport, so the viewport always fits it to the visible
	 * width and the header and the rows split the same width the same way.
	 */
	private static class ViewportWidthPanel extends JPanel
	{
		ViewportWidthPanel()
		{
			super(new BorderLayout());
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(0, super.getPreferredSize().height);
		}
	}

	LowestCommonPingPanel(LowestCommonPingPlugin plugin, LowestCommonPingConfig config)
	{
		super(false);
		this.plugin = plugin;
		this.config = config;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel info = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
		info.setBorder(new EmptyBorder(INFO_PADDING, INFO_PADDING, INFO_PADDING, INFO_PADDING));
		info.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Lowest Common Ping");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		info.add(title);

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		info.add(statusLabel);

		bestLabel.setFont(FontManager.getRunescapeSmallFont());
		info.add(bestLabel);

		add(info, BorderLayout.NORTH);

		headerRow.setBackground(ColorScheme.SCROLL_TRACK_COLOR);
		headerRow.setBorder(new EmptyBorder(4, 0, 4, 0));

		listContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JPanel listWrapper = new ViewportWidthPanel();
		listWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		listWrapper.add(listContainer, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(listWrapper);
		scrollPane.setColumnHeaderView(headerRow);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
		add(scrollPane, BorderLayout.CENTER);

		updateStatus();
		rebuildHeader();
	}

	@Override
	public void onActivate()
	{
		plugin.setPanelActive(true);
	}

	@Override
	public void onDeactivate()
	{
		plugin.setPanelActive(false);
	}

	void render(PanelState newState)
	{
		state = newState;
		updateStatus();

		List<String> key = headerKey(newState.getRanking());
		if (!key.equals(headerKey))
		{
			headerKey = key;
			if (sortColumn == SortColumn.MEMBER && sortMemberIndex() < 0)
			{
				// the member we sorted by left or is no longer shown
				sortColumn = SortColumn.SCORE;
				ascending = true;
			}
			rebuildHeader();
		}

		refreshRows();
	}

	private void updateStatus()
	{
		Ranking ranking = state.getRanking();
		List<Participant> participants = ranking.getParticipants();

		StringBuilder sb = new StringBuilder();
		if (!state.isSharingAvailable())
		{
			sb.append("Party sharing is unavailable because another plugin uses the same message name."
				+ " Showing only your pings.");
		}
		else if (!state.isInParty())
		{
			sb.append("Not in a party, showing only your pings. Create or join a party with the Party plugin"
				+ " to compare pings with friends.");
		}
		else if (state.isJoining())
		{
			sb.append("Joining party...");
		}
		else
		{
			int others = Math.max(0, participants.size() - 1) + state.getWaitingFor().size();
			int sharing = others + (state.isSharingEnabled() ? 1 : 0);
			sb.append("Party of ").append(state.getPartySize()).append(", ")
				.append(sharing).append(sharing == 1 ? " member shares" : " members share").append(" pings.");
			if (!state.isSharingEnabled())
			{
				sb.append(" You only watch.");
			}

			if (others == 0 && state.getListeners().isEmpty())
			{
				sb.append(" Nobody else in the party uses Lowest Common Ping yet.");
			}
			else
			{
				appendNames(sb, " Watching only: ", state.getListeners());
				appendNames(sb, " Waiting for first pings from ", state.getWaitingFor());
				appendNames(sb, " Without the plugin: ", state.getMembersWithoutPlugin());
			}

			if (participants.size() > MAX_MEMBER_COLUMNS)
			{
				sb.append(" Columns show ").append(MAX_MEMBER_COLUMNS).append(" of ").append(participants.size())
					.append(" members; hover a world to see everyone.");
			}
		}

		if (state.getCandidateWorlds() > 0 && !participants.isEmpty())
		{
			int measured = 0;
			for (WorldRank rank : ranking.getRows())
			{
				if (rank.getPing(0) != PingEstimate.UNKNOWN)
				{
					measured++;
				}
			}
			sb.append("<br>You measured ").append(measured).append(" of ").append(ranking.getRows().size())
				.append(" worlds.");
		}

		statusLabel.setText("<html><body style='width:" + TEXT_WIDTH + "px'>" + sb + "</body></html>");

		WorldRank best = ranking.getBest();
		if (best == null)
		{
			bestLabel.setText(state.getCandidateWorlds() == 0 ? "Loading worlds..." : "Waiting for ping data...");
			bestLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}
		else
		{
			RegionFilter region = RegionFilter.of(best.getWorld().getRegion());
			String label = participants.size() > 1
				? ranking.getMetric() == RankMetric.AVERAGE ? " average" : " worst"
				: "";
			bestLabel.setText("Best: world " + best.getWorldId() + (region == null ? "" : " " + region.getCode())
				+ ", " + best.getScore() + " ms" + label);
			bestLabel.setForeground(PingColors.of(best.getScore(), config.goodPing(), config.badPing()));
		}
	}

	private static void appendNames(StringBuilder sb, String prefix, List<String> names)
	{
		if (!names.isEmpty())
		{
			sb.append(prefix).append(Names.escapeHtml(String.join(", ", names))).append('.');
		}
	}

	private static List<String> headerKey(Ranking ranking)
	{
		List<String> key = new ArrayList<>();
		key.add(ranking.getMetric().name());
		for (Participant participant : ranking.getParticipants())
		{
			key.add(participant.getMemberId() + ":" + shortName(participant));
		}
		return key;
	}

	private static int memberColumns(Ranking ranking)
	{
		int participants = ranking.getParticipants().size();
		return participants > 1 ? Math.min(participants, MAX_MEMBER_COLUMNS) : 0;
	}

	/**
	 * @return the column of the member we sort by, or -1 if that member is not shown
	 */
	private int sortMemberIndex()
	{
		Ranking ranking = state.getRanking();
		int columns = memberColumns(ranking);
		for (int i = 0; i < columns; i++)
		{
			if (ranking.getParticipants().get(i).getMemberId() == sortMemberId)
			{
				return i;
			}
		}
		return -1;
	}

	private static String shortName(Participant participant)
	{
		if (participant.isLocal())
		{
			return "You";
		}

		String name = participant.getName();
		return name.length() > MEMBER_NAME_LENGTH ? name.substring(0, MEMBER_NAME_LENGTH) : name;
	}

	private void rebuildHeader()
	{
		Ranking ranking = state.getRanking();
		List<Participant> participants = ranking.getParticipants();
		headerRow.removeAll();

		JPanel left = new JPanel(new BorderLayout());
		left.setOpaque(false);
		left.setPreferredSize(new Dimension(WorldRow.WORLD_WIDTH + WorldRow.SCORE_WIDTH, 16));

		JLabel world = headerLabel("World", sortColumn == SortColumn.WORLD, true, "Sort by world number");
		world.setHorizontalAlignment(SwingConstants.LEFT);
		world.setBorder(new EmptyBorder(0, 5, 0, 0));
		world.setPreferredSize(new Dimension(WorldRow.WORLD_WIDTH, 16));
		world.addMouseListener(sortListener(SortColumn.WORLD, 0));
		left.add(world, BorderLayout.WEST);

		String scoreName = participants.size() > 1 ? ranking.getMetric().getShortName() : "Ping";
		String scoreTooltip = participants.size() > 1
			? "Sort by the party's " + ranking.getMetric().toString().toLowerCase() + " ping"
			: "Sort by ping";
		JLabel score = headerLabel(scoreName, sortColumn == SortColumn.SCORE, true, scoreTooltip);
		score.addMouseListener(sortListener(SortColumn.SCORE, 0));
		left.add(score, BorderLayout.CENTER);
		headerRow.add(left, BorderLayout.WEST);

		int columns = memberColumns(ranking);
		if (columns > 0)
		{
			int sortIndex = sortColumn == SortColumn.MEMBER ? sortMemberIndex() : -1;
			JPanel members = new JPanel(new GridLayout(1, columns));
			members.setOpaque(false);
			for (int i = 0; i < columns; i++)
			{
				Participant participant = participants.get(i);
				// no arrow on member columns: they are too narrow for the name and an icon
				JLabel label = headerLabel(shortName(participant), i == sortIndex, false,
					"Sort by " + participant.getName() + "'s ping");
				label.addMouseListener(sortListener(SortColumn.MEMBER, participant.getMemberId()));
				members.add(label);
			}
			headerRow.add(members, BorderLayout.CENTER);
		}

		headerRow.revalidate();
		headerRow.repaint();
	}

	private JLabel headerLabel(String text, boolean active, boolean arrow, String tooltip)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setHorizontalTextPosition(SwingConstants.LEFT);
		label.setIconTextGap(2);
		label.setForeground(active ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
		label.setIcon(active && arrow ? (ascending ? ARROW_UP : ARROW_DOWN) : null);
		label.setToolTipText(tooltip);
		return label;
	}

	private MouseAdapter sortListener(SortColumn column, long memberId)
	{
		return new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					sortBy(column, memberId);
				}
			}
		};
	}

	private void sortBy(SortColumn column, long memberId)
	{
		if (sortColumn == column && (column != SortColumn.MEMBER || sortMemberId == memberId))
		{
			ascending = !ascending;
		}
		else
		{
			sortColumn = column;
			sortMemberId = memberId;
			ascending = true;
		}

		rebuildHeader();
		refreshRows();
	}

	private void refreshRows()
	{
		Ranking ranking = state.getRanking();
		int columns = memberColumns(ranking);
		int goodPing = config.goodPing();
		int badPing = config.badPing();

		List<WorldRank> sorted = new ArrayList<>(ranking.getRows());
		sorted.sort(comparator());

		boolean resized = rows.size() != sorted.size();
		while (rows.size() < sorted.size())
		{
			WorldRow row = new WorldRow(plugin::hopTo, this::onDoubleClick);
			rows.add(row);
			listContainer.add(row);
		}
		while (rows.size() > sorted.size())
		{
			listContainer.remove(rows.remove(rows.size() - 1));
		}

		for (int i = 0; i < sorted.size(); i++)
		{
			WorldRow row = rows.get(i);
			row.update(sorted.get(i), ranking, columns, state.getConnectedWorld(), goodPing, badPing);
			row.setRowBackground(i % 2 == 0 ? ODD_ROW : ColorScheme.DARK_GRAY_COLOR);
		}

		if (resized)
		{
			listContainer.revalidate();
		}
		listContainer.repaint();
	}

	private void onDoubleClick(World world)
	{
		if (config.hopOnDoubleClick())
		{
			plugin.hopTo(world);
		}
	}

	private Comparator<WorldRank> comparator()
	{
		switch (sortColumn)
		{
			case WORLD:
			{
				Comparator<WorldRank> byWorld = Comparator.comparingInt(WorldRank::getWorldId);
				return ascending ? byWorld : byWorld.reversed();
			}
			case MEMBER:
			{
				int index = sortMemberIndex();
				if (index >= 0)
				{
					return (a, b) ->
					{
						int c = comparePings(a.getPing(index), b.getPing(index));
						return c != 0 ? c : RankingCalculator.ORDER.compare(a, b);
					};
				}
				return RankingCalculator.ORDER;
			}
			case SCORE:
			default:
			{
				if (ascending)
				{
					return RankingCalculator.ORDER;
				}
				return Comparator.comparingInt(WorldRank::getTier)
					.thenComparing((a, b) -> comparePings(a.getScore(), b.getScore()))
					.thenComparingInt(WorldRank::getWorldId);
			}
		}
	}

	/**
	 * Orders measured pings by the current direction; timeouts and unmeasured worlds always go last.
	 */
	private int comparePings(int a, int b)
	{
		int ca = pingClass(a);
		int cb = pingClass(b);
		if (ca != cb)
		{
			return Integer.compare(ca, cb);
		}
		if (ca != 0)
		{
			return 0;
		}
		return ascending ? Integer.compare(a, b) : Integer.compare(b, a);
	}

	private static int pingClass(int ms)
	{
		if (ms >= 0)
		{
			return 0;
		}
		return ms == PingEstimate.TIMEOUT ? 1 : 2;
	}

	private static ImageIcon arrow(boolean up)
	{
		BufferedImage image = new BufferedImage(7, 4, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(ColorScheme.BRAND_ORANGE);
		int[] xs = {0, 7, 3};
		int[] ys = up ? new int[]{4, 4, 0} : new int[]{0, 0, 4};
		g.fillPolygon(xs, ys, 3);
		g.dispose();
		return new ImageIcon(image);
	}
}
