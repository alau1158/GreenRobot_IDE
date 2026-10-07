package com.alau.texteditor

import java.util.Locale

data class LanguageDefinition(val name: String, val scopeName: String)

object LanguageRegistry {

    private val byExtension: Map<String, LanguageDefinition> = mapOf(
        "json" to LanguageDefinition("json", "source.json"),
        "jsonc" to LanguageDefinition("json", "source.json"),
        "yaml" to LanguageDefinition("yaml", "source.yaml"),
        "yml" to LanguageDefinition("yaml", "source.yaml"),
        "xml" to LanguageDefinition("xml", "text.xml"),
        "plist" to LanguageDefinition("xml", "text.xml"),
        "svg" to LanguageDefinition("xml", "text.xml"),
        "html" to LanguageDefinition("html", "text.html.basic"),
        "htm" to LanguageDefinition("html", "text.html.basic"),
        "ini" to LanguageDefinition("ini", "source.ini"),
        "cfg" to LanguageDefinition("ini", "source.ini"),
        "conf" to LanguageDefinition("ini", "source.ini"),
        "properties" to LanguageDefinition("ini", "source.ini"),
        "env" to LanguageDefinition("ini", "source.ini"),
        "toml" to LanguageDefinition("toml", "source.toml"),
        "sh" to LanguageDefinition("shell", "source.shell"),
        "bash" to LanguageDefinition("shell", "source.shell"),
        "zsh" to LanguageDefinition("shell", "source.shell"),
        "py" to LanguageDefinition("python", "source.python"),
        "js" to LanguageDefinition("javascript", "source.js"),
        "mjs" to LanguageDefinition("javascript", "source.js"),
        "cjs" to LanguageDefinition("javascript", "source.js"),
        "ts" to LanguageDefinition("typescript", "source.ts"),
        "md" to LanguageDefinition("markdown", "text.html.markdown"),
        "markdown" to LanguageDefinition("markdown", "text.html.markdown"),
        "sql" to LanguageDefinition("sql", "source.sql"),
        "ps1" to LanguageDefinition("powershell", "source.powershell"),
        "psm1" to LanguageDefinition("powershell", "source.powershell"),
        "psd1" to LanguageDefinition("powershell", "source.powershell")
    )

    fun forFileName(fileName: String?): LanguageDefinition? {
        if (fileName.isNullOrEmpty()) return null
        val extension = fileName.substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
            .takeIf { it.isNotEmpty() && it != fileName }
            ?: return null
        return byExtension[extension]
    }
}
