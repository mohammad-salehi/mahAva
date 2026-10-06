package com.mahava.app

import com.mahava.app.pattern.PatternAnalyzer
import com.mahava.app.pattern.PeriodStartPoint
import com.mahava.app.pattern.SymptomLogPoint
import com.google.gson.Gson
import com.mahava.app.content.ContentBank
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.mahava.app.content.ContentRepository

class PatternTest {
    @Test fun requiresMinimumCycles() {
        val insights = PatternAnalyzer.analyze(
            listOf(SymptomLogPoint(10, "bloating")),
            listOf(PeriodStartPoint(20))
        )
        assertTrue(insights.isEmpty())
    }

    @Test fun nonCausalWording() {
        val starts = listOf(PeriodStartPoint(30), PeriodStartPoint(60), PeriodStartPoint(90))
        val symptoms = listOf(
            SymptomLogPoint(25, "bloating"),
            SymptomLogPoint(55, "bloating"),
            SymptomLogPoint(85, "bloating")
        )
        val insights = PatternAnalyzer.analyze(symptoms, starts)
        assertTrue(insights.isNotEmpty())
        insights.forEach {
            assertFalse(it.textFa.contains("حتماً"))
            assertFalse(it.textFa.contains("هورمون‌ها باعث"))
            assertTrue(it.textFa.contains("ثبت"))
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContentBankTest {
    @Test fun schemaAndNoFabricatedReview() {
        val ctx = RuntimeEnvironment.getApplication()
        val repo = ContentRepository(ctx)
        val bank = repo.load()
        assertTrue(bank.items.isNotEmpty())
        assertTrue(repo.validateNoFabricatedReview())
        bank.items.forEach { item ->
            assertTrue(item.id.isNotBlank())
            assertTrue(item.titleFa.isNotBlank())
            assertTrue(item.bodyFa.isNotBlank())
            assertTrue(item.sources.isNotEmpty())
            item.sources.forEach { s ->
                assertTrue(s.url.startsWith("https://"))
                assertFalse(s.url.contains("example.com"))
            }
            assertTrue(item.medicalReviewStatus.contains("بازبینی پزشکی مستقل انجام نشده"))
        }
        // parse schema asset
        val schema = ctx.assets.open("content/content_schema.json").bufferedReader().readText()
        assertTrue(schema.contains("medicalReviewStatus"))
    }
}
