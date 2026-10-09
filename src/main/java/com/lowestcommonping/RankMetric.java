package com.lowestcommonping;

public enum RankMetric
{
	WORST_MEMBER("Worst member", "Worst"),
	AVERAGE("Average", "Avg");

	private final String displayName;
	private final String shortName;

	RankMetric(String displayName, String shortName)
	{
		this.displayName = displayName;
		this.shortName = shortName;
	}

	String getShortName()
	{
		return shortName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
