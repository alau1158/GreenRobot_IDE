package com.alau.texteditor

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.alau.texteditor.databinding.ActivityMainBinding
import com.alau.texteditor.databinding.DialogFindBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.EditorSearcher
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var recents: RecentStore

    private val prefs by lazy { getSharedPreferences("editor", MODE_PRIVATE) }

    private var currentUri: Uri? = null
    private var currentName: String? = null
    private var currentWritable = false
    private var dirty = false

    private var suppressChange = false
    private var cursorLine = 1
    private var cursorColumn = 1

    private var wrapEnabled = true
    private var monoEnabled = true
    private var fontSizeSp = 16f

    private var lastQuery = ""
    private var lastMatchCase = false

    private var pendingAction: (() -> Unit)? = null

    private val openDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) openUri(uri)
        }

    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) saveTo(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        recents = RecentStore(this)

        TextMateBootstrap.init(this)

        fontSizeSp = prefs.getFloat(KEY_FONT, 16f)
        wrapEnabled = prefs.getBoolean(KEY_WRAP, true)
        monoEnabled = prefs.getBoolean(KEY_MONO, true)

        TextMateBootstrap.setTheme(isDarkMode())
        TextMateBootstrap.applyColorScheme(binding.editor)
        binding.editor.setLineNumberEnabled(true)
        binding.editor.setHighlightCurrentLine(true)
        binding.editor.subscribeEvent(ContentChangeEvent::class.java) { _, _ -> onEditorContentChanged() }
        binding.editor.subscribeEvent(SelectionChangeEvent::class.java) { event, _ ->
            cursorLine = event.left.line + 1
            cursorColumn = event.left.column + 1
            updateStatus()
        }
        applyEditorPrefs()

        if (savedInstanceState != null) {
            currentUri = savedInstanceState.getString(STATE_URI)?.let(Uri::parse)
            currentName = savedInstanceState.getString(STATE_NAME)
            dirty = savedInstanceState.getBoolean(STATE_DIRTY, false)
            currentWritable = currentUri?.let { hasWritePermission(it) } ?: false
            updateTitle()
            updateStatus()
        } else {
            handleIntent(intent)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmExit()
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_URI, currentUri?.toString())
        outState.putString(STATE_NAME, currentName)
        outState.putBoolean(STATE_DIRTY, dirty)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        updateTitle()
        updateStatus()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_undo)?.isEnabled = binding.editor.canUndo()
        menu.findItem(R.id.action_redo)?.isEnabled = binding.editor.canRedo()
        menu.findItem(R.id.action_wrap)?.isChecked = wrapEnabled
        menu.findItem(R.id.action_mono)?.isChecked = monoEnabled
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_new -> guardUnsaved { newDocument() }
            R.id.action_open -> guardUnsaved { openDocument.launch(arrayOf("*/*")) }
            R.id.action_recents -> showRecents()
            R.id.action_save -> saveCurrent()
            R.id.action_save_as -> createDocument.launch(suggestedName())
            R.id.action_undo -> {
                binding.editor.undo()
                invalidateOptionsMenu()
            }
            R.id.action_redo -> {
                binding.editor.redo()
                invalidateOptionsMenu()
            }
            R.id.action_find -> showFindDialog()
            R.id.action_wrap -> {
                wrapEnabled = !wrapEnabled
                applyEditorPrefs()
            }
            R.id.action_mono -> {
                monoEnabled = !monoEnabled
                applyEditorPrefs()
            }
            R.id.action_font_inc -> changeFontSize(1f)
            R.id.action_font_dec -> changeFontSize(-1f)
            R.id.action_about -> showAbout()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onDestroy() {
        binding.editor.release()
        super.onDestroy()
    }

    // ---------------------------------------------------------------------------
    // Editor state
    // ---------------------------------------------------------------------------

    private fun editorText(): String = binding.editor.text.toString()

    private fun onEditorContentChanged() {
        if (suppressChange) return
        if (!dirty) {
            dirty = true
            updateTitle()
        }
        updateStatus()
    }

    private fun setEditorText(text: String) {
        suppressChange = true
        binding.editor.setText(text)
        suppressChange = false
        cursorLine = 1
        cursorColumn = 1
        updateStatus()
    }

    // ---------------------------------------------------------------------------
    // Open / save
    // ---------------------------------------------------------------------------

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        if (uri != null && (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_EDIT)) {
            openUri(uri)
        }
    }

    private fun openUri(uri: Uri) {
        val text = try {
            readText(uri)
        } catch (e: Exception) {
            toast(getString(R.string.error_open, e.message ?: ""))
            return
        }

        takePermission(uri)
        currentUri = uri
        currentName = queryName(uri)
        currentWritable = hasWritePermission(uri)

        setEditorText(text)
        val definition = LanguageRegistry.forFileName(currentName)
        binding.editor.setEditorLanguage(definition?.let { TextMateBootstrap.languageFor(it) })
        dirty = false
        recents.add(uri.toString(), currentName ?: uri.toString())
        updateTitle()
        updateStatus()
    }

    private fun saveCurrent() {
        val uri = currentUri
        if (uri != null && currentWritable) {
            try {
                writeText(uri, editorText())
                dirty = false
                updateTitle()
                updateStatus()
                toast(getString(R.string.saved))
                runPending()
                return
            } catch (e: SecurityException) {
                currentWritable = false
            } catch (e: Exception) {
                toast(getString(R.string.error_save, e.message ?: ""))
                return
            }
        }
        createDocument.launch(suggestedName())
    }

    private fun saveTo(uri: Uri) {
        try {
            writeText(uri, editorText())
        } catch (e: Exception) {
            toast(getString(R.string.error_save, e.message ?: ""))
            return
        }
        takePermission(uri)
        currentUri = uri
        currentWritable = true
        currentName = queryName(uri) ?: currentName
        dirty = false
        val definition = LanguageRegistry.forFileName(currentName)
        binding.editor.setEditorLanguage(definition?.let { TextMateBootstrap.languageFor(it) })
        recents.add(uri.toString(), currentName ?: uri.toString())
        updateTitle()
        updateStatus()
        toast(getString(R.string.saved))
        runPending()
    }

    private fun readText(uri: Uri): String {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error(getString(R.string.error_open, uri.toString()))
        var text = String(bytes, StandardCharsets.UTF_8)
        if (text.startsWith('\uFEFF')) text = text.substring(1)
        return text
    }

    private fun writeText(uri: Uri, text: String) {
        contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(text.toByteArray(StandardCharsets.UTF_8))
        } ?: error(getString(R.string.error_save, uri.toString()))
    }

    private fun takePermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (ignored: SecurityException) {
                // Session-only access is fine.
            }
        }
    }

    private fun hasWritePermission(uri: Uri): Boolean =
        contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }

    private fun queryName(uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) return cursor.getString(index)
                    }
                }
            } catch (ignored: Exception) {
                // fall through
            }
        }
        return uri.lastPathSegment
    }

    private fun suggestedName(): String =
        currentName ?: getString(R.string.untitled_txt)

    // ---------------------------------------------------------------------------
    // Unsaved-changes guards and new document
    // ---------------------------------------------------------------------------

    private fun guardUnsaved(action: () -> Unit) {
        if (!dirty) {
            action()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.unsaved_title)
            .setMessage(R.string.unsaved_message)
            .setPositiveButton(R.string.save) { _, _ ->
                pendingAction = action
                saveCurrent()
            }
            .setNegativeButton(R.string.discard) { _, _ -> action() }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun confirmExit() {
        if (!dirty) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.unsaved_title)
            .setMessage(R.string.unsaved_exit)
            .setPositiveButton(R.string.save) { _, _ ->
                pendingAction = { finish() }
                saveCurrent()
            }
            .setNegativeButton(R.string.discard) { _, _ -> finish() }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun runPending() {
        val action = pendingAction
        pendingAction = null
        action?.invoke()
    }

    private fun newDocument() {
        currentUri = null
        currentName = null
        currentWritable = false
        setEditorText("")
        binding.editor.setEditorLanguage(null)
        dirty = false
        updateTitle()
        updateStatus()
    }

    // ---------------------------------------------------------------------------
    // Recents, find/replace, preferences, about
    // ---------------------------------------------------------------------------

    private fun showRecents() {
        val entries = recents.load()
        if (entries.isEmpty()) {
            toast(getString(R.string.no_recents))
            return
        }
        val names = entries.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.recents)
            .setItems(names) { _, which ->
                guardUnsaved { openUri(Uri.parse(entries[which].uri)) }
            }
            .setNeutralButton(R.string.clear) { _, _ -> recents.clear() }
            .setNegativeButton(R.string.close, null)
            .show()
    }

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
        var pending: (() -> Unit)? = null

        fun startSearch(then: () -> Unit) {
            lastQuery = dialogBinding.findInput.text?.toString() ?: ""
            lastMatchCase = dialogBinding.matchCase.isChecked
            if (lastQuery.isEmpty()) {
                searcher.stopSearch()
                return
            }
            pending = then
            searcher.search(lastQuery, EditorSearcher.SearchOptions(!lastMatchCase, false))
        }

        val receipt = binding.editor.subscribeEvent(PublishSearchResultEvent::class.java) { _, _ ->
            val action = pending
            pending = null
            action?.invoke()
        }

        dialogBinding.btnNext.setOnClickListener {
            startSearch {
                if (!searcher.gotoNext()) toast(getString(R.string.no_matches))
            }
        }
        dialogBinding.btnPrev.setOnClickListener {
            startSearch {
                if (!searcher.gotoPrevious()) toast(getString(R.string.no_matches))
            }
        }
        dialogBinding.btnReplace.setOnClickListener {
            val replacement = dialogBinding.replaceInput.text?.toString() ?: ""
            startSearch {
                searcher.replaceCurrentMatch(replacement)
            }
        }
        dialogBinding.btnReplaceAll.setOnClickListener {
            val replacement = dialogBinding.replaceInput.text?.toString() ?: ""
            startSearch {
                val count = searcher.matchedPositionCount
                if (count > 0) {
                    searcher.replaceAll(replacement)
                    toast(getString(R.string.replaced_n, count))
                } else {
                    toast(getString(R.string.no_matches))
                }
            }
        }

        dialog.setOnDismissListener { receipt.unsubscribe() }
        dialog.show()
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_name)
            .setMessage(R.string.about_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun changeFontSize(delta: Float) {
        fontSizeSp = (fontSizeSp + delta).coerceIn(MIN_FONT_SP, MAX_FONT_SP)
        applyEditorPrefs()
    }

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

    // ---------------------------------------------------------------------------
    // Title / status bar
    // ---------------------------------------------------------------------------

    private fun isDarkMode(): Boolean {
        val mask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mask == Configuration.UI_MODE_NIGHT_YES
    }

    private fun updateTitle() {
        val name = currentName ?: getString(R.string.untitled)
        supportActionBar?.title = if (dirty) "$name •" else name
    }

    private fun updateStatus() {
        binding.statusBar.text =
            getString(R.string.status_format, cursorLine, cursorColumn, binding.editor.text.length)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val KEY_FONT = "font"
        private const val KEY_WRAP = "wrap"
        private const val KEY_MONO = "mono"
        private const val STATE_URI = "uri"
        private const val STATE_NAME = "name"
        private const val STATE_DIRTY = "dirty"
        private const val MIN_FONT_SP = 8f
        private const val MAX_FONT_SP = 40f
    }
}
