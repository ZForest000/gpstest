package com.example.gpstest.data.validator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XtraDataValidatorTest {
    private fun makeValidator(
        minSizeBytes: Int = 1024,
        maxSizeBytes: Int = 2 * 1024 * 1024,
        strictMode: Boolean = true,
    ): XtraDataValidator = XtraDataValidator(
        minSizeBytes = minSizeBytes,
        maxSizeBytes = maxSizeBytes,
        strictMode = strictMode,
    )

    private fun makeValidData(size: Int = 2048): ByteArray = ByteArray(size) { (it % 256).toByte() }

    // --- empty data ---

    @Test
    fun `validate rejects empty data with EMPTY_DATA error`() {
        val validator = makeValidator()
        val result = validator.validate(ByteArray(0))
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.EMPTY_DATA, result.errorType)
        assertTrue(result.details.contains("空"))
    }

    // --- size bounds ---

    @Test
    fun `validate rejects data smaller than minSizeBytes`() {
        val validator = makeValidator(minSizeBytes = 1024)
        val result = validator.validate(ByteArray(512))
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.TOO_SMALL, result.errorType)
    }

    @Test
    fun `validate accepts data exactly at minSizeBytes`() {
        val validator = makeValidator(minSizeBytes = 1024, strictMode = false)
        val result = validator.validate(makeValidData(1024))
        assertTrue(result.isValid)
    }

    @Test
    fun `validate rejects data larger than maxSizeBytes`() {
        val validator = makeValidator(maxSizeBytes = 4096)
        val result = validator.validate(ByteArray(5000))
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.TOO_LARGE, result.errorType)
    }

    @Test
    fun `validate accepts data exactly at maxSizeBytes`() {
        val validator = makeValidator(maxSizeBytes = 4096, strictMode = false)
        val result = validator.validate(makeValidData(4096))
        assertTrue(result.isValid)
    }

    // --- HTML signature detection ---

    @Test
    fun `validate detects HTML lowercase error page`() {
        val validator = makeValidator()
        val htmlData = "<html><body>404</body></html>".toByteArray() + ByteArray(2000)
        val result = validator.validate(htmlData)
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.ERROR_PAGE_DETECTED, result.errorType)
        assertTrue(result.details.contains("HTML"))
    }

    @Test
    fun `validate detects HTML uppercase error page`() {
        val validator = makeValidator()
        val htmlData = "<HTML><BODY>ERROR</BODY></HTML>".toByteArray() + ByteArray(2000)
        val result = validator.validate(htmlData)
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.ERROR_PAGE_DETECTED, result.errorType)
    }

    @Test
    fun `validate detects DOCTYPE error page`() {
        val validator = makeValidator()
        val htmlData = "<!DOCTYPE html><html></html>".toByteArray() + ByteArray(2000)
        val result = validator.validate(htmlData)
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.ERROR_PAGE_DETECTED, result.errorType)
    }

    // --- JSON signature detection ---

    @Test
    fun `validate detects JSON error response with error key`() {
        val validator = makeValidator()
        val jsonData = "{\"error\":\"not found\"}".toByteArray() + ByteArray(2000)
        val result = validator.validate(jsonData)
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.ERROR_PAGE_DETECTED, result.errorType)
        assertTrue(result.details.contains("JSON"))
    }

    @Test
    fun `validate detects JSON error response with message key`() {
        val validator = makeValidator()
        val jsonData = "{\"message\":\"forbidden\"}".toByteArray() + ByteArray(2000)
        val result = validator.validate(jsonData)
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.ERROR_PAGE_DETECTED, result.errorType)
    }

    @Test
    fun `validate detects JSON error response with code key`() {
        val validator = makeValidator()
        val jsonData = "{\"code\":403}".toByteArray() + ByteArray(2000)
        val result = validator.validate(jsonData)
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.ERROR_PAGE_DETECTED, result.errorType)
    }

    // --- printable char ratio ---

    @Test
    fun `validate rejects data with high printable char ratio above 2KB`() {
        val validator = makeValidator()
        val textData = ByteArray(3000) { 'A'.code.toByte() }
        val result = validator.validate(textData)
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.INVALID_FORMAT, result.errorType)
        assertTrue(result.details.contains("文本"))
    }

    @Test
    fun `validate accepts high printable ratio data below 2KB threshold`() {
        val validator = makeValidator(strictMode = false)
        val textData = ByteArray(1500) { 'A'.code.toByte() }
        val result = validator.validate(textData)
        assertTrue(result.isValid)
    }

    // --- valid data passes ---

    @Test
    fun `validate accepts valid binary data`() {
        val validator = makeValidator(strictMode = false)
        val binaryData = ByteArray(2048) { i -> ((i * 37) % 256).toByte() }
        val result = validator.validate(binaryData)
        assertTrue(result.isValid)
        assertNull(result.errorType)
    }

    @Test
    fun `validation result has empty details when valid`() {
        val validator = makeValidator(strictMode = false)
        val result = validator.validate(makeValidData(2048))
        assertTrue(result.isValid)
        assertEquals("", result.details)
    }

    // --- MIME type validation (strictMode) ---

    @Test
    fun `validate accepts valid MIME type application slash octet-stream in strict mode`() {
        val validator = makeValidator(strictMode = true)
        val result = validator.validate(makeValidData(), mimeType = "application/octet-stream")
        assertTrue(result.isValid)
    }

    @Test
    fun `validate accepts valid MIME type application slash vnd qualcomm xtra in strict mode`() {
        val validator = makeValidator(strictMode = true)
        val result = validator.validate(makeValidData(), mimeType = "application/vnd.qualcomm.xtra")
        assertTrue(result.isValid)
    }

    @Test
    fun `validate rejects text slash html MIME type in strict mode`() {
        val validator = makeValidator(strictMode = true)
        val result = validator.validate(makeValidData(), mimeType = "text/html")
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.INVALID_MIME_TYPE, result.errorType)
    }

    @Test
    fun `validate rejects application slash json MIME type in strict mode`() {
        val validator = makeValidator(strictMode = true)
        val result = validator.validate(makeValidData(), mimeType = "application/json")
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.INVALID_MIME_TYPE, result.errorType)
    }

    @Test
    fun `validate rejects application slash html MIME type in strict mode`() {
        val validator = makeValidator(strictMode = true)
        val result = validator.validate(makeValidData(), mimeType = "application/html")
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.INVALID_MIME_TYPE, result.errorType)
    }

    @Test
    fun `validate skips MIME check when mimeType is null`() {
        val validator = makeValidator(strictMode = true)
        val result = validator.validate(makeValidData(), mimeType = null)
        assertTrue(result.isValid)
    }

    @Test
    fun `validate skips MIME check when strictMode is false`() {
        val validator = makeValidator(strictMode = false)
        val result = validator.validate(makeValidData(), mimeType = "text/html")
        assertTrue(result.isValid)
    }

    @Test
    fun `MIME type comparison is case insensitive`() {
        val validator = makeValidator(strictMode = true)
        val result = validator.validate(makeValidData(), mimeType = "Application/OCTET-Stream")
        assertTrue(result.isValid)
    }

    @Test
    fun `MIME type with whitespace is trimmed`() {
        val validator = makeValidator(strictMode = true)
        val result = validator.validate(makeValidData(), mimeType = "  application/octet-stream  ")
        assertTrue(result.isValid)
    }

    // --- getSizeStatistics ---

    @Test
    fun `getSizeStatistics includes size in KB`() {
        val data = ByteArray(2048)
        val stats = XtraDataValidator().getSizeStatistics(data)
        assertTrue(stats.contains("2.00 KB"))
    }

    @Test
    fun `getSizeStatistics includes first byte hex`() {
        val data = ByteArray(2048)
        data[0] = 0xAB.toByte()
        val stats = XtraDataValidator().getSizeStatistics(data)
        assertTrue(stats.contains("0xAB"))
    }

    @Test
    fun `getSizeStatistics includes magic bytes when data has 4 or more bytes`() {
        val data = ByteArray(2048)
        data[0] = 0x01
        data[1] = 0x02
        data[2] = 0x03
        data[3] = 0x04
        val stats = XtraDataValidator().getSizeStatistics(data)
        assertTrue(stats.contains("Magic:"))
        assertTrue(stats.contains("01 02 03 04"))
    }

    @Test
    fun `getSizeStatistics omits magic bytes when data has fewer than 4 bytes`() {
        val data = byteArrayOf(0x01.toByte(), 0x02.toByte())
        val stats = XtraDataValidator().getSizeStatistics(data)
        assertFalse(stats.contains("Magic:"))
    }
}
