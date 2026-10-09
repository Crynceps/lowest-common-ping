package com.lowestcommonping;

import java.util.EnumSet;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldType;

final class TestWorlds
{
	static final int US = 0;
	static final int UK = 1;
	static final int AU = 3;
	static final int DE = 7;

	private TestWorlds()
	{
	}

	static World world(int id)
	{
		return world(id, UK, EnumSet.of(WorldType.MEMBERS));
	}

	static World world(int id, int location, EnumSet<WorldType> types)
	{
		return World.builder()
			.id(id)
			.address("oldschool" + (id - 300) + ".runescape.com")
			.activity("-")
			.location(location)
			.players(500)
			.types(types)
			.build();
	}
}
