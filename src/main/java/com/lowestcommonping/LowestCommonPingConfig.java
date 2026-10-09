package com.lowestcommonping;

import java.util.Collections;
import java.util.Set;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(LowestCommonPingConfig.GROUP)
public interface LowestCommonPingConfig extends Config
{
	String GROUP = "lowestcommonping";

	@ConfigSection(
		name = "Worlds",
		description = "Which worlds are pinged and listed",
		position = 10
	)
	String worldsSection = "worlds";

	@ConfigSection(
		name = "Pinging",
		description = "How pings are measured and shared",
		position = 20
	)
	String pingingSection = "pinging";

	@ConfigSection(
		name = "Display",
		description = "Panel and overlay settings",
		position = 30
	)
	String displaySection = "display";

	@ConfigItem(
		keyName = "rankBy",
		name = "Rank worlds by",
		description = "Worst member picks the world where the slowest member has the lowest ping, which is fairest"
			+ " for the group. Average picks the lowest ping averaged over all members.",
		position = 1
	)
	default RankMetric rankBy()
	{
		return RankMetric.WORST_MEMBER;
	}

	@ConfigItem(
		keyName = "averageWindow",
		name = "Averaging window",
		description = "Your ping to a world is the median of the pings measured within this many seconds",
		position = 2
	)
	@Range(min = 10, max = 120)
	@Units(Units.SECONDS)
	default int averageWindow()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "membership",
		name = "World type",
		description = "Show members worlds, free worlds or both",
		position = 11,
		section = worldsSection
	)
	default MembershipFilter membership()
	{
		return MembershipFilter.ANY;
	}

	@ConfigItem(
		keyName = "regions",
		name = "Regions",
		description = "Only show worlds in these regions. Select none to show all regions.",
		position = 12,
		section = worldsSection
	)
	default Set<RegionFilter> regions()
	{
		return Collections.emptySet();
	}

	@ConfigItem(
		keyName = "skillTotalWorlds",
		name = "Skill total worlds",
		description = "Include worlds that require a minimum total level",
		position = 13,
		section = worldsSection
	)
	default boolean skillTotalWorlds()
	{
		return false;
	}

	@ConfigItem(
		keyName = "sharePings",
		name = "Share pings with party",
		description = "Send your world pings to your RuneLite party so members can find a world that suits everyone."
			+ " Pings reveal roughly which region you are in. When off, you still see the pings of members who share.",
		position = 21,
		section = pingingSection
	)
	default boolean sharePings()
	{
		return true;
	}

	@ConfigItem(
		keyName = "backgroundPinging",
		name = "Ping while panel is closed",
		description = "Keep measuring while the side panel is closed, but only when another party member uses this plugin",
		position = 22,
		section = pingingSection
	)
	default boolean backgroundPinging()
	{
		return true;
	}

	@ConfigItem(
		keyName = "maxPingsPerSecond",
		name = "Max pings per second",
		description = "Upper limit on how many worlds are pinged per second while the side panel is open."
			+ " While it is closed, at most 2 per second.",
		position = 23,
		section = pingingSection
	)
	@Range(min = 1, max = 4)
	default int maxPingsPerSecond()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "goodPing",
		name = "Good ping",
		description = "Pings up to this value are shown in green",
		position = 31,
		section = displaySection
	)
	@Range(min = 1, max = 1000)
	@Units(Units.MILLISECONDS)
	default int goodPing()
	{
		return 80;
	}

	@ConfigItem(
		keyName = "badPing",
		name = "Bad ping",
		description = "Pings above this value are shown in red; values in between in orange",
		position = 32,
		section = displaySection
	)
	@Range(min = 1, max = 2000)
	@Units(Units.MILLISECONDS)
	default int badPing()
	{
		return 150;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show overlay",
		description = "Show the best worlds for the party in-game while another member shares pings",
		position = 33,
		section = displaySection
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayWorlds",
		name = "Overlay worlds",
		description = "Number of worlds listed in the overlay",
		position = 34,
		section = displaySection
	)
	@Range(min = 1, max = 5)
	default int overlayWorlds()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "hopOnDoubleClick",
		name = "Double-click to hop",
		description = "Hop to a world by double-clicking it in the panel",
		position = 35,
		section = displaySection
	)
	default boolean hopOnDoubleClick()
	{
		return true;
	}
}
