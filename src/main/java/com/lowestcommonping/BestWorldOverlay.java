package com.lowestcommonping;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Lists the best worlds for the party while at least one other member shares pings.
 */
class BestWorldOverlay extends OverlayPanel
{
	private final LowestCommonPingPlugin plugin;
	private final LowestCommonPingConfig config;

	@Inject
	BestWorldOverlay(LowestCommonPingPlugin plugin, LowestCommonPingConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, "Configure", "Lowest Common Ping overlay");
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay())
		{
			return null;
		}

		Ranking ranking = plugin.getLatestRanking();
		if (ranking.getParticipants().size() < 2)
		{
			return null;
		}

		List<WorldRank> rows = ranking.getRows();
		int limit = config.overlayWorlds();
		int goodPing = config.goodPing();
		int badPing = config.badPing();
		int shown = 0;
		for (int i = 0; i < rows.size() && shown < limit; i++)
		{
			WorldRank rank = rows.get(i);
			if (rank.getTier() != WorldRank.TIER_COMPLETE)
			{
				break;
			}
			if (!rank.isRecommendable())
			{
				continue;
			}

			if (shown == 0)
			{
				panelComponent.getChildren().add(TitleComponent.builder()
					.text("Party worlds (" + ranking.getMetric().getShortName().toLowerCase() + ")")
					.build());
			}

			RegionFilter region = RegionFilter.of(rank.getWorld().getRegion());
			panelComponent.getChildren().add(LineComponent.builder()
				.left(rank.getWorldId() + (region == null ? "" : " " + region.getCode()))
				.right(rank.getScore() + " ms")
				.rightColor(PingColors.of(rank.getScore(), goodPing, badPing))
				.build());
			shown++;
		}

		if (shown == 0)
		{
			return null;
		}
		return super.render(graphics);
	}
}
