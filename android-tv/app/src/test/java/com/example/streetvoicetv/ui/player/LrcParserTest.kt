package com.example.streetvoicetv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LrcParserTest {

    private fun parse(text: String) = LrcParser.parse(text)!!

    @Test
    fun basicLines_withFractionsOfOneTwoAndThreeDigits() {
        val lrc = parse(
            """
            [00:01.5]one
            [00:02.25]two
            [00:03.125]three
            [01:04]four
            """.trimIndent(),
        )
        assertEquals(
            listOf(LrcLine(1_500, "one"), LrcLine(2_250, "two"), LrcLine(3_125, "three"), LrcLine(64_000, "four")),
            lrc.lines,
        )
    }

    @Test
    fun multipleTimestampsPerLine_areExpandedAndSorted() {
        val lrc = parse("[00:10.00][00:30.00]chorus\n[00:20.00]verse")
        assertEquals(listOf("chorus", "verse", "chorus"), lrc.lines.map { it.text })
        assertEquals(listOf(10_000L, 20_000L, 30_000L), lrc.lines.map { it.timeMs })
    }

    @Test
    fun unsortedInput_isSortedStably() {
        val lrc = parse("[00:05.00]b\n[00:01.00]a\n[00:05.00]c")
        assertEquals(listOf("a", "b", "c"), lrc.lines.map { it.text })
    }

    @Test
    fun offsetTag_shiftsEarlierForPositiveValues_andClampsAtZero() {
        val lrc = parse("[offset:+500]\n[00:00.20]a\n[00:02.00]b")
        assertEquals(listOf(0L, 1_500L), lrc.lines.map { it.timeMs })
        val later = parse("[offset:-1000]\n[00:01.00]a\n[00:02.00]b")
        assertEquals(listOf(2_000L, 3_000L), later.lines.map { it.timeMs })
    }

    @Test
    fun metadataBlankAndMalformedLines_areSkipped_emptyTimedLinesKept() {
        val lrc = parse(
            """
            [ar:歌手]
            [ti:歌名]

            [00:01.00]第一行
            [xx:yy]broken
            not a tag line
            [00:02.00]
            [00:03.00]  第三行
            [00:99.00]bad seconds
            """.trimIndent(),
        )
        assertEquals(listOf(LrcLine(1_000, "第一行"), LrcLine(2_000, ""), LrcLine(3_000, "第三行")), lrc.lines)
    }

    @Test
    fun windowsLineEndings() {
        assertEquals(2, parse("[00:01.00]a\r\n[00:02.00]b\r\n").lines.size)
    }

    @Test
    fun plainLyrics_orMostlyUntimed_areNotSynced() {
        assertNull(LrcParser.parse(null))
        assertNull(LrcParser.parse("   "))
        assertNull(LrcParser.parse("第一行\n第二行\n[副歌]\n第三行"))
        assertNull(LrcParser.parse("[00:01.00]only one timed line\nplain"))
        assertNull(LrcParser.parse("[00:01.00]a\n[00:02.00]b\nx\ny\nz"))
    }

    @Test
    fun indexAt_findsTheLastStartedLine() {
        val lrc = parse("[00:01.00]a\n[00:02.00]b\n[00:04.00]c")
        assertEquals(-1, lrc.indexAt(999))
        assertEquals(0, lrc.indexAt(1_000))
        assertEquals(1, lrc.indexAt(3_999))
        assertEquals(2, lrc.indexAt(100_000))
    }
}
