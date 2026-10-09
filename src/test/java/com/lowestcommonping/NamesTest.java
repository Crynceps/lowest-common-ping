package com.lowestcommonping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class NamesTest
{
	@Test
	public void keepsValidNames()
	{
		assertEquals("Zezima", Names.sanitize("Zezima"));
		assertEquals("Iron Man_99", Names.sanitize("Iron Man_99"));
		assertEquals("a-b", Names.sanitize("a-b"));
	}

	@Test
	public void replacesNonBreakingSpaces()
	{
		assertEquals("Iron Man", Names.sanitize("Iron Man"));
	}

	@Test
	public void stripsMarkup()
	{
		assertEquals("htmlbHib", Names.sanitize("<html><b>Hi</b>"));
	}

	@Test
	public void limitsLength()
	{
		assertEquals("abcdefghijkl", Names.sanitize("abcdefghijklmnop"));
	}

	@Test
	public void rejectsEmptyNames()
	{
		assertNull(Names.sanitize(null));
		assertNull(Names.sanitize(""));
		assertNull(Names.sanitize("<>&"));
		assertNull(Names.sanitize("   "));
	}

	@Test
	public void escapesHtml()
	{
		assertEquals("&lt;b&gt; &amp; &quot;", Names.escapeHtml("<b> & \""));
	}
}
