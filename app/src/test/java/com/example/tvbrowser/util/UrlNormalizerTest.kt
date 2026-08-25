package com.example.tvbrowser.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UrlNormalizerTest {

    @Test
    fun prependsHttpsToBareHost() {
        assertEquals("https://service.example.tv", UrlNormalizer.normalize("service.example.tv"))
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertEquals("https://service.example.tv", UrlNormalizer.normalize("  service.example.tv  "))
    }

    @Test
    fun keepsExistingSchemeWithoutDowngrade() {
        assertEquals("https://service.example.tv/watch", UrlNormalizer.normalize("https://service.example.tv/watch"))
        assertEquals("http://cleartext.example", UrlNormalizer.normalize("http://cleartext.example"))
    }

    @Test
    fun rejectsEmptyInput() {
        assertThrows(IllegalArgumentException::class.java) { UrlNormalizer.normalize("") }
        assertThrows(IllegalArgumentException::class.java) { UrlNormalizer.normalize("   ") }
    }

    @Test
    fun rejectsSchemeOnlyInputWithoutHost() {
        assertThrows(IllegalArgumentException::class.java) { UrlNormalizer.normalize("https://") }
        assertThrows(IllegalArgumentException::class.java) { UrlNormalizer.normalize("http://") }
    }
}
