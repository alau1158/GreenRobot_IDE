# Android Text Editor — Design

Date: 2026-10-07
Target device: Samsung Galaxy Z Fold 6 (inner ~7.6" / outer cover display), Android 14 (API 34)

## Purpose

A simple, reliable text editor for reading and modifying plain text files on the phone.
Prioritize correctness of open/save and not losing the user's edits over feature count.

## Scope (YAGNI)

In scope:
- Open a text file via the system file picker (Storage Access Framework).
- Open a text file shared from another app (ACTION_VIEW / ACTION_EDIT, text/*).
- Edit with a monospace-friendly multi-line editor.
- Save (overwrite the current file) and Save As (create a new file).
- Recent files list, with persisted access permissions.
- Undo / Redo.
- Find and replace (next, replace, replace all, match case).
- Toggle word wrap; toggle monospace; increase/decrease font size.
- Status bar: line/column, character count, encoding.
- Unsaved-changes guard on back / open / new.
- Dark mode support.

Out of scope: syntax highlighting, tabs/multiple documents, cloud sync, markdown preview,
binary/hex editing, non-UTF-8 encodings (UTF-8 + UTF-8 BOM only), line-number gutter.

## Architecture

Single `MainActivity`, view-based (no Compose) for a light, reliable build and good behavior
with large text. Pure logic is extracted into testable classes with JVM unit tests.

- `MainActivity` — UI wiring, SAF open/save, menu, dialogs, dirty state.
- `EditorEditText` — `AppCompatEditText` subclass exposing selection changes (for status bar).
- `TextSearch` — pure search/replace helpers (`findAll`, `replaceAll`).
- `UndoStack` — pure bounded snapshot undo/redo stack.
- `RecentStore` — persisted recent files (SharedPreferences JSON).

Configuration changes (rotation, folding/unfolding) are handled in-place via
`android:configChanges` so the editor state (text, cursor, scroll, undo) is never lost.

## Data flow

Open: SAF `OpenDocument` -> take persistable read/write permission -> read UTF-8 (strip BOM)
-> load into editor -> record baseline -> add to recents.
Save: if current URI is writable -> write UTF-8 (truncating) -> clear dirty; else Save As.
Save As: `CreateDocument("text/plain")` -> write UTF-8 -> adopt new URI -> clear dirty.

## Error handling

- Read/write failures surface a Toast and leave the editor state untouched.
- Persistable permission failures are non-fatal (file still works for this session).
- Files that cannot be opened are reported and skipped.

## Testing

- JVM unit tests for `TextSearch` and `UndoStack` (core correctness).
- Manual build verification: `assembleDebug` produces an installable, debug-signed APK.
