package org.http4k.multipart.internal

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.present
import org.http4k.multipart.internal.MultipartFormBuilder
import org.http4k.multipart.internal.ParseError
import org.http4k.multipart.internal.StreamingMultipartFormParts
import org.http4k.multipart.internal.part.StreamingPart
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test
import java.util.*

class StreamingMultipartFormSadTests {

    @Test
    fun failsWhenNoBoundaryInStream() {
        val boundary = "---1234"
        var form = getMultipartFormParts(boundary, "No boundary anywhere".toByteArray())

        assertParseErrorWrapsTokenNotFound(form, "Boundary not found <<-----1234>>")

        form = getMultipartFormParts(boundary, "No boundary anywhere".toByteArray())

        try {
            form.next()
            fail("Should have thrown ParseError")
        } catch (e: ParseError) {
            assertThat(e.cause!!, has(Throwable::message, present(equalTo("Boundary not found <<-----1234>>"))))
        }

    }

    @Test
    fun failsWhenGettingNextPastEndOfParts() {
        val boundary = "-----1234"
        val form = getMultipartFormParts(boundary, MultipartFormBuilder(boundary)
            .file("aFile", "file.name", "application/octet-stream", "File contents here".byteInputStream())
            .file("anotherFile", "your.name", "application/octet-stream", "Different file contents here".byteInputStream()).build())

        form.next() // aFile
        form.next() // anotherFile
        try {
            form.next() // no such element
            fail("Should have thrown NoSuchElementException")
        } catch (e: NoSuchElementException) {
            // pass
        }

    }

    @Test
    fun failsWhenGettingNextPastEndOfPartsAfterHasNext() {
        val boundary = "-----1234"
        val form = getMultipartFormParts(boundary, MultipartFormBuilder(boundary)
            .file("aFile", "file.name", "application/octet-stream", "File contents here".byteInputStream())
            .file("anotherFile", "your.name", "application/octet-stream", "Different file contents here".byteInputStream()).build())

        form.next() // aFile
        form.next() // anotherFile
        assertThat(form.hasNext(), equalTo(false))
        try {
            form.next() // no such element
            fail("Should have thrown NoSuchElementException")
        } catch (e: NoSuchElementException) {
            // pass
        }

    }

    @Test
    @Ignore("this is not a valid test case according to the RFC - we should blow up..")
    fun partHasNoHeaders() {
        val boundary = "-----2345"
        val form = getMultipartFormParts(boundary, MultipartFormBuilder(boundary)
            .field("multi", "value0")
            .part("" + CR_LF + "value with no headers")
            .field("multi", "value2")
            .build())

        form.next()
        val StreamingPart = form.next()
        assertThat(StreamingPart.fieldName, absent())
        assertThat(StreamingPart.contentsAsString, equalTo("value with no headers"))
        assertThat(StreamingPart.headers.size, equalTo(0))
        assertThat(StreamingPart.isFormField, equalTo(true))
        assertThat(StreamingPart.fileName, absent())
        form.next()
    }

    @Test
    fun overwritesPartHeaderIfHeaderIsRepeated() {
        val boundary = "-----2345"
        val form = getMultipartFormParts(boundary, MultipartFormBuilder(boundary)
            .part("contents of StreamingPart",
                "Content-Disposition" to listOf("form-data" to null, "bit" to "first", "name" to "first-name"),
                "Content-Disposition" to listOf("form-data" to null, "bot" to "second", "name" to "second-name"))
            .build())

        val StreamingPart = form.next()
        assertThat(StreamingPart.fieldName, equalTo("second-name"))
        assertThat(StreamingPart.headers["Content-Disposition"],
            equalTo("form-data; bot=\"second\"; name=\"second-name\""))
    }

    @Test
    fun failsIfFoundBoundaryButNoFieldSeparator() {
        val boundary = "---2345"

        val form = getMultipartFormParts(boundary, ("-----2345" + // no CR_LF

            "Content-Disposition: form-data; name=\"name\"" + CR_LF +
            "" + CR_LF +
            "value" + CR_LF +
            "-----2345--" + CR_LF).toByteArray())

        assertParseErrorWrapsTokenNotFound(form, "Boundary must be followed by field separator, but didn't find it")
    }

    @Test
    fun failsIfHeaderMissingFieldSeparator() {
        val boundary = "---2345"

        assertParseError(getMultipartFormParts(boundary, ("-----2345" + CR_LF +
            "Content-Disposition: form-data; name=\"name\"" + // no CR_LF

            "" + CR_LF +
            "value" + CR_LF +
            "-----2345--" + CR_LF).toByteArray()), "Header didn't include a colon <<value>>")


        assertParseError(getMultipartFormParts(boundary, ("-----2345" + CR_LF +
            "Content-Disposition: form-data; name=\"name\"" + CR_LF +
            // no CR_LF
            "value" + CR_LF +
            "-----2345--" + CR_LF).toByteArray()), "Header didn't include a colon <<value>>")
    }

