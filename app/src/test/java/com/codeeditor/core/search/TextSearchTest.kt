package com.codeeditor.core.search

import org.junit.Assert.assertEquals
import org.junit.Test

class TextSearchTest {

    @Test
    fun `literal search finds all occurrences`() {
        val matches = TextSearch.findAll("foo bar foo", "foo", SearchOptions())
        assertEquals(listOf(SearchMatch(0, 3), SearchMatch(8, 11)), matches)
    }

    @Test
    fun `empty query returns nothing`() {
        assertEquals(emptyList<SearchMatch>(), TextSearch.findAll("abc", "", SearchOptions()))
    }

    @Test
    fun `case sensitivity is respected`() {
        val text = "Foo foo FOO"
        val insensitive = TextSearch.findAll(text, "foo", SearchOptions())
        assertEquals(listOf(SearchMatch(0, 3), SearchMatch(4, 7), SearchMatch(8, 11)), insensitive)

        val sensitive = TextSearch.findAll(text, "foo", SearchOptions(caseSensitive = true))
        assertEquals(listOf(SearchMatch(4, 7)), sensitive)
    }

    @Test
    fun `whole word excludes partial matches`() {
        val matches = TextSearch.findAll("foo foobar foo", "foo", SearchOptions(wholeWord = true))
        assertEquals(listOf(SearchMatch(0, 3), SearchMatch(11, 14)), matches)
    }

    @Test
    fun `regex search matches patterns`() {
        val matches = TextSearch.findAll("a1 b2 c3", """\w+""", SearchOptions(regex = true))
        assertEquals(listOf(SearchMatch(0, 2), SearchMatch(3, 5), SearchMatch(6, 8)), matches)
    }

    @Test
    fun `regex search honours case sensitivity`() {
        val matches = TextSearch.findAll("ABC", "abc", SearchOptions(regex = true))
        assertEquals(listOf(SearchMatch(0, 3)), matches)
        assertEquals(emptyList<SearchMatch>(), TextSearch.findAll("ABC", "abc", SearchOptions(regex = true, caseSensitive = true)))
    }

    @Test
    fun `invalid regex is ignored`() {
        assertEquals(emptyList<SearchMatch>(), TextSearch.findAll("abc", "(", SearchOptions(regex = true)))
    }
}