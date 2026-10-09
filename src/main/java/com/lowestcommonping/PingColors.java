package com.lowestcommonping;

import java.awt.Color;
import net.runelite.client.ui.ColorScheme;

final class PingColors
{
	static final Color UNKNOWN = ColorScheme.MEDIUM_GRAY_COLOR;

	private PingColors()
	{
	}

	static Color of(int ms, int goodPing, int badPing)
	{
		if (ms == PingEstimate.TIMEOUT)
		{
			return ColorScheme.PROGRESS_ERROR_COLOR;
		}
		if (ms < 0)
		{
			return UNKNOWN;
		}
		if (ms <= goodPing)
		{
			return ColorScheme.PROGRESS_COMPLETE_COLOR;
		}
		if (ms <= badPing)
		{
			return ColorScheme.PROGRESS_INPROGRESS_COLOR;
		}
		return ColorScheme.PROGRESS_ERROR_COLOR;
	}

	/**
	 * @return short text for a table cell
	 */
	static String text(int ms)
	{
		if (ms == PingEstimate.TIMEOUT)
		{
			return "t/o";
		}
		if (ms < 0)
		{
			return "-";
		}
		return Integer.toString(ms);
	}

	/**
	 * @return text for tooltips and the overlay
	 */
	static String longText(int ms)
	{
		if (ms == PingEstimate.TIMEOUT)
		{
			return "no response";
		}
		if (ms < 0)
		{
			return "not measured yet";
		}
		return ms + " ms";
	}

	static String toHex(Color color)
	{
		return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}
}
