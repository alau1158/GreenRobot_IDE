# Syntax Highlighting & IDE Polish — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the plain `EditText` editor with sora-editor + TextMate grammars so config files get syntax highlighting, line numbers, current-line highlight, bracket matching/auto-close, and auto-indent.

**Architecture:** Keep the existing app shell (SAF open/save, recents, dirty guard, prefs). Swap the editor widget to `io.github.rosemoe.sora.widget.CodeEditor`. A pure `LanguageRegistry` maps file extension → TextMate scope. A `TextMateBootstrap` loads bundled grammar/theme assets once. Sora owns undo/redo, search, brackets, and indentation.

**Tech Stack:** Kotlin, Android Views, sora-editor 0.24.6 (`editor` + `language-textmate`), TextMate grammars, core library desugaring, JUnit4.

## Global Constraints

- Application id / namespace: `com.alau.texteditor`.
- `minSdk = 26`, `targetSdk = 34`, `compileSdk = 34` (raise to 35 only if dependency resolution requires it).
- Java source/target compatibility `JavaVersion.VERSION_17`; JDK 17 for Gradle.
- sora-editor BOM `io.github.rosemoe:editor-bom:0.24.6`; modules `io.github.rosemoe:editor`, `io.github.rosemoe:language-textmate`.
- Core library desugaring: `com.android.tools:desugar_jdk_libs:2.1.5` and `isCoreLibraryDesugaringEnabled = true`.
- Kotlin Gradle plugin `2.2.10` (must read Sora's Kotlin 2.1.x stdlib metadata).
- Files are read/written as UTF-8 (BOM stripped). Unknown extensions open with no highlighting.
- Build commands (run from `/home/alau/text-editor`):
  `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/alau/android-sdk /opt/gradle-8.9/bin/gradle <tasks> --no-daemon --console=plain`
- No source comments unless already present.

---

## Task 1: Build configuration for sora-editor

**Files:**
- Modify: `build.gradle.kts` (root)
- Modify: `app/build.gradle.kts`
- Create: `.gitignore`
- Modify: `gradle.properties` (only if memory needs tuning)

**Interfaces:**
- Consumes: nothing.
- Produces: sora-editor classes resolvable on the app classpath; `:app:assembleDebug` still builds.

- [ ] **Step 1: Initialize git and add .gitignore**

```bash
cd /home/alau/text-editor && git init
```

Create `.gitignore`:

```gitignore
.gradle/
build/
local.properties
*.iml
.idea/
dist/
release.keystore
```

- [ ] **Step 2: Bump the Kotlin plugin**

`build.gradle.kts` (root):

```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
}
```

- [ ] **Step 3: Add Sora dependencies and desugaring**

In `app/build.gradle.kts`, inside `android { compileOptions { ... } }` add `isCoreLibraryDesugaringEnabled = true`:

```kotlin
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
```

Replace the `dependencies { ... }` block with:

```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity-ktx:1.8.2")

    implementation(platform("io.github.rosemoe:editor-bom:0.24.6"))
    implementation("io.github.rosemoe:editor")
    implementation("io.github.rosemoe:language-textmate")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 4: Verify the configuration resolves and the app builds**

Run: `:app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. If it fails with "compileSdk 35 required by dependency", set `compileSdk = 35` and add `buildToolsVersion = "35.0.0"`, then re-run. If it fails with a Kotlin metadata version error, re-check the Kotlin plugin version.

- [ ] **Step 5: Commit**

```bash
cd /home/alau/text-editor
git add .gitignore build.gradle.kts app/build.gradle.kts gradle.properties settings.gradle.kts
git commit -m "build: add sora-editor and enable desugaring"
```

---

## Task 2: LanguageRegistry (pure logic, TDD)

**Files:**
- Create: `app/src/main/java/com/alau/texteditor/LanguageRegistry.kt`
- Test: `app/src/test/java/com/alau/texteditor/LanguageRegistryTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `data class LanguageDefinition(val name: String, val scopeName: String)`; `object LanguageRegistry { fun forFileName(fileName: String?): LanguageDefinition? }`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/alau/texteditor/LanguageRegistryTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `:app:testDebugUnitTest --tests "com.alau.texteditor.LanguageRegistryTest"`
Expected: FAIL — unresolved reference `LanguageRegistry`.

- [ ] **Step 3: Implement LanguageRegistry**

`app/src/main/java/com/alau/texteditor/LanguageRegistry.kt`:

```kotlin
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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `:app:testDebugUnitTest --tests "com.alau.texteditor.LanguageRegistryTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/alau/texteditor/LanguageRegistry.kt app/src/test/java/com/alau/texteditor/LanguageRegistryTest.kt
git commit -m "feat: add LanguageRegistry mapping extensions to TextMate scopes"
```

---

## Task 3: Bundle TextMate grammar and theme assets

**Files:**
- Create: `tools/fetch-grammars.sh`
- Create: `app/src/main/assets/textmate/languages.json`
- Create: `app/src/main/assets/textmate/**` (grammars, configs, themes)

**Interfaces:**
- Consumes: nothing.
- Produces: assets loadable by `GrammarRegistry.loadGrammars("textmate/languages.json")` and `ThemeRegistry`.

- [ ] **Step 1: Write the fetch script**

`tools/fetch-grammars.sh` downloads each grammar/config/theme into `app/src/main/assets/textmate/`. Missing downloads are skipped (that language then opens with no highlighting, which is acceptable). Exact layout mirrors sora's sample (`<lang>/syntaxes/*.tmLanguage.json`, `<lang>/language-configuration.json`).

```bash
#!/usr/bin/env bash
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/assets/textmate"
SORA="https://raw.githubusercontent.com/Rosemoe/sora-editor/main/app/src/main/assets/textmate"
VSCODE="https://raw.githubusercontent.com/microsoft/vscode/main/extensions"

fetch() { # url dest
  mkdir -p "$(dirname "$2")"
  if curl -fsSL -m 60 "$1" -o "$2"; then echo "ok   $2"; else echo "SKIP $1"; rm -f "$2"; fi
}

# From sora's proven sample assets
fetch "$SORA/html/syntaxes/html.tmLanguage.json"       "$DEST/html/syntaxes/html.tmLanguage.json"
fetch "$SORA/html/language-configuration.json"          "$DEST/html/language-configuration.json"
fetch "$SORA/xml/syntaxes/xml.tmLanguage.json"          "$DEST/xml/syntaxes/xml.tmLanguage.json"
fetch "$SORA/xml/language-configuration.json"           "$DEST/xml/language-configuration.json"
fetch "$SORA/javascript/syntaxes/JavaScript.tmLanguage.json" "$DEST/javascript/syntaxes/JavaScript.tmLanguage.json"
fetch "$SORA/javascript/language-configuration.json"    "$DEST/javascript/language-configuration.json"
fetch "$SORA/markdown/syntaxes/markdown.tmLanguage.json" "$DEST/markdown/syntaxes/markdown.tmLanguage.json"
fetch "$SORA/markdown/language-configuration.json"      "$DEST/markdown/language-configuration.json"
fetch "$SORA/python/syntaxes/python.tmLanguage.json"    "$DEST/python/syntaxes/python.tmLanguage.json"
fetch "$SORA/python/language-configuration.json"        "$DEST/python/language-configuration.json"

# Themes
fetch "$SORA/darcula.json"        "$DEST/themes/darcula.json"
fetch "$SORA/quietlight.json"     "$DEST/themes/quietlight.json"

# From VS Code built-in extensions
fetch "$VSCODE/json/syntaxes/JSON.tmLanguage.json"       "$DEST/json/syntaxes/json.tmLanguage.json"
fetch "$VSCODE/json/language-configuration.json"         "$DEST/json/language-configuration.json"
fetch "$VSCODE/yaml/syntaxes/yaml.tmLanguage.json"       "$DEST/yaml/syntaxes/yaml.tmLanguage.json"
fetch "$VSCODE/yaml/language-configuration.json"         "$DEST/yaml/language-configuration.json"
fetch "$VSCODE/ini/syntaxes/ini.tmLanguage.json"         "$DEST/ini/syntaxes/ini.tmLanguage.json"
fetch "$VSCODE/ini/language-configuration.json"          "$DEST/ini/language-configuration.json"
fetch "$VSCODE/shellscript/syntaxes/shell-unix-bash.tmLanguage.json" "$DEST/shell/syntaxes/shell.tmLanguage.json"
fetch "$VSCODE/shellscript/language-configuration.json"  "$DEST/shell/language-configuration.json"
fetch "$VSCODE/typescript-basics/syntaxes/TypeScript.tmLanguage.json" "$DEST/typescript/syntaxes/TypeScript.tmLanguage.json"
fetch "$VSCODE/typescript-basics/language-configuration.json" "$DEST/typescript/language-configuration.json"
```

- [ ] **Step 2: Fetch TOML, SQL, and PowerShell grammars (best-effort)**

Append to the script; verify each URL resolves at execution time and drop any that 404 (then remove the matching entry from `languages.json` in Step 4).

```bash
# TOML (taplo / VS Code TOML extension)
fetch "https://raw.githubusercontent.com/tamasfe/taplo/master/editors/vscode/syntaxes/taplo.tmLanguage.json" "$DEST/toml/syntaxes/toml.tmLanguage.json"
# SQL (TM4E language pack)
fetch "https://raw.githubusercontent.com/eclipse/tm4e/master/org.eclipse.tm4e.language_pack/syntaxes/sql.tmLanguage.json" "$DEST/sql/syntaxes/sql.tmLanguage.json"
# PowerShell (EditorSyntax, PLIST grammar)
fetch "https://raw.githubusercontent.com/PowerShell/EditorSyntax/master/PowerShell.tmLanguage" "$DEST/powershell/syntaxes/PowerShell.tmLanguage"
```

- [ ] **Step 3: Run the fetch script and create minimal configs for missing ones**

Run: `bash tools/fetch-grammars.sh`

For any language fetched without a `language-configuration.json` (TOML/SQL/PowerShell), create a minimal config, e.g. `app/src/main/assets/textmate/toml/language-configuration.json`:

```json
{
  "comments": { "lineComment": "#" },
  "brackets": [["[", "]"], ["{", "}"]],
  "autoClosingPairs": [
    { "open": "[", "close": "]" },
    { "open": "{", "close": "}" },
    { "open": "\"", "close": "\"" }
  ],
  "surroundingPairs": [["[", "]"], ["{", "}"], ["\"", "\""]]
}
```

(SQL uses `--` line comments and `'` quoting; PowerShell uses `#` and `'`/`"`.)

- [ ] **Step 4: Write languages.json for the successfully fetched grammars**

`app/src/main/assets/textmate/languages.json` — include only entries whose grammar file exists. Base content (trim any language that failed to fetch):

```json
{
  "languages": [
    { "grammar": "textmate/json/syntaxes/json.tmLanguage.json", "name": "json", "scopeName": "source.json", "languageConfiguration": "textmate/json/language-configuration.json" },
    { "grammar": "textmate/yaml/syntaxes/yaml.tmLanguage.json", "name": "yaml", "scopeName": "source.yaml", "languageConfiguration": "textmate/yaml/language-configuration.json" },
    { "grammar": "textmate/xml/syntaxes/xml.tmLanguage.json", "name": "xml", "scopeName": "text.xml", "languageConfiguration": "textmate/xml/language-configuration.json" },
    { "grammar": "textmate/html/syntaxes/html.tmLanguage.json", "name": "html", "scopeName": "text.html.basic", "languageConfiguration": "textmate/html/language-configuration.json", "embeddedLanguages": { "source.js": "javascript" } },
    { "grammar": "textmate/ini/syntaxes/ini.tmLanguage.json", "name": "ini", "scopeName": "source.ini", "languageConfiguration": "textmate/ini/language-configuration.json" },
    { "grammar": "textmate/toml/syntaxes/toml.tmLanguage.json", "name": "toml", "scopeName": "source.toml", "languageConfiguration": "textmate/toml/language-configuration.json" },
    { "grammar": "textmate/shell/syntaxes/shell.tmLanguage.json", "name": "shell", "scopeName": "source.shell", "languageConfiguration": "textmate/shell/language-configuration.json" },
    { "grammar": "textmate/python/syntaxes/python.tmLanguage.json", "name": "python", "scopeName": "source.python", "languageConfiguration": "textmate/python/language-configuration.json" },
    { "grammar": "textmate/javascript/syntaxes/JavaScript.tmLanguage.json", "name": "javascript", "scopeName": "source.js", "languageConfiguration": "textmate/javascript/language-configuration.json" },
    { "grammar": "textmate/typescript/syntaxes/TypeScript.tmLanguage.json", "name": "typescript", "scopeName": "source.ts", "languageConfiguration": "textmate/typescript/language-configuration.json" },
    { "grammar": "textmate/markdown/syntaxes/markdown.tmLanguage.json", "name": "markdown", "scopeName": "text.html.markdown", "languageConfiguration": "textmate/markdown/language-configuration.json" },
    { "grammar": "textmate/sql/syntaxes/sql.tmLanguage.json", "name": "sql", "scopeName": "source.sql", "languageConfiguration": "textmate/sql/language-configuration.json" },
    { "grammar": "textmate/powershell/syntaxes/PowerShell.tmLanguage", "name": "powershell", "scopeName": "source.powershell", "languageConfiguration": "textmate/powershell/language-configuration.json" }
  ]
}
```

- [ ] **Step 5: Verify assets**

Run:
```bash
cd /home/alau/text-editor
python3 -c "import json,os; d=json.load(open('app/src/main/assets/textmate/languages.json')); [print(('MISSING ' if not os.path.exists('app/src/main/assets/'+l['grammar']) else 'ok ') + l['name']) for l in d['languages']]"
```
Expected: every line starts with `ok `.

- [ ] **Step 6: Commit**

```bash
git add tools/fetch-grammars.sh app/src/main/assets
git commit -m "feat: bundle TextMate grammars and themes"
```

---

## Task 4: TextMateBootstrap

**Files:**
- Create: `app/src/main/java/com/alau/texteditor/TextMateBootstrap.kt`

**Interfaces:**
- Consumes: `LanguageDefinition` (Task 2); assets at `textmate/languages.json` and `textmate/themes/{darcula,quietlight}.json` (Task 3).
- Produces:
  - `TextMateBootstrap.init(context: Context)` — idempotent, call once in `Application`/`Activity`.
  - `TextMateBootstrap.setTheme(isDark: Boolean)`
  - `TextMateBootstrap.applyColorScheme(editor: CodeEditor)`
  - `TextMateBootstrap.languageFor(definition: LanguageDefinition): TextMateLanguage?`

- [ ] **Step 1: Implement the bootstrap**

`app/src/main/java/com/alau/texteditor/TextMateBootstrap.kt`:

```kotlin
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
        val assets = context.applicationContext.assets
        FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(assets))

        val registry = ThemeRegistry.getInstance()
        loadTheme(registry, "$THEMES$DARK_THEME.json", DARK_THEME, dark = true)
        loadTheme(registry, "$THEMES$LIGHT_THEME.json", LIGHT_THEME, dark = false)
        registry.setTheme(DARK_THEME)

        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        initialized = true
    }

    private fun loadTheme(registry: ThemeRegistry, path: String, name: String, dark: Boolean) {
        val stream = FileProviderRegistry.getInstance().tryGetInputStream(path) ?: return
        val model = ThemeModel(IThemeSource.fromInputStream(stream, path, null), name)
        model.setDark(dark)
        registry.loadTheme(model)
    }

    fun setTheme(isDark: Boolean) {
        ThemeRegistry.getInstance().setTheme(if (isDark) DARK_THEME else LIGHT_THEME)
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
```

- [ ] **Step 2: Verify it compiles**

Run: `:app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. If a class path is wrong, correct the import to match the resolved AAR (packages confirmed: `io.github.rosemoe.sora.langs.textmate.*`, `io.github.rosemoe.sora.langs.textmate.registry.*`, `org.eclipse.tm4e.core.registry.IThemeSource`).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/alau/texteditor/TextMateBootstrap.kt
git commit -m "feat: add TextMate bootstrap for grammars and themes"
```

---

## Task 5: Swap the editor widget and rewire MainActivity

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/java/com/alau/texteditor/MainActivity.kt`
- Delete: `app/src/main/java/com/alau/texteditor/EditorEditText.kt`
- Delete: `app/src/main/java/com/alau/texteditor/UndoStack.kt`
- Delete: `app/src/main/java/com/alau/texteditor/TextSearch.kt`
- Delete: `app/src/test/java/com/alau/texteditor/UndoStackTest.kt`
- Delete: `app/src/test/java/com/alau/texteditor/TextSearchTest.kt`

**Interfaces:**
- Consumes: `TextMateBootstrap`, `LanguageRegistry`.
- Produces: a working editor using Sora; `binding.editor` is now `CodeEditor`.

- [ ] **Step 1: Replace the editor in the layout**

In `activity_main.xml`, replace the `<com.alau.texteditor.EditorEditText ... />` element with:

```xml
        <io.github.rosemoe.sora.widget.CodeEditor
            android:id="@+id/editor"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:importantForAutofill="no" />
```

- [ ] **Step 2: Delete retired classes and tests**

```bash
cd /home/alau/text-editor
git rm app/src/main/java/com/alau/texteditor/EditorEditText.kt \
       app/src/main/java/com/alau/texteditor/UndoStack.kt \
       app/src/main/java/com/alau/texteditor/TextSearch.kt \
       app/src/test/java/com/alau/texteditor/UndoStackTest.kt \
       app/src/test/java/com/alau/texteditor/TextSearchTest.kt
```

- [ ] **Step 3: Rewire MainActivity**

Apply these changes to `MainActivity.kt`:

1. Imports: remove `android.text.*`, `android.widget.Toast` stays, `android.graphics.Typeface` stays. Add:

```kotlin
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.EditorSearcher
```

2. Remove the fields `undoStack`, `lastState`, `suppressWatcher`, `handler`, `recordRunnable`, `watcher`, and the methods `snapshot`, `resetUndoState`, `recordUndo`, `restore`. Keep `dirty`, `pendingAction`.

3. In `onCreate`, after `setSupportActionBar(binding.toolbar)`:

```kotlin
        TextMateBootstrap.init(this)
        TextMateBootstrap.setTheme(isDarkMode())
        TextMateBootstrap.applyColorScheme(binding.editor)
        binding.editor.typefaceText = Typeface.MONOSPACE
        binding.editor.setTextSize(fontSizeSp)
        binding.editor.setWordwrap(wrapEnabled)
        binding.editor.setLineNumberEnabled(true)
        binding.editor.setHighlightCurrentLine(true)
        binding.editor.subscribeEvent(ContentChangeEvent::class.java) { _, _ -> onContentChanged() }
        binding.editor.subscribeEvent(SelectionChangeEvent::class.java) { event, _ ->
            updateStatus(event.left.line + 1, event.left.column + 1)
        }
```

4. Replace `editorText()` with:

```kotlin
    private fun editorText(): String = binding.editor.text.toString()
```

5. Replace `setEditorText` with:

```kotlin
    private fun setEditorText(text: String) {
        suppressChange = true
        binding.editor.setText(text)
        suppressChange = false
        updateStatus(1, 1)
    }
```

6. Add the field `private var suppressChange = false` and the handler:

```kotlin
    private fun onContentChanged() {
        if (suppressChange) return
        if (!dirty) {
            dirty = true
            updateTitle()
        }
        updateStatus(binding.editor.cursor.left.line + 1, binding.editor.cursor.left.column + 1)
    }
```

7. Replace `undo()` / `redo()`:

```kotlin
    private fun undo() {
        binding.editor.undo()
        updateTitleDirtyFromEditor()
    }

    private fun redo() {
        binding.editor.redo()
        updateTitleDirtyFromEditor()
    }

    private fun updateTitleDirtyFromEditor() {
        if (!binding.editor.canUndo() && !dirty) return
        invalidateOptionsMenu()
    }
```

8. In `onPrepareOptionsMenu`, set enabled state from Sora:

```kotlin
        menu.findItem(R.id.action_undo)?.isEnabled = binding.editor.canUndo()
        menu.findItem(R.id.action_redo)?.isEnabled = binding.editor.canRedo()
```

9. In `openUri`, after `setEditorText(text)`, apply the language:

```kotlin
        val definition = LanguageRegistry.forFileName(currentName)
        binding.editor.setEditorLanguage(
            definition?.let { TextMateBootstrap.languageFor(it) }
        )
```

10. In `applyEditorPrefs`, replace EditText calls with:

```kotlin
    private fun applyEditorPrefs() {
        binding.editor.setWordwrap(wrapEnabled)
        binding.editor.typefaceText = if (monoEnabled) Typeface.MONOSPACE else Typeface.SANS_SERIF
        binding.editor.setTextSize(fontSizeSp)
        prefs.edit()
            .putBoolean(KEY_WRAP, wrapEnabled)
            .putBoolean(KEY_MONO, monoEnabled)
            .putFloat(KEY_FONT, fontSizeSp)
            .apply()
        invalidateOptionsMenu()
    }
```

11. Replace `updateStatus()` signature/body:

```kotlin
    private fun updateStatus(line: Int, column: Int) {
        binding.statusBar.text = getString(R.string.status_format, line, column, binding.editor.text.length())
    }
```

12. Add a dark-mode helper and theme application:

```kotlin
    private fun isDarkMode(): Boolean {
        val mask = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
```

13. In `onDestroy`, release the editor before removing callbacks:

```kotlin
    override fun onDestroy() {
        binding.editor.release()
        super.onDestroy()
    }
```

14. Remove the `resetUndoState()` calls (they no longer exist). In `openUri` and `newDocument`, nothing replaces them.

- [ ] **Step 4: Build and lint**

Run: `:app:assembleDebug :app:lintDebug`
Expected: `BUILD SUCCESSFUL`; lint reports 0 errors. Fix any remaining references to removed members (`editorText` callers, `updateStatus()` no-arg callers) by passing the cursor position or `(1, 1)`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: replace EditText with sora CodeEditor and wire highlighting"
```

---

## Task 6: Rewire find/replace to Sora's searcher

**Files:**
- Modify: `app/src/main/java/com/alau/texteditor/MainActivity.kt` (`showFindDialog`)

**Interfaces:**
- Consumes: `binding.editor.searcher` (`EditorSearcher`).
- Produces: find/replace using Sora (regex-capable, highlights matches).

- [ ] **Step 1: Replace showFindDialog**

```kotlin
    private fun showFindDialog() {
        val dialogBinding = DialogFindBinding.inflate(layoutInflater)
        dialogBinding.findInput.setText(lastQuery)
        dialogBinding.matchCase.isChecked = lastMatchCase

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.find_replace)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.close, null)
            .create()

        val searcher = binding.editor.searcher

        fun runSearch() {
            lastQuery = dialogBinding.findInput.text?.toString() ?: ""
            lastMatchCase = dialogBinding.matchCase.isChecked
            if (lastQuery.isEmpty()) {
                searcher.stopSearch()
                return
            }
            searcher.search(lastQuery, EditorSearcher.SearchOptions(!lastMatchCase, false))
        }

        dialogBinding.btnNext.setOnClickListener {
            runSearch()
            if (!searcher.gotoNext()) toast(getString(R.string.no_matches))
        }
        dialogBinding.btnPrev.setOnClickListener {
            runSearch()
            if (!searcher.gotoPrevious()) toast(getString(R.string.no_matches))
        }
        dialogBinding.btnReplace.setOnClickListener {
            val replacement = dialogBinding.replaceInput.text?.toString() ?: ""
            if (!searcher.isMatchedPositionSelected) {
                runSearch()
            }
            searcher.replaceCurrentMatch(replacement)
            searcher.gotoNext()
        }
        dialogBinding.btnReplaceAll.setOnClickListener {
            runSearch()
            val replacement = dialogBinding.replaceInput.text?.toString() ?: ""
            searcher.replaceAll(replacement)
        }

        dialog.show()
    }
```

- [ ] **Step 2: Build and lint**

Run: `:app:assembleDebug :app:lintDebug`
Expected: `BUILD SUCCESSFUL`, 0 lint errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/alau/texteditor/MainActivity.kt
git commit -m "feat: use sora searcher for find and replace"
```

---

## Task 7: Final build, sign, and package

**Files:**
- Modify: `dist/TextEditor-1.1.apk` (output)
- Modify: `app/build.gradle.kts` (version bump)

**Interfaces:**
- Consumes: everything.
- Produces: installable signed release APK.

- [ ] **Step 1: Bump version**

In `app/build.gradle.kts`, set `versionCode = 2` and `versionName = "1.1"`.

- [ ] **Step 2: Run full verification**

Run: `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`
Expected: `BUILD SUCCESSFUL`; all unit tests pass; lint 0 errors.

- [ ] **Step 3: Sign and verify the release APK**

```bash
cd /home/alau/text-editor
BT=/home/alau/android-sdk/build-tools/34.0.0
mkdir -p dist
"$BT/zipalign" -p -f 4 app/build/outputs/apk/release/app-release-unsigned.apk /tmp/opencode/texteditor-aligned.apk
"$BT/apksigner" sign --ks release.keystore --ks-key-alias texteditor \
  --ks-pass pass:texteditor --key-pass pass:texteditor \
  --out dist/TextEditor-1.1.apk /tmp/opencode/texteditor-aligned.apk
"$BT/apksigner" verify dist/TextEditor-1.1.apk && echo SIGNED
"$BT/aapt" dump badging dist/TextEditor-1.1.apk | grep -E "^package:|sdkVersion|targetSdkVersion"
```

- [ ] **Step 4: Install smoke test (requires the Z Fold 6 connected via USB)**

```bash
/home/alau/android-sdk/platform-tools/adb install -r dist/TextEditor-1.1.apk
```
Then on device: open one `.json`, `.yaml`, `.sh`, `.ps1`, `.py`, and `.md` file and confirm highlighting, line numbers, current-line highlight, bracket auto-close, auto-indent, undo/redo, find/replace, and save round-trip.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts
git commit -m "release: v1.1 with syntax highlighting"
```

---

## Self-Review Notes

- **Spec coverage:** highlighting (Tasks 3–5), line numbers/current-line/brackets/auto-indent (Task 5), LanguageRegistry (Task 2), TextMateBootstrap (Task 4), desugaring/deps (Task 1), find/replace re-backing (Task 6), build/sign (Task 7). Deferred items (folders, tabs, folding, validation) intentionally absent.
- **Type consistency:** `LanguageDefinition(name, scopeName)`, `LanguageRegistry.forFileName(String?): LanguageDefinition?`, `TextMateBootstrap.init/setTheme/applyColorScheme/languageFor`, `updateStatus(line, column)`, `editorText()` all used consistently across tasks.
- **Known risk:** exact grammar URLs for TOML/SQL/PowerShell may differ; Task 3 Step 2 is best-effort with graceful fallback, so the build is never blocked.
