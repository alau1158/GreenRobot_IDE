# Text Editor v2 — Syntax Highlighting & IDE Polish (Design)

Date: 2026-10-07
Builds on: `docs/superpowers/specs/2026-10-07-android-text-editor-design.md`
Target device: Samsung Galaxy Z Fold 6, Android 14 (API 34)

## Purpose

Turn the working plain-text editor into a config-file-friendly code editor by adding
syntax highlighting and basic IDE ergonomics, while keeping the existing open/save flow,
recent files, and unsaved-changes protection.

## Scope (Option A)

In scope:
- Syntax highlighting for many languages.
- Line-number gutter.
- Current-line highlight.
- Bracket matching and auto-close of brackets/quotes.
- Auto-indent.

Explicitly deferred (future C/D): folder/file browser, tabs, session restore, code
folding, validation/pretty-print, find-in-files, completion, LSP.

## Approach

Adopt **sora-editor** (`io.github.rosemoe`, version 0.24.6) as the editor engine and use
its **TextMate** language module for highlighting. This provides all in-scope features
natively and is the foundation for future IDE features, avoiding hand-written highlighters
for ~13 languages.

## Architecture

Replace the custom `EditorEditText` with `io.github.rosemoe.sora.widget.CodeEditor` in
`activity_main.xml`.

`MainActivity` keeps its responsibilities for file I/O and app chrome, and delegates text
editing to Sora:

- `MainActivity` — SAF open/save/save-as, recents, dirty guard, preferences, menu,
  status bar, and wiring of the editor language on file load.
- `LanguageRegistry` (new, pure logic) — maps a file extension to a TextMate language name
  and scope name; unit-testable.
- `TextMateBootstrap` (new) — one-time initialization of the TextMate file provider,
  themes, and grammar registry from bundled assets.
- Retired: `EditorEditText`, `UndoStack` (and `UndoStackTest`), and `TextSearch` (and
  `TextSearchTest`) — Sora owns undo/redo and search. The existing find/replace dialog is
  retained as UI but re-backed by Sora's searcher (which also supports regex and match
  highlighting).

`CodeEditor.release()` MUST be called in `onDestroy`.

## Feature mapping

| Feature | Source |
|---|---|
| Syntax highlighting | `TextMateLanguage.create(scopeName, true)` set via `editor.setEditorLanguage(...)` |
| Color scheme / theme | `TextMateColorScheme.create(ThemeRegistry.getInstance())`; theme selected from bundled JSON |
| Line numbers | Sora built-in |
| Current-line highlight | Sora built-in |
| Bracket matching + auto-close brackets/quotes | Sora built-in symbol input handling |
| Auto-indent | Sora, driven by each language's `language-configuration.json` |
| Undo / Redo | Sora undo manager (`editor.undo()` / `editor.redo()`) |
| Find / Replace | Existing dialog, re-backed by Sora's searcher (regex + match highlighting) |
| Word wrap | Sora `setWordwrap` |
| Font size / monospace | Sora `textSize` / `typefaceText` |
| Open / Save / Save-As / Recents / dirty guard | Existing app code (unchanged) |

## Languages and assets

Bundle TextMate assets under `app/src/main/assets/textmate/` with a `languages.json`
registry (grammar path, name, scopeName, languageConfiguration, embeddedLanguages).

Target languages and extensions:
- json (.json, .jsonc)
- yaml (.yaml, .yml)
- xml (.xml, .plist, .svg)
- html (.html, .htm)
- ini (.ini, .cfg, .conf, .properties, .env)
- toml (.toml)
- shell (.sh, .bash, .zsh)
- python (.py)
- javascript (.js, .mjs, .cjs) and typescript (.ts)
- markdown (.md, .markdown)
- sql (.sql)
- powershell (.ps1, .psm1, .psd1)

Grammar sources: sora-editor's own sample assets where available (html, xml, javascript,
markdown, python) plus VS Code / TM4E language packs for the remainder. Where a grammar is
missing for a listed extension, that file opens with no highlighting rather than failing.

Bundle 1–2 editor themes (dark + light), sourced from sora-editor's sample assets
(darcula/ayu-dark for dark, quietlight for light). The theme follows the app's light/dark
mode.

## Data flow

Open: SAF returns a URI → read UTF-8 (strip BOM) → `editor.setText(text)` → look up the
extension in `LanguageRegistry` → `editor.setEditorLanguage(TextMateLanguage.create(scope, true))`
→ baseline dirty state → add to recents.
Save / Save-As: unchanged; `editor.getText().toString()` is written as UTF-8.
Language is re-applied on every open; if no language matches, the editor uses the default
(no highlight).

## Build changes

- Bump Kotlin plugin to a version that can read Sora's Kotlin 2.1.x stdlib metadata
  (plan: 2.2.10, already cached).
- Add dependencies (BOM `io.github.rosemoe:editor-bom:0.24.6`):
  `io.github.rosemoe:editor`, `io.github.rosemoe:language-textmate`.
- Enable core library desugaring (`com.android.tools:desugar_jdk_libs:2.1.5`,
  `isCoreLibraryDesugaringEnabled = true`) as required by `language-textmate` for API < 33.
- Raise `compileSdk` to 35 if Sora's transitive AndroidX dependencies require it
  (platform android-35 and build-tools 35.0.0 are installed); adjust AGP only if required.
- Keep `minSdk 26`, `targetSdk 34`, Java 17.

## Error handling

- Grammar/theme asset load failures are logged and non-fatal; the editor falls back to no
  highlighting and the default color scheme.
- Unknown extensions open with no highlighting.
- File read/write errors behave as today (Toast, editor state untouched).

## Testing

- JVM unit tests for `LanguageRegistry` (extension → language/scope mapping, including
  unknown extensions and case-insensitivity).
- Remove `UndoStackTest` and `TextSearchTest` along with their classes. `RecentStore` is
  unchanged.
- Build verification: `assembleDebug`, `assembleRelease`, and `lintDebug` with zero errors.
- Manual device smoke test: open one file per language family, confirm highlighting, line
  numbers, current-line highlight, bracket auto-close, auto-indent, undo/redo, find/replace,
  save round-trip, and folding/unfolding preserving state.

## Risks and mitigations

- **Kotlin/AGP/dependency compatibility** — bump Kotlin first; raise compileSdk/AGP only if
  the build demands it. Verify with a build before further work.
- **Sora API specifics** — follow the official quickstart; confirm `setWordwrap`,
  `setTextSize`, and undo/redo/search method names against the 0.24.6 API during
  implementation.
- **Grammar availability/quality** — prefer sora's proven sample grammars; accept
  no-highlight fallback for any missing grammar.
- **APK size** — expect +2–4 MB from Sora, grammars, and TextMate regex deps; acceptable.
- **Highlight performance on large files** — TextMate is incremental; optional
  `oniguruma-native` module can be added later if needed.

## Out of scope

Tabs, folder browser, session restore, code folding, diagnostics/validation, formatting,
find-in-files, completion, LSP, non-UTF-8 encodings.