    @Test
    fun failsIfContentsMissingFieldSeparator() {
        val boundary = "---2345"

        val form = getMultipartFormParts(boundary, ("-----2345" + CR_LF +
            "Content-Disposition: form-data; name=\"name\"" + CR_LF +
            "" + CR_LF +
            "value" + // no CR_LF

            "-----2345--" + CR_LF).toByteArray())

        form.next()
        // StreamingPart's content stream hasn't been closed
        assertParseErrorWrapsTokenNotFound(form, "Boundary must be proceeded by field separator, but didn't find it")
    }

    @Test
    fun failsIfContentsMissingFieldSeparatorAndHasReadToEndOfContent() {
        val boundary = "---2345"

        val form = getMultipartFormParts(boundary, ("-----2345" + CR_LF +
            "Content-Disposition: form-data; name=\"name\"" + CR_LF +
            "" + CR_LF +
            "value" + // no CR_LF

            "-----2345--" + CR_LF).toByteArray())

        val StreamingPart = form.next()
        StreamingPart.contentsAsString
        assertParseErrorWrapsTokenNotFound(form, "Boundary must be proceeded by field separator, but didn't find it")
    }

    @Test
    fun failsIfClosingBoundaryIsMissingFieldSeparator() {
        val boundary = "---2345"

        val form = getMultipartFormParts(boundary, ("-----2345" + CR_LF +
            "Content-Disposition: form-data; name=\"name\"" + CR_LF +
            "" + CR_LF +
            "value" + CR_LF +
            "-----2345--").toByteArray()) // no CR_LF

        form.next()
        assertParseErrorWrapsTokenNotFound(form, "Stream terminator must be followed by field separator, but didn't find it")
    }

    @Test
    fun failsIfClosingBoundaryIsMissing() {
        val boundary = "---2345"

        val form = getMultipartFormParts(boundary, ("-----2345" + CR_LF +
            "Content-Disposition: form-data; name=\"name\"" + CR_LF +
            "" + CR_LF +
            "value" + CR_LF +
            "-----2345" + CR_LF).toByteArray())

        form.next()
        assertParseErrorWrapsTokenNotFound(form, "Reached end of stream before finding Token <<\r\n>>. Last 2 bytes read were <<>>")
    }

    @Test
    fun failsIfHeadingTooLong() {
        val boundary = "---2345"

        val chars = CharArray(StreamingMultipartFormParts.HEADER_SIZE_MAX)
        chars.fill('x')
        val form = getMultipartFormParts(boundary, MultipartFormBuilder(boundary)
            .file("aFile", String(chars), "application/octet-stream", "File contents here".byteInputStream()).build())

        assertParseErrorWrapsTokenNotFound(form, "Didn't find end of Token <<\r\n>> within 10240 bytes")
    }

    @Test
    fun failsIfTooManyHeadings() {
        val boundary = "---2345"

        val chars = CharArray(1024)
        chars.fill('x')
        val form = getMultipartFormParts(boundary, MultipartFormBuilder(boundary)
            .part("some contents",
                "Content-Disposition" to listOf("form-data" to null, "name" to "fieldName", "filename" to "filename"),
                "Content-Type" to listOf("text/plain" to null),
                "extra-1" to listOf(String(chars) to null),
                "extra-2" to listOf(String(chars) to null),
                "extra-3" to listOf(String(chars) to null),
                "extra-4" to listOf(String(chars) to null),
                "extra-5" to listOf(String(chars) to null),
                "extra-6" to listOf(String(chars) to null),
                "extra-7" to listOf(String(chars) to null),
                "extra-8" to listOf(String(chars) to null),
                "extra-9" to listOf(String(chars) to null),
                "extra-10" to listOf(String(chars, 0, 816) to null) // header section exactly 10240 bytes big!
            ).build())

        assertParseErrorWrapsTokenNotFound(form, "Didn't find end of Header section within 10240 bytes")
    }

    private fun assertParseErrorWrapsTokenNotFound(form: Iterator<StreamingPart>, errorMessage: String) {
        try {
            form.hasNext()
        } catch (e: ParseError) {
            assertThat(e.cause!!, has(Throwable::message, present(equalTo(errorMessage))))
        }
    }

    private fun assertParseError(form: Iterator<StreamingPart>, errorMessage: String) {
        try {
            form.hasNext() // will hit missing \r\n
            fail("Should have thrown a parse error")
        } catch (e: ParseError) {
            assertThat(e.message, equalTo(errorMessage))
        }
    }
}