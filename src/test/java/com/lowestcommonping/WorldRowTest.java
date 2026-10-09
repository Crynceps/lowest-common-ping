package com.lowestcommonping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import net.runelite.http.api.worlds.World;
import static com.lowestcommonping.TestWorlds.world;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class WorldRowTest
{
	private static Ranking ranking(int participants)
	{
		List<Participant> list = new ArrayList<>();
		for (int i = 0; i < participants; i++)
		{
			Map<Integer, Integer> pings = new HashMap<>();
			pings.put(301, 20 + i);
			pings.put(302, 30 + i);
			list.add(new Participant(i, "P" + i, i == 0, pings));
		}
		return RankingCalculator.compute(Arrays.asList(world(301), world(302)), list, RankMetric.WORST_MEMBER,
			Collections.emptyMap());
	}

	@Test
	public void keepsItsHeightWhenMemberColumnsDisappear()
	{
		WorldRow row = new WorldRow(world -> {}, world -> {});

		Ranking party = ranking(2);
		row.update(party.getRows().get(0), party, 2, 0, 80, 150);
		int partyHeight = row.getPreferredSize().height;
		assertTrue("party row height " + partyHeight, partyHeight >= 12);

		Ranking solo = ranking(1);
		row.update(solo.getRows().get(0), solo, 0, 0, 80, 150);
		assertEquals(partyHeight, row.getPreferredSize().height);
	}

	@Test
	public void hopMenuKeepsTheWorldItWasOpenedFor()
	{
		List<World> hopped = new ArrayList<>();
		WorldRow row = new WorldRow(hopped::add, world -> {});
		Ranking ranking = ranking(2);
		row.update(ranking.getRows().get(0), ranking, 2, 0, 80, 150);

		JPopupMenu menu = row.getComponentPopupMenu();
		for (PopupMenuListener listener : menu.getPopupMenuListeners())
		{
			listener.popupMenuWillBecomeVisible(new PopupMenuEvent(menu));
		}
		JMenuItem item = (JMenuItem) menu.getComponent(0);
		assertEquals("Hop to world 301", item.getText());

		// a re-sort puts another world in this row while the menu is open
		row.update(ranking.getRows().get(1), ranking, 2, 0, 80, 150);
		item.doClick(0);

		assertEquals(1, hopped.size());
		assertEquals(301, hopped.get(0).getId());
	}
}
