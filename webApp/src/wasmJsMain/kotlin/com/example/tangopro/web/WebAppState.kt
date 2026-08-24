package com.example.tangopro.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.tangopro.domain.AnswerNormalizer
import com.example.tangopro.domain.QuizQuestionFactory
import com.example.tangopro.domain.StudyFilterMode
import com.example.tangopro.domain.StudyLanguage
import com.example.tangopro.domain.StudyProgress
import com.example.tangopro.domain.StudyRound
import com.example.tangopro.model.StudyGroupRecord
import com.example.tangopro.model.StudyQuestion
import com.example.tangopro.model.WordRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class WebScreen { DASHBOARD, WORDS, STUDY, SUMMARY }
data class PendingCsvImport(val suggestedName: String, val text: String)

class WebAppState(
    private val repository: WebRepository,
    private val scope: CoroutineScope,
) {
    private val archiveService = WebArchiveService(repository)
    var groups by mutableStateOf<List<StudyGroupRecord>>(emptyList())
        private set
    var rounds by mutableStateOf<Map<Long, Int>>(emptyMap())
        private set
    var selectedGroup by mutableStateOf<StudyGroupRecord?>(null)
        private set
    var words by mutableStateOf<List<WordRecord>>(emptyList())
        private set
    var screen by mutableStateOf(WebScreen.DASHBOARD)
        private set
    var isBusy by mutableStateOf(true)
        private set
    var bootstrapProgress by mutableStateOf("データベースを準備しています…")
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var pendingCsvImport by mutableStateOf<PendingCsvImport?>(null)
        private set

    var directionForward by mutableStateOf(settingBoolean("directionForward", true))
        private set
    var multipleChoice by mutableStateOf(settingBoolean("multipleChoice", true))
        private set
    var filterMode by mutableStateOf(getSetting("filterMode") ?: StudyFilterMode.RECOMMEND)
        private set
    var selectedTag by mutableStateOf("すべて")
        private set
    var useRange by mutableStateOf(false)
        private set
    var rangeStart by mutableStateOf("1")
        private set
    var rangeEnd by mutableStateOf("")
        private set
    var quizCount by mutableStateOf((getSetting("quizCount")?.toIntOrNull() ?: 10).takeIf { it in setOf(5, 10, 20, 50, 100_000) } ?: 10)
        private set
    var darkTheme by mutableStateOf(settingBoolean("darkTheme", false))
        private set
    var simpleMode by mutableStateOf(settingBoolean("simpleMode", false))
        private set
    var evenStudyLayout by mutableStateOf(settingBoolean("evenStudyLayout", false))
        private set
    var textScale by mutableStateOf(getSetting("textScale")?.toFloatOrNull()?.coerceIn(0.8f, 1.4f) ?: 1f)
        private set
    var ttsEnabled by mutableStateOf(settingBoolean("ttsEnabled", true))
        private set
    var ttsVolume by mutableStateOf(getSetting("ttsVolume")?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 1.0)
        private set
    var soundVolume by mutableStateOf(getSetting("soundVolume")?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 0.5)
        private set
    var persistentStorage by mutableStateOf<Boolean?>(null)
        private set

    var questions by mutableStateOf<List<StudyQuestion>>(emptyList())
        private set
    var currentIndex by mutableStateOf(0)
        private set
    var checked by mutableStateOf(false)
        private set
    var answer by mutableStateOf("")
        private set
    var correct by mutableStateOf(false)
        private set
    var score by mutableStateOf(0)
        private set

    private var wordsJob: Job? = null

    init {
        scope.launch {
            repository.groups.collectLatest { updated ->
                groups = updated
                val currentId = selectedGroup?.id ?: getSetting("lastGroup")?.toLongOrNull()
                val next = updated.firstOrNull { it.id == currentId } ?: updated.firstOrNull()
                if (next?.id != selectedGroup?.id) selectGroup(next)
            }
        }
        scope.launch { repository.rounds.collectLatest { rounds = it } }
        scope.launch {
            try {
                repository.bootstrapBundledGroups { completed, total ->
                    bootstrapProgress = "組み込み単語帳を準備しています ($completed / $total)"
                }
                requestPersistentStorage { persistentStorage = it }
            } catch (error: Throwable) {
                notice = "初期化に失敗しました: ${error.message ?: error::class.simpleName}"
            } finally {
                isBusy = false
                removeLoading()
            }
        }
    }

    fun selectGroup(group: StudyGroupRecord?) {
        selectedGroup = group
        wordsJob?.cancel()
        words = emptyList()
        if (group == null) return
        setSetting("lastGroup", group.id.toString())
        multipleChoice = groupBoolean(group.id, "multipleChoice", settingBoolean("multipleChoice", true))
        directionForward = multipleChoice && groupBoolean(group.id, "directionForward", settingBoolean("directionForward", true))
        filterMode = StudyFilterMode.normalize(groupSetting(group.id, "filterMode") ?: getSetting("filterMode"))
        selectedTag = groupSetting(group.id, "selectedTag") ?: "すべて"
        useRange = groupBoolean(group.id, "useRange", false)
        rangeStart = groupSetting(group.id, "rangeStart") ?: "1"
        rangeEnd = groupSetting(group.id, "rangeEnd") ?: ""
        quizCount = (groupSetting(group.id, "quizCount")?.toIntOrNull() ?: getSetting("quizCount")?.toIntOrNull() ?: 10)
            .takeIf { it in setOf(5, 10, 20, 50, 100_000) } ?: 10
        wordsJob = scope.launch {
            repository.words(group.id).collectLatest { updated ->
                words = updated
                if (selectedTag != "すべて" && updated.none { it.tag == selectedTag }) selectedTag = "すべて"
            }
        }
    }

    fun showDashboard() { screen = WebScreen.DASHBOARD }
    fun showWords() { if (selectedGroup != null) screen = WebScreen.WORDS }
    fun clearNotice() { notice = null }

    fun setDirection(value: Boolean) {
        directionForward = multipleChoice && value
        setCurrentGroupSetting("directionForward", directionForward.toString())
    }
    fun setMultipleChoice(value: Boolean) {
        multipleChoice = value
        if (!value) directionForward = false
        setCurrentGroupSetting("multipleChoice", value.toString())
        setCurrentGroupSetting("directionForward", directionForward.toString())
    }
    fun setFilter(value: String) { filterMode = StudyFilterMode.normalize(value); setCurrentGroupSetting("filterMode", filterMode) }
    fun setTag(value: String) { selectedTag = value; setCurrentGroupSetting("selectedTag", value) }
    fun setUseRange(value: Boolean) { useRange = value; setCurrentGroupSetting("useRange", value.toString()) }
    fun setRangeStart(value: String) { rangeStart = value.filter(Char::isDigit).take(6); setCurrentGroupSetting("rangeStart", rangeStart) }
    fun setRangeEnd(value: String) { rangeEnd = value.filter(Char::isDigit).take(6); setCurrentGroupSetting("rangeEnd", rangeEnd) }
    fun setQuizCount(value: Int) { quizCount = value; setCurrentGroupSetting("quizCount", value.toString()) }
    fun setDarkTheme(value: Boolean) { darkTheme = value; setSetting("darkTheme", value.toString()) }
    fun setSimpleMode(value: Boolean) { simpleMode = value; setSetting("simpleMode", value.toString()) }
    fun setEvenStudyLayout(value: Boolean) { evenStudyLayout = value; setSetting("evenStudyLayout", value.toString()) }
    fun setTextScale(value: Float) { textScale = value.coerceIn(0.8f, 1.4f); setSetting("textScale", textScale.toString()) }
    fun setTtsEnabled(value: Boolean) { ttsEnabled = value; setSetting("ttsEnabled", value.toString()) }
    fun setTtsVolume(value: Double) { ttsVolume = value.coerceIn(0.0, 1.0); setSetting("ttsVolume", ttsVolume.toString()) }
    fun setSoundVolume(value: Double) { soundVolume = value.coerceIn(0.0, 1.0); setSetting("soundVolume", soundVolume.toString()) }

    fun importCsv() {
        pickTextFile(".csv,text/csv,text/plain") { filename, text ->
            pendingCsvImport = PendingCsvImport(filename.substringBeforeLast('.').ifBlank { "新しい単語帳" }, text)
        }
    }

    fun cancelCsvImport() { pendingCsvImport = null }

    fun confirmCsvImport(name: String, language: String) {
        val pending = pendingCsvImport ?: return
        if (name.isBlank()) return
        pendingCsvImport = null
        scope.launch {
            runBusy("CSVを読み込んでいます…") {
                val normalizedName = name.trim().take(200)
                val id = repository.importCsv(normalizedName, language, pending.text)
                selectGroup(repository.groupsDirect().firstOrNull { it.id == id })
                notice = "「$normalizedName」を追加しました。"
            }
        }
    }

    fun combineGroups(groupIds: List<Long>, name: String, language: String) {
        scope.launch {
            runBusy("単語帳を連結しています…") {
                val normalizedName = name.trim().take(200)
                val id = repository.combine(groupIds, normalizedName, language)
                selectGroup(repository.groupsDirect().firstOrNull { it.id == id })
                notice = "「$normalizedName」を作成しました。"
            }
        }
    }

    fun exportCsv(share: Boolean = false) {
        val group = selectedGroup ?: return
        scope.launch {
            try {
                val csv = repository.exportCsv(group.id)
                val filename = "${safeFilename(group.name)}.csv"
                if (share) shareTextFile(filename, csv, "text/csv;charset=utf-8")
                else downloadText(filename, csv, "text/csv;charset=utf-8")
            } catch (error: Throwable) {
                notice = "CSVを書き出せませんでした: ${error.message}"
            }
        }
    }

    fun exportStudyArchive() {
        scope.launch {
            runBusy("学習記録ZIPを作成しています…") {
                archiveService.export()
                notice = "学習記録ZIPを書き出しました。"
            }
        }
    }

    fun importStudyArchive() {
        pickStudyArchive { entriesJson, error ->
            if (error.isNotBlank()) {
                notice = "学習記録ZIPを開けませんでした: $error"
                return@pickStudyArchive
            }
            scope.launch {
                runBusy("学習記録ZIPを検証しています…") {
                    val (merged, added) = archiveService.import(entriesJson)
                    notice = "学習記録を読み込みました（統合${merged}冊・追加${added}冊）。"
                }
            }
        }
    }

    fun renameGroup(name: String, language: String) {
        val group = selectedGroup ?: return
        if (name.isBlank()) return
        scope.launch {
            try {
                val updated = group.copy(name = name.trim().take(200), language = StudyLanguage.normalize(language))
                repository.updateGroup(updated)
                selectedGroup = updated
                notice = "単語帳情報を更新しました。"
            } catch (error: Throwable) { notice = "更新できませんでした: ${error.message}" }
        }
    }

    fun updateWord(word: WordRecord) {
        if (word.term.isBlank() || word.meaning.isBlank()) return
        scope.launch {
            try { repository.updateWord(word); notice = "単語を更新しました。" }
            catch (error: Throwable) { notice = "更新できませんでした: ${error.message}" }
        }
    }

    fun deleteSelectedGroup() {
        val group = selectedGroup ?: return
        scope.launch {
            runBusy("削除しています…") {
                repository.deleteGroup(group.id)
                selectGroup(null)
                screen = WebScreen.DASHBOARD
                notice = "「${group.name}」を削除しました。"
            }
        }
    }

    fun resetProgress() {
        val group = selectedGroup ?: return
        scope.launch {
            try { repository.resetProgress(group.id); notice = "学習記録をリセットしました。" }
            catch (error: Throwable) { notice = "リセットできませんでした: ${error.message}" }
        }
    }

    fun startStudy() {
        val source = words.let { all ->
            val ranged = if (useRange) {
                val start = (rangeStart.toIntOrNull() ?: 1).coerceAtLeast(1)
                val end = (rangeEnd.toIntOrNull() ?: all.size).coerceAtLeast(start)
                all.drop(start - 1).take(end - start + 1)
            } else all
            ranged.filter { selectedTag == "すべて" || it.tag == selectedTag }
        }
        val selected = StudyFilterMode.select(source, filterMode)
            .let { if (quizCount == 100_000) it else it.take(quizCount) }
        if (selected.isEmpty()) {
            notice = "現在の条件に一致する単語がありません。"
            return
        }
        questions = QuizQuestionFactory.create(selected, words, directionForward, multipleChoice)
        currentIndex = 0
        score = 0
        answer = ""
        checked = false
        correct = false
        screen = WebScreen.STUDY
        speakCurrent()
    }

    fun submitAnswer(value: String) {
        if (checked || value.isBlank()) return
        val question = questions.getOrNull(currentIndex) ?: return
        answer = value
        correct = AnswerNormalizer.matches(value, question.correctAnswer)
        checked = true
        if (correct) score += 1
        playTone(correct, soundVolume)
        scope.launch {
            try { repository.recordResult(question.word.id, correct) }
            catch (error: Throwable) { notice = "学習結果を保存できませんでした: ${error.message}" }
        }
    }

    fun nextQuestion() {
        if (!checked) return
        if (currentIndex + 1 >= questions.size) {
            screen = WebScreen.SUMMARY
            return
        }
        currentIndex += 1
        checked = false
        answer = ""
        correct = false
        speakCurrent()
    }

    fun stopStudy() { screen = WebScreen.DASHBOARD }

    fun speakCurrent() {
        if (!ttsEnabled) return
        val question = questions.getOrNull(currentIndex) ?: return
        val group = selectedGroup ?: return
        val language = if (question.directionForward) StudyLanguage.fromCode(group.language).speechTag else "ja-JP"
        if (language != null) speak(question.questionText, language, ttsVolume)
    }

    val learned: Int get() = words.count(StudyProgress::isLearned)
    val vague: Int get() = words.count(StudyProgress::isVague)
    val review: Int get() = words.size - learned - vague
    val currentRound: Int get() = StudyRound.current(words)
    val tags: List<String> get() = listOf("すべて") + words.map { it.tag }.filter(String::isNotBlank).distinct().sorted()

    private suspend fun runBusy(label: String, block: suspend () -> Unit) {
        isBusy = true
        bootstrapProgress = label
        try { block() }
        catch (error: Throwable) { notice = error.message ?: "処理に失敗しました。" }
        finally { isBusy = false }
    }

    private fun setCurrentGroupSetting(key: String, value: String) {
        selectedGroup?.let { setSetting("group.${it.id}.$key", value) }
    }
}

private fun settingBoolean(key: String, default: Boolean): Boolean =
    getSetting(key)?.toBooleanStrictOrNull() ?: default

private fun groupSetting(groupId: Long, key: String): String? = getSetting("group.$groupId.$key")
private fun groupBoolean(groupId: Long, key: String, default: Boolean): Boolean =
    groupSetting(groupId, key)?.toBooleanStrictOrNull() ?: default

private fun safeFilename(value: String): String = value.replace(Regex("[\\/:*?\"<>|]"), "_").take(100)
