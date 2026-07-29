package com.care.voice.ui.feature.photo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoCommandMatcherTest {

    @Test
    fun matchesPhotoCommands() {
        assertTrue(PhotoCommandMatcher.matches("сфотографируй"))
        assertTrue(PhotoCommandMatcher.matches("Пожалуйста, сделай фото"))
    }

    @Test
    fun ignoresUnrelatedText() {
        assertFalse(PhotoCommandMatcher.matches("привет"))
        assertFalse(PhotoCommandMatcher.matches("напомни завтра"))
    }
}
