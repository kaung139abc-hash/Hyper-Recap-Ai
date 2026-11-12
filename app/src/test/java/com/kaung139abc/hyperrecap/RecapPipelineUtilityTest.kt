package com.kaung139abc.hyperrecap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrtTranscriptTest {
    @Test
    fun parsesCommaAndDotMillisecondSeparatorsAndLongTimestamps() {
        val source = """1
01:02:03,450 --> 01:02:05,900
A character finds a hidden letter.

2
01:02:06.000 --> 01:02:08.125
The truth is finally revealed.
"""
        val cues = SrtTranscript.parse(source)

        assertEquals(2, cues.size)
        assertEquals(3_723_450L, cues[0].startMs)
        assertEquals(3_725_900L, cues[0].endMs)
        assertEquals(3_726_000L, cues[1].startMs)
        assertEquals("The truth is finally revealed.", cues[1].text)
    }

    @Test
    fun ignoresInvalidOrEmptyCuesAndKeepsValidCueText() {
        val source = """1
00:00:05,000 --> 00:00:04,000
Invalid reversed timing.

2
00:00:06,000 --> 00:00:08,000

3
00:00:09,000 --> 00:00:11,000
A valid event.
"""
        val cues = SrtTranscript.parse(source)

        assertEquals(1, cues.size)
        assertEquals("A valid event.", cues.single().text)
    }

    @Test
    fun serializesAndParsesChronologicalCuesWithoutLosingTiming() {
        val original = listOf(
            SrtTranscript.Cue(1_250L, 2_750L, "အစပိုင်း"),
            SrtTranscript.Cue(3_000L, 4_500L, "အဆုံးပိုင်း")
        )

        val parsed = SrtTranscript.parse(SrtTranscript.toSrt(original))

        assertEquals(original, parsed)
    }

    @Test
    fun summaryIsChronologicalAndNeverExceedsRequestedCueCount() {
        val cues = (0 until 20).map { index ->
            SrtTranscript.Cue(
                index * 60_000L,
                index * 60_000L + 5_000L,
                "Character event number $index reveals a secret and changes the story."
            )
        }

        val summary = SrtTranscript.summarize(cues, maxCues = 6)

        assertTrue(summary.size <= 6)
        assertEquals(summary.sortedBy { it.startMs }, summary)
        assertTrue(summary.isNotEmpty())
    }
}

class SubtitleDraftTest {
    @Test
    fun createsSequentialValidSrtCuesForMyanmarNarration() {
        val srt = SubtitleDraft.fromNarration("မင်္ဂလာပါ။ ဇာတ်လမ်းက ဒီနေရာမှာ စတင်ပါတယ်။")
        val cues = SrtTranscript.parse(srt)

        assertEquals(2, cues.size)
        assertTrue(cues[0].endMs <= cues[1].startMs)
        assertTrue(cues.all { it.text.isNotBlank() })
    }

    @Test
    fun emptyNarrationProducesEmptySubtitleFile() {
        assertEquals("", SubtitleDraft.fromNarration("   "))
    }
}
