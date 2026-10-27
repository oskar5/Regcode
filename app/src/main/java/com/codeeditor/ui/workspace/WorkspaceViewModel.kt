package com.codeeditor.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeeditor.core.autocomplete.BufferCompletionProvider
import com.codeeditor.core.autocomplete.CompletionContext
import com.codeeditor.core.autocomplete.CompletionEngine
import com.codeeditor.core.autocomplete.CompletionProvider
import com.codeeditor.core.editor.highlight.LanguageRegistry
import com.codeeditor.core.editor.highlight.SyntaxToken
import com.codeeditor.core.encoding.TextEncoding
import com.codeeditor.core.search.SearchMatch
import com.codeeditor.core.search.SearchOptions
import com.codeeditor.core.session.EditorSession
import com.codeeditor.domain.model.EditorSettings
import com.codeeditor.domain.model.FileNode
import com.codeeditor.domain.model.FileTooLargeException
import com.codeeditor.domain.model.SessionDocument
import com.codeeditor.domain.model.ThemeMode
import com.codeeditor.domain.repository.FileRepository
import com.codeeditor.domain.repository.SessionRepository
import com.codeeditor.domain.repository.SettingsRepository
import com.codeeditor.domain.usecase.OpenDocumentUseCase
import com.codeeditor.domain.usecase.SaveDocumentUseCase
import com.codeeditor.ui.editor.EditorAction
import com.codeeditor.ui.editor.EditorAction.AddNextOccurrence
import com.codeeditor.ui.editor.EditorAction.AcceptCompletion
import com.codeeditor.ui.editor.EditorAction.Backspace
import com.codeeditor.ui.editor.EditorAction.CloseFind
import com.codeeditor.ui.editor.EditorAction.CompletionNext
import com.codeeditor.ui.editor.EditorAction.CompletionPrevious
import com.codeeditor.ui.editor.EditorAction.DismissCompletion
import com.codeeditor.ui.editor.EditorAction.ShowCompletions
import com.codeeditor.ui.editor.EditorAction.ToggleKeyRow
import com.codeeditor.ui.editor.EditorAction.DeleteForward
import com.codeeditor.ui.editor.EditorAction.DuplicateLine
import com.codeeditor.ui.editor.EditorAction.FindNext
import com.codeeditor.ui.editor.EditorAction.FindPrevious
import com.codeeditor.ui.editor.EditorAction.MoveLineDown
import com.codeeditor.ui.editor.EditorAction.MoveLineUp
import com.codeeditor.ui.editor.EditorAction.OpenFind
import com.codeeditor.ui.editor.EditorAction.OpenReplace
import com.codeeditor.ui.editor.EditorAction.ReplaceAll
import com.codeeditor.ui.editor.EditorAction.ReplaceCurrent
import com.codeeditor.ui.editor.EditorAction.SelectWord
import com.codeeditor.ui.editor.EditorAction.BeginSelection
import com.codeeditor.ui.editor.EditorAction.ExtendSelection
import com.codeeditor.ui.editor.EditorAction.SelectAll
import com.codeeditor.ui.editor.EditorAction.DeleteSelection
import com.codeeditor.ui.editor.EditorAction.Save
import com.codeeditor.ui.editor.EditorAction.Paste
import com.codeeditor.ui.editor.EditorAction.GoToLine
import com.codeeditor.ui.editor.EditorAction.ToggleComment
import com.codeeditor.ui.editor.EditorAction.InsertTab
import com.codeeditor.ui.editor.EditorAction.InsertText
import com.codeeditor.ui.editor.EditorAction.MoveDown
import com.codeeditor.ui.editor.EditorAction.MoveLeft
import com.codeeditor.ui.editor.EditorAction.MoveLineEnd
import com.codeeditor.ui.editor.EditorAction.MoveLineStart
import com.codeeditor.ui.editor.EditorAction.MoveRight
import com.codeeditor.ui.editor.EditorAction.MoveTo
import com.codeeditor.ui.editor.EditorAction.MoveUp
import com.codeeditor.ui.editor.EditorAction.Newline
import com.codeeditor.ui.editor.EditorAction.Redo
import com.codeeditor.ui.editor.EditorAction.Undo
import com.codeeditor.ui.editor.CompletionState
import com.codeeditor.ui.editor.EditorUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val openDocument: OpenDocumentUseCase,
    private val saveDocument: SaveDocumentUseCase,
    private val fileRepository: FileRepository,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val sessions = mutableListOf<EditorSession>()
    private var activeId: String? = null
    private var settings = EditorSettings()
    private var matches: List<SearchMatch> = emptyList()
    private var currentMatch: Int = -1
    private var completionJob: Job? = null

    private val _state = MutableStateFlow(WorkspaceUiState())
    val state: StateFlow<WorkspaceUiState> = _state.asStateFlow()

    private var persistJob: Job? = null
    private var autosaveJob: Job? = null
    private var lastPersisted: List<SessionDocument>? = null

    init {
        viewModelScope.launch {
            settings = settingsRepository.settings.first()
            applySettings()
            restoreSession()
            refresh()

            launch {
                sessionRepository.observeRecent().collect { recent ->
                    _state.update { it.copy(recentFiles = recent) }
                }
            }
            launch {
                settingsRepository.settings.collect { updated ->
                    settings = updated
                    applySettings()
                    refresh()
                }
            }
        }
    }

    // ---------------------------------------------------------------- editor

    fun onAction(action: EditorAction) {
        when (action) {
            OpenFind -> { openFind(replace = false); return }
            OpenReplace -> { openFind(replace = true); return }
            CloseFind -> { closeFind(); return }
            FindNext -> { findNext(); return }
            FindPrevious -> { findPrevious(); return }
            ReplaceCurrent -> { replaceCurrent(); return }
            ReplaceAll -> { replaceAllMatches(); return }
            ShowCompletions -> { scheduleCompletions(immediate = true); return }
            CompletionNext -> { completionNext(); return }
            CompletionPrevious -> { completionPrevious(); return }
            AcceptCompletion -> { acceptCompletion(); return }
            DismissCompletion -> { dismissCompletion(); return }
            ToggleKeyRow -> { toggleKeyRow(); return }
            Save -> { saveActive(); return }
            else -> Unit
        }
        val session = activeSession() ?: return
        var autoComplete = false
        when (action) {
            is InsertText -> { session.insertText(action.text); autoComplete = true }
            Newline -> session.newline()
            InsertTab -> session.insertTab()
            Backspace -> { session.backspace(); autoComplete = true }
            DeleteForward -> { session.deleteForward(); autoComplete = true }
            Undo -> session.undo()
            Redo -> session.redo()
            MoveLeft -> { dismissCompletion(); session.moveLeft() }
            MoveRight -> { dismissCompletion(); session.moveRight() }
            MoveUp -> { dismissCompletion(); session.moveUp() }
            MoveDown -> { dismissCompletion(); session.moveDown() }
            MoveLineStart -> { dismissCompletion(); session.moveToColumn(0) }
            MoveLineEnd -> {
                dismissCompletion()
                session.moveToColumn(session.lineLength(session.caret.line))
            }
            is MoveTo -> { dismissCompletion(); session.setCaret(action.line, action.column) }
            ToggleComment -> { dismissCompletion(); session.toggleLineComment() }
            DuplicateLine -> { dismissCompletion(); session.duplicateLine() }
            MoveLineUp -> { dismissCompletion(); session.moveLineUp() }
            MoveLineDown -> { dismissCompletion(); session.moveLineDown() }
            AddNextOccurrence -> { dismissCompletion(); session.addNextOccurrence() }
            SelectWord -> { dismissCompletion(); session.selectWordAtCaret() }
            is BeginSelection -> { dismissCompletion(); session.beginSelection(action.line, action.column) }
            is ExtendSelection -> { dismissCompletion(); session.extendSelectionTo(action.line, action.column) }
            SelectAll -> { dismissCompletion(); session.selectAll() }
            DeleteSelection -> { dismissCompletion(); session.deleteSelection() }
            is Paste -> { dismissCompletion(); session.paste(action.text); autoComplete = false }
            is GoToLine -> {
                dismissCompletion()
                val line = action.line
                if (line < 1) {
                    session.moveToDocumentStart()
                } else {
                    val target = (line - 1).coerceIn(0, session.lineCount() - 1)
                    session.setCaret(target, 0)
                }
            }
            else -> Unit
        }
        scheduleAutosave(session)
        schedulePersist()
        if (_state.value.find.visible && _state.value.find.query.isNotEmpty()) {
            recomputeMatches(select = false)
        } else if (autoComplete) {
            scheduleCompletions()
        }
        refresh()
    }

    /** Text of a line of the active document (used by the virtualised renderer). */
    fun activeLineText(line: Int): String = activeSession()?.lineText(line) ?: ""

    /** Syntax tokens of a line of the active document. */
    fun activeTokens(line: Int): List<SyntaxToken> = activeSession()?.tokensForLine(line) ?: emptyList()

    // ---------------------------------------------------------------- find / replace

    fun openFind(replace: Boolean) {
        dismissCompletion()
        _state.update {
            it.copy(find = it.find.copy(visible = true, replaceVisible = replace || it.find.replaceVisible))
        }
        if (_state.value.find.query.isNotEmpty()) recomputeMatches(select = true) else publishFind()
    }

    fun closeFind() {
        matches = emptyList()
        currentMatch = -1
        _state.update {
            it.copy(find = it.find.copy(visible = false, replaceVisible = false, matchCount = 0, currentMatch = -1))
        }
    }

    fun setFindQuery(query: String) {
        _state.update { it.copy(find = it.find.copy(query = query)) }
        recomputeMatches(select = true)
    }

    fun setReplacement(replacement: String) {
        _state.update { it.copy(find = it.find.copy(replacement = replacement)) }
    }

    fun toggleCaseSensitive() = updateOptions { it.copy(caseSensitive = !it.caseSensitive) }

    fun toggleWholeWord() = updateOptions { it.copy(wholeWord = !it.wholeWord) }

    fun toggleRegex() = updateOptions { it.copy(regex = !it.regex) }

    private fun updateOptions(transform: (SearchOptions) -> SearchOptions) {
        _state.update { it.copy(find = it.find.copy(options = transform(it.find.options))) }
        recomputeMatches(select = true)
    }

    fun findNext() {
        if (_state.value.find.query.isEmpty()) return
        if (matches.isEmpty()) recomputeMatches(select = true)
        if (matches.isEmpty()) return
        if (currentMatch < 0) currentMatch = 0
        currentMatch = (currentMatch + 1) % matches.size
        activeSession()?.selectMatch(matches[currentMatch])
        publishFind()
    }

    fun findPrevious() {
        if (_state.value.find.query.isEmpty()) return
        if (matches.isEmpty()) recomputeMatches(select = true)
        if (matches.isEmpty()) return
        if (currentMatch < 0) currentMatch = 0
        currentMatch = ((currentMatch - 1) % matches.size + matches.size) % matches.size
        activeSession()?.selectMatch(matches[currentMatch])
        publishFind()
    }

    fun replaceCurrent() {
        val session = activeSession() ?: return
        if (currentMatch !in matches.indices) {
            findNext()
            return
        }
        session.replaceMatch(matches[currentMatch], _state.value.find.replacement)
        scheduleAutosave(session)
        schedulePersist()
        recomputeMatches(select = true)
        refresh()
    }

    fun replaceAllMatches() {
        val session = activeSession() ?: return
        val find = _state.value.find
        if (find.query.isEmpty()) return
        if (session.replaceAll(find.query, find.options, find.replacement) == 0) return
        scheduleAutosave(session)
        schedulePersist()
        recomputeMatches(select = true)
        refresh()
    }

    private fun recomputeMatches(select: Boolean) {
        val session = activeSession()
        val find = _state.value.find
        matches = if (session != null && find.query.isNotEmpty()) {
            session.findAll(find.query, find.options)
        } else {
            emptyList()
        }
        currentMatch = if (matches.isEmpty()) {
            -1
        } else {
            val caretOffset = if (session != null) session.document.offsetAt(session.caret) else 0
            matches.indexOfFirst { it.start >= caretOffset }.let { if (it >= 0) it else 0 }
        }
        if (select && currentMatch >= 0) session?.selectMatch(matches[currentMatch])
        publishFind()
    }

    private fun publishFind() {
        _state.update { it.copy(find = it.find.copy(matchCount = matches.size, currentMatch = currentMatch)) }
    }

    // ---------------------------------------------------------------- autocomplete

    private fun scheduleCompletions(immediate: Boolean = false) {
        val session = activeSession() ?: return
        if (_state.value.find.visible) return
        val line = session.lineText(session.caret.line)
        if (CompletionEngine.prefixBeforeCaret(line, session.caret.column).isEmpty()) {
            dismissCompletion()
            return
        }
        completionJob?.cancel()
        completionJob = viewModelScope.launch {
            if (!immediate) delay(COMPLETION_DELAY_MS)
            computeCompletions(session)
        }
    }

    private fun computeCompletions(session: EditorSession) {
        if (session !== activeSession()) return
        val line = session.lineText(session.caret.line)
        val prefix = CompletionEngine.prefixBeforeCaret(line, session.caret.column)
        if (prefix.isEmpty()) {
            _state.update { it.copy(completion = CompletionState()) }
            return
        }
        val context = CompletionContext(line, session.caret.column, session.language.id, session.wordIndex())
        val providers = listOf<CompletionProvider>(
            BufferCompletionProvider(
                index = session.wordIndex(),
                keywords = session.language.keywords,
                types = session.language.types
            )
        )
        val items = CompletionEngine.complete(prefix, context, providers)
        _state.update {
            it.copy(
                completion = CompletionState(
                    visible = items.isNotEmpty(),
                    items = items,
                    filterText = prefix,
                    selectedIndex = 0,
                    caretOffset = session.document.offsetAt(session.caret)
                )
            )
        }
    }

    fun completionNext() {
        _state.update { state ->
            val current = state.completion
            if (!current.visible || current.items.isEmpty()) {
                state
            } else {
                state.copy(completion = current.copy(selectedIndex = (current.selectedIndex + 1) % current.items.size))
            }
        }
    }

    fun completionPrevious() {
        _state.update { state ->
            val current = state.completion
            if (!current.visible || current.items.isEmpty()) {
                state
            } else {
                state.copy(
                    completion = current.copy(selectedIndex = (current.selectedIndex - 1 + current.items.size) % current.items.size)
                )
            }
        }
    }

    fun acceptCompletion() {
        val comp = _state.value.completion
        acceptCompletionAt(comp.selectedIndex)
    }

    fun acceptCompletionAt(index: Int) {
        val session = activeSession() ?: return
        val comp = _state.value.completion
        if (!comp.visible || comp.items.isEmpty()) return
        val item = comp.items[index.coerceIn(0, comp.items.size - 1)]
        val currentLine = session.lineText(session.caret.line)
        val currentPrefix = CompletionEngine.prefixBeforeCaret(currentLine, session.caret.column)
        if (currentPrefix != comp.filterText) {
            dismissCompletion()
            return
        }
        val caretOffset = session.document.offsetAt(session.caret)
        val prefixStart = caretOffset - comp.filterText.length
        if (prefixStart >= 0) {
            session.replaceMatch(SearchMatch(prefixStart, caretOffset), item.label)
            scheduleAutosave(session)
            schedulePersist()
        }
        dismissCompletion()
        refresh()
    }

    fun dismissCompletion() {
        completionJob?.cancel()
        _state.update { it.copy(completion = CompletionState()) }
    }

    // ---------------------------------------------------------------- mobile UX

    fun toggleKeyRow() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(showKeyRow = !it.showKeyRow) }
        }
    }

    fun updateFontSize(fontSizeSp: Float) {
        viewModelScope.launch {
            settingsRepository.update {
                it.copy(fontSizeSp = fontSizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP))
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(themeMode = mode) }
        }
    }

    fun setEditorScheme(id: String) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(editorSchemeId = id) }
        }
    }

    fun setFontFamily(id: String) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(fontFamilyId = id) }
        }
    }

    fun toggleLineNumbers() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(showLineNumbers = !it.showLineNumbers) }
        }
    }

    fun toggleWordWrap() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(wordWrap = !it.wordWrap) }
        }
    }

    // ---------------------------------------------------------------- tabs

    fun newFile() {
        val session = EditorSession(
            id = UUID.randomUUID().toString(),
            uri = null,
            title = "Untitled.txt",
            initialText = "",
            language = LanguageRegistry.byId("plaintext")
        )
        session.indentUnit = indentUnit()
        sessions.add(session)
        activeId = session.id
        refresh()
    }

    fun selectTab(id: String) {
        if (sessions.none { it.id == id }) return
        activeId = id
        dismissCompletion()
        persistActiveUri()
        if (_state.value.find.visible) recomputeMatches(select = false)
        refresh()
    }

    fun closeTab(id: String) {
        val index = sessions.indexOfFirst { it.id == id }
        if (index < 0) return
        dismissCompletion()
        val closing = sessions[index]
        sessions.removeAt(index)
        val closingUri = closing.uri
        if (closingUri != null) {
            viewModelScope.launch { sessionRepository.remove(closingUri) }
        }
        if (activeId == id) {
            activeId = sessions.getOrNull(index.coerceAtMost(sessions.size - 1))?.id
        }
        refresh()
    }

    fun closeActiveTab() {
        activeId?.let(::closeTab)
    }

    // ---------------------------------------------------------------- files

    fun openFile(uri: String) {
        val existing = sessions.firstOrNull { it.uri == uri }
        if (existing != null) {
            activeId = existing.id
            refresh()
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            runCatching { openDocument(uri) }
                .onSuccess { loaded ->
                    val session = EditorSession(
                        id = uri,
                        uri = uri,
                        title = loaded.name,
                        initialText = loaded.text,
                        language = LanguageRegistry.forFileName(loaded.name),
                        encoding = loaded.encoding,
                        hasBom = loaded.hasBom
                    )
                    session.indentUnit = indentUnit()
                    sessions.add(session)
                    activeId = session.id
                    if (loaded.encodingGuessed) {
                        _state.update {
                            it.copy(
                                message = "Кодування визначено приблизно: ${loaded.encoding.displayName}. " +
                                    "Якщо текст відображається неправильно, змініть кодування вручну."
                            )
                        }
                    }
                    sessions.forEachIndexed { i, s ->
                        if (s.uri != null) sessionRepository.upsert(s.toDocument(i))
                    }
                }
                .onFailure { error ->
                    val text = if (error is FileTooLargeException) {
                        "Файл завеликий: ${error.limitBytes / (1024 * 1024)} МБ — це максимум."
                    } else {
                        "Не вдалося відкрити файл: ${error.message}"
                    }
                    _state.update { it.copy(message = text) }
                }
            _state.update { it.copy(isLoading = false) }
            dismissCompletion()
            if (_state.value.find.visible) recomputeMatches(select = false)
            refresh()
        }
    }

    fun openFolder(uri: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            fileRepository.takePersistablePermission(uri)
            runCatching { fileRepository.listChildren(uri) }
                .onSuccess { children ->
                    val root = FileNode(uri, fileRepository.displayName(uri), true, null)
                    settingsRepository.update { it.copy(lastFolderUri = uri) }
                    _state.update {
                        it.copy(
                            rootFolder = root,
                            folderChildren = mapOf(uri to children),
                            expandedDirs = setOf(uri)
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(message = "Не вдалося відкрити теку: ${error.message}") }
                }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun toggleDirectory(uri: String) {
        val current = _state.value
        if (current.expandedDirs.contains(uri)) {
            _state.update { it.copy(expandedDirs = it.expandedDirs - uri) }
            return
        }
        if (current.folderChildren.containsKey(uri)) {
            _state.update { it.copy(expandedDirs = it.expandedDirs + uri) }
            return
        }
        viewModelScope.launch {
            val children = runCatching { fileRepository.listChildren(uri) }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    folderChildren = it.folderChildren + (uri to children),
                    expandedDirs = it.expandedDirs + uri
                )
            }
        }
    }

    fun onTreeRowClick(row: TreeRow) {
        if (row.node.isDirectory) toggleDirectory(row.node.uri) else openFile(row.node.uri)
    }

    // ---------------------------------------------------------------- persistence

    fun saveActive() {
        val session = activeSession() ?: return
        viewModelScope.launch {
            runCatching { saveDocument(session) }
                .onFailure { error ->
                    _state.update { it.copy(message = "Помилка збереження: ${error.message}") }
                }
            schedulePersist()
            refresh()
        }
    }

    /**
     * "Save As": writes the active buffer to [uri] (created by the SAF picker),
     * then retargets the tab at the new file — updating its title and language —
     * while the old file is left untouched.
     */
    fun saveActiveAs(uri: String) {
        val session = activeSession() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            runCatching {
                fileRepository.takePersistablePermission(uri)
                fileRepository.writeDocument(
                    uri = uri,
                    text = session.content,
                    encoding = session.encoding,
                    lineEnding = session.lineEnding,
                    withBom = session.hasBom
                )
            }.onSuccess {
                val previousUri = session.uri
                val name = fileRepository.displayName(uri)
                session.retarget(uri, name, LanguageRegistry.forFileName(name))
                session.markSaved()
                if (previousUri != null && previousUri != uri) {
                    sessionRepository.remove(previousUri)
                }
                schedulePersist()
                persistActiveUri()
            }.onFailure { error ->
                _state.update { it.copy(message = "Не вдалося зберегти файл: ${error.message}") }
            }
            _state.update { it.copy(isLoading = false) }
            refresh()
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private suspend fun restoreSession() {
        val restored = sessionRepository.loadSession()
        if (restored.isEmpty()) {
            openSample()
            return
        }
        restored.forEach { doc ->
            // Clean buffers are not stored in the database; reload them from disk
            // (this also recovers their original encoding). Skip tabs whose file
            // has since disappeared.
            val loaded = if (doc.content.isEmpty() && doc.uri.isNotEmpty()) {
                runCatching { fileRepository.readDocument(doc.uri) }.getOrNull() ?: return@forEach
            } else {
                null
            }
            val session = EditorSession(
                id = doc.uri,
                uri = doc.uri,
                title = doc.title,
                initialText = loaded?.text ?: doc.content,
                language = LanguageRegistry.byId(doc.languageId),
                encoding = loaded?.encoding ?: TextEncoding.UTF_8,
                hasBom = loaded?.hasBom ?: false
            )
            session.indentUnit = indentUnit()
            session.setCaret(doc.cursorLine, doc.cursorColumn)
            if (doc.isDirty) session.markDirty()
            sessions.add(session)
        }
        if (sessions.isEmpty()) {
            openSample()
            return
        }
        activeId = settings.lastActiveUri?.takeIf { uri -> sessions.any { it.uri == uri } }
            ?: sessions.first().id
        settings.lastFolderUri?.let { uri ->
            viewModelScope.launch {
                val children = runCatching { fileRepository.listChildren(uri) }.getOrDefault(emptyList())
                val root = FileNode(uri, fileRepository.displayName(uri), true, null)
                _state.update {
                    it.copy(rootFolder = root, folderChildren = mapOf(uri to children), expandedDirs = setOf(uri))
                }
            }
        }
    }

    private fun openSample() {
        val sample = """
            package com.example

            /**
             * Вітаємо в редакторі!
             * Спробуйте: введення, Enter, відступ, Ctrl+Z, вкладки вгорі.
             */
            class Greeter(private val name: String) {
                fun greet(): String {
                    val count = 42
                    return "Hello, ${'$'}name! (#${'$'}count)"
                }
            }

            fun main() = println(Greeter("world").greet())
        """.trimIndent()
        val session = EditorSession(
            id = UUID.randomUUID().toString(),
            uri = null,
            title = "Untitled.kt",
            initialText = sample,
            language = LanguageRegistry.byId("kotlin")
        )
        session.indentUnit = indentUnit()
        sessions.add(session)
        activeId = session.id
    }

    private fun scheduleAutosave(session: EditorSession) {
        if (!settings.autosave || session.uri == null || !session.dirty) return
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            runCatching { saveDocument(session) }
            schedulePersist()
        }
    }

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(PERSIST_DELAY_MS)
            persistSession()
        }
    }

    private suspend fun persistSession() {
        val documents = sessions.mapIndexedNotNull { index, session ->
            if (session.uri == null) null else session.toDocument(index)
        }
        if (documents == lastPersisted) return
        sessionRepository.saveSession(documents)
        lastPersisted = documents
    }

    private fun persistActiveUri() {
        val uri = activeSession()?.uri ?: return
        viewModelScope.launch { settingsRepository.update { it.copy(lastActiveUri = uri) } }
    }

    // ---------------------------------------------------------------- helpers

    private fun activeSession(): EditorSession? =
        sessions.firstOrNull { it.id == activeId } ?: sessions.firstOrNull()

    private fun applySettings() {
        sessions.forEach {
            it.indentUnit = indentUnit()
            it.autoCloseBrackets = settings.autoCloseBrackets
        }
    }

    private fun indentUnit(): String = " ".repeat(settings.tabWidth.coerceIn(1, 8))

    private fun EditorSession.toDocument(order: Int) = SessionDocument(
        uri = requireNotNull(uri),
        title = title,
        languageId = language.id,
        // Clean, file-backed buffers are reloaded from disk on restore, so only
        // dirty buffers need their content persisted. This keeps large files out
        // of the session database.
        content = if (dirty) content else "",
        cursorLine = caret.line,
        cursorColumn = caret.column,
        isDirty = dirty,
        orderIndex = order
    )

    private fun refresh() {
        val active = activeSession()
        val editor = active?.let {
            EditorUiState(
                fileName = it.title,
                languageId = it.language.id,
                contentVersion = it.document.version,
                lineCount = it.lineCount(),
                maxLineLength = it.maxLineLength(),
                cursor = it.caret,
                selection = it.selection,
                selections = it.allSelections,
                isDirty = it.dirty,
                canUndo = it.canUndo,
                canRedo = it.canRedo,
                lineEnding = it.lineEnding,
                selectedText = it.selectedText()
            )
        } ?: EditorUiState()

        _state.update { current ->
            current.copy(
                tabs = sessions.map { TabInfo(it.id, it.title, it.dirty, it.language.id) },
                activeTabId = active?.id,
                editor = editor,
                settings = settings
            )
        }
    }

    companion object {
        private const val AUTOSAVE_DELAY_MS = 800L
        private const val PERSIST_DELAY_MS = 600L
        private const val COMPLETION_DELAY_MS = 120L
        private const val MIN_FONT_SIZE_SP = 10f
        private const val MAX_FONT_SIZE_SP = 28f
    }
}
