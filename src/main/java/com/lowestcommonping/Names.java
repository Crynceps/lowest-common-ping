package com.lowestcommonping;

/**
 * Helpers for names received from other party members, which are untrusted input.
 */
final class Names
{
	// longest possible RuneScape name
	private static final int MAX_LENGTH = 12;

	private Names()
	{
	}

	/**
	 * Keeps only characters that can appear in a RuneScape name, so Swing never interprets the name as HTML.
	 *
	 * @return the cleaned name, or null if nothing usable is left
	 */
	static String sanitize(String name)
	{
		if (name == null)
		{
			return null;
		}

		StringBuilder sb = new StringBuilder(Math.min(name.length(), MAX_LENGTH));
		for (int i = 0; i < name.length() && sb.length() < MAX_LENGTH; i++)
		{
			char c = name.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_')
			{
				sb.append(c);
			}
			else if (c == ' ' || c == ' ')
			{
				sb.append(' ');
			}
		}

		String result = sb.toString().trim();
		return result.isEmpty() ? null : result;
	}

	/**
	 * Escapes text for use inside an HTML tooltip.
	 */
	static String escapeHtml(String text)
	{
		StringBuilder sb = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			switch (c)
			{
				case '<':
					sb.append("&lt;");
					break;
				case '>':
					sb.append("&gt;");
					break;
				case '&':
					sb.append("&amp;");
					break;
				case '"':
					sb.append("&quot;");
					break;
				default:
					sb.append(c);
			}
		}
		return sb.toString();
	}
}
