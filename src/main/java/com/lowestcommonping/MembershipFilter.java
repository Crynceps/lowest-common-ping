package com.lowestcommonping;

public enum MembershipFilter
{
	ANY("Members and free"),
	MEMBERS("Members only"),
	FREE("Free only");

	private final String displayName;

	MembershipFilter(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
