package com.alau.texteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageRegistryTest {

    @Test
    fun mapsKnownExtensions() {
        assertEquals("source.json", LanguageRegistry.forFileName("settings.json")?.scopeName)
        assertEquals("source.yaml", LanguageRegistry.forFileName("docker-compose.yml")?.scopeName)
        assertEquals("source.shell", LanguageRegistry.forFileName("run.sh")?.scopeName)
        assertEquals("source.powershell", LanguageRegistry.forFileName("deploy.ps1")?.scopeName)
        assertEquals("text.html.markdown", LanguageRegistry.forFileName("README.md")?.scopeName)
    }

    @Test
    fun isCaseInsensitive() {
        assertEquals("source.json", LanguageRegistry.forFileName("CONFIG.JSON")?.scopeName)
    }

    @Test
    fun returnsNullForUnknownOrMissingExtension() {
        assertNull(LanguageRegistry.forFileName("notes.xyz"))
        assertNull(LanguageRegistry.forFileName("Makefile"))
        assertNull(LanguageRegistry.forFileName(null))
        assertNull(LanguageRegistry.forFileName(""))
    }
}
