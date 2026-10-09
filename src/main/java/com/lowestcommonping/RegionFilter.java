package com.lowestcommonping;

import net.runelite.http.api.worlds.WorldRegion;

public enum RegionFilter
{
	UNITED_STATES("United States", "US", WorldRegion.UNITED_STATES_OF_AMERICA),
	UNITED_KINGDOM("United Kingdom", "UK", WorldRegion.UNITED_KINGDOM),
	GERMANY("Germany", "DE", WorldRegion.GERMANY),
	AUSTRALIA("Australia", "AU", WorldRegion.AUSTRALIA),
	BRAZIL("Brazil", "BR", WorldRegion.BRAZIL),
	JAPAN("Japan", "JP", WorldRegion.JAPAN),
	SINGAPORE("Singapore", "SG", WorldRegion.SINGAPORE),
	SOUTH_AFRICA("South Africa", "ZA", WorldRegion.SOUTH_AFRICA);

	private final String displayName;
	private final String code;
	private final WorldRegion region;

	RegionFilter(String displayName, String code, WorldRegion region)
	{
		this.displayName = displayName;
		this.code = code;
		this.region = region;
	}

	String getDisplayName()
	{
		return displayName;
	}

	String getCode()
	{
		return code;
	}

	/**
	 * @return the matching filter, or null for an unknown region
	 */
	static RegionFilter of(WorldRegion region)
	{
		if (region == null)
		{
			return null;
		}

		for (RegionFilter filter : values())
		{
			if (filter.region == region)
			{
				return filter;
			}
		}
		return null;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
