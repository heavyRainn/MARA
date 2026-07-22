package com.care.voice.brain.memory.extract

import com.care.voice.brain.memory.fact.MemoryOperation
import com.care.voice.brain.memory.fact.MemorySubject
import com.care.voice.brain.memory.fact.MemoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MemoryCandidateParserTest {

    private val now = Instant.parse("2026-07-22T10:00:00Z")

    @Test
    fun parsesIdentityAdd() {
        val json = """
            [{"operation":"ADD","subjectType":"USER","type":"IDENTITY","key":"name","value":"Анна","confidence":0.95,"sensitivity":"NORMAL","requiresConfirmation":false,"reason":"explicit"}]
        """.trimIndent()
        val result = MemoryCandidateParser.parse(json, now)
        assertEquals(1, result.size)
        assertEquals(MemoryType.IDENTITY, result[0].type)
        assertEquals("Анна", result[0].value)
    }

    @Test
    fun rejectsInvalidEnum() {
        val json = """[{"operation":"ADD","subjectType":"USER","type":"NOT_A_TYPE","key":"x","value":"y","confidence":0.5,"sensitivity":"NORMAL","requiresConfirmation":false}]"""
        assertTrue(MemoryCandidateParser.parse(json, now).isEmpty())
    }

    @Test
    fun parsesNoop() {
        val json = """[{"operation":"NOOP","subjectType":"USER","type":"ASSISTANT_NOTE","key":"noop","confidence":0.0,"sensitivity":"NORMAL","requiresConfirmation":false,"reason":"none"}]"""
        val result = MemoryCandidateParser.parse(json, now)
        assertEquals(MemoryOperation.NOOP, result.single().operation)
    }

    @Test
    fun parsesRelatedPerson() {
        val json = """[{"operation":"ADD","subjectType":"RELATED_PERSON","relation":"daughter","subjectName":"Аня","type":"PREFERENCE","key":"genre","value":"детективы","confidence":0.8,"sensitivity":"NORMAL","requiresConfirmation":false,"reason":"explicit"}]"""
        val subject = MemoryCandidateParser.parse(json, now).single().subject
        assertTrue(subject is MemorySubject.RelatedPerson)
    }
}
