package com.alau.texteditor

import android.content.Context
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import org.eclipse.tm4e.core.registry.IThemeSource

object TextMateBootstrap {

    const val DARK_THEME = "darcula"
    const val LIGHT_THEME = "quietlight"
    private const val THEMES = "textmate/themes/"

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        try {
            val assets = context.applicationContext.assets
            FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(assets))

            val registry = ThemeRegistry.getInstance()
            loadTheme(registry, "$THEMES$DARK_THEME.json", DARK_THEME, dark = true)
            loadTheme(registry, "$THEMES$LIGHT_THEME.json", LIGHT_THEME, dark = false)
            registry.setTheme(DARK_THEME)

            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            initialized = true
        }
    }

    private fun loadTheme(registry: ThemeRegistry, path: String, name: String, dark: Boolean) {
        val stream = FileProviderRegistry.getInstance().tryGetInputStream(path) ?: return
        val model = ThemeModel(IThemeSource.fromInputStream(stream, path, null), name)
        model.setDark(dark)
        registry.loadTheme(model)
    }

    fun setTheme(isDark: Boolean) {
        try {
            ThemeRegistry.getInstance().setTheme(if (isDark) DARK_THEME else LIGHT_THEME)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun applyColorScheme(editor: CodeEditor) {
        editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
    }

    fun languageFor(definition: LanguageDefinition): TextMateLanguage? =
        try {
            TextMateLanguage.create(definition.scopeName, true)
        } catch (e: Exception) {
            null
        }
}
