package com.mahava.app

import com.google.gson.JsonParser
import com.mahava.app.content.PartnerLogFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PartnerLogFormatTest {
    @Test fun newFieldsAreShownInPersianWithoutRawKeys() {
        val log = JsonParser.parseString(
            """{"epochDay":1,"moods":"happy,focused","energy":"medium","energyScore":5,"overallFeeling":"great",
               "sleepHours":7.5,"sleepMinutes":450,"appetite":"normal","waterIntakeMl":2000,"painType":"cramping",
               "physicalSymptomSeverities":"cramps:2"}"""
        ).asJsonObject
        val lines = PartnerLogFormat.lines(log)
        val labels = lines.map { it.label }
        labels.forEach { l -> assertFalse("raw key $l", l.any { it in 'a'..'z' }) }
        lines.forEach { l -> assertFalse("raw value ${l.value}", l.value.contains("focused") || l.value.contains("great")) }
        assertEquals(1, labels.count { it == "ساعت خواب" })
        assertEquals(1, labels.count { it == "انرژی" })
        assertEquals("۵ از ۵", lines.first { it.label == "انرژی" }.value)
        assertEquals("عالی", lines.first { it.label == "حال کلی" }.value)
    }

    @Test fun sleepMinutesAloneBecomesHours() {
        val log = JsonParser.parseString("""{"sleepMinutes":390}""").asJsonObject
        assertEquals("۶٫۵ ساعت", PartnerLogFormat.lines(log).single().value)
    }
}

class SignalLabelFallbackTest {
    @Test fun newMoodKeysHavePersianLabels() {
        assertEquals("تمرکز خوب", com.mahava.app.content.TodaySignalContent.label("mood", "focused"))
        assertEquals("تمرکز کم", com.mahava.app.content.TodaySignalContent.label("mood", "unfocused"))
    }
}
