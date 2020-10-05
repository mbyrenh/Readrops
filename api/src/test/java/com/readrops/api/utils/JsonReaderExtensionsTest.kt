package com.readrops.api.utils

import com.squareup.moshi.JsonReader
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import okio.Buffer
import org.junit.Test

class JsonReaderExtensionsTest {

    @Test
    fun nextNullableStringNullCaseTest() {
        val reader = JsonReader.of(Buffer().readFrom("""
            {
                "field": null
            }
        """.trimIndent().byteInputStream()))

        reader.beginObject()
        reader.nextName()

        assertNull(reader.nextNullableString())
        reader.endObject()
    }

    @Test
    fun nextNullableStringEmptyCaseTest() {
        val reader = JsonReader.of(Buffer().readFrom("""
            {
                "field": ""
            }
        """.trimIndent().byteInputStream()))

        reader.beginObject()
        reader.nextName()

        assertNull(reader.nextNullableString())
        reader.endObject()
    }

    @Test
    fun nextNullableValueNormalCaseTest() {
        val reader = JsonReader.of(Buffer().readFrom("""
            {
                "field": "value"
            }
        """.trimIndent().byteInputStream()))

        reader.beginObject()
        reader.nextName()

        assertEquals(reader.nextNullableString(), "value")
        reader.endObject()
    }

    @Test
    fun nextNonEmptyStringTest() {
        val reader = JsonReader.of(Buffer().readFrom("""
            {
                "field": "value"
            }
        """.trimIndent().byteInputStream()))

        reader.beginObject()
        reader.nextName()

        assertEquals(reader.nextNullableString(), "value")
        reader.endObject()
    }

    @Test(expected = ParseException::class)
    fun nextNonEmptyStringEmptyCaseTest() {
        val reader = JsonReader.of(Buffer().readFrom("""
            {
                "field": ""
            }
        """.trimIndent().byteInputStream()))

        reader.beginObject()
        reader.nextName()

        reader.nextNonEmptyString()
    }

}