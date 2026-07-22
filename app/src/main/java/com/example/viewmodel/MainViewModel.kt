package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.service.SoundPlayer
import com.example.service.TtsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val wordDao = AppDatabase.getDatabase(application).wordDao()
    private val soundPlayer = SoundPlayer()
    private val ttsService = TtsService(application)

    private val prefs = application.getSharedPreferences("TangoProPrefs", Context.MODE_PRIVATE)

    // TTS configurations
    private val _isTtsEnabled = mutableStateOf(prefs.getBoolean("isTtsEnabled", true))
    var isTtsEnabled: Boolean
        get() = _isTtsEnabled.value
        set(value) {
            _isTtsEnabled.value = value
            prefs.edit().putBoolean("isTtsEnabled", value).apply()
        }

    // TTS Volume (0.0f - 1.0f)
    private val _ttsVolume = mutableStateOf(prefs.getFloat("ttsVolume", 1.0f))
    var ttsVolume: Float
        get() = _ttsVolume.value
        set(value) {
            _ttsVolume.value = value
            prefs.edit().putFloat("ttsVolume", value).apply()
        }

    // Sound Effect Volume (0.0f - 1.0f)
    private val _soundVolume = mutableStateOf(prefs.getFloat("soundVolume", 1.0f))
    var soundVolume: Float
        get() = _soundVolume.value
        set(value) {
            _soundVolume.value = value
            prefs.edit().putFloat("soundVolume", value).apply()
        }

    // Sound settings and preferences
    private val _soundStyle = mutableStateOf(
        try {
            SoundPlayer.SoundStyle.valueOf(prefs.getString("soundStyle", SoundPlayer.SoundStyle.PIKO.name) ?: SoundPlayer.SoundStyle.PIKO.name)
        } catch (e: Exception) {
            SoundPlayer.SoundStyle.PIKO
        }
    )
    var soundStyle: SoundPlayer.SoundStyle
        get() = _soundStyle.value
        set(value) {
            _soundStyle.value = value
            prefs.edit().putString("soundStyle", value.name).apply()
        }

    private val _selectedQuizCount = mutableStateOf(prefs.getInt("selectedQuizCount", 10))
    var selectedQuizCount: Int
        get() = _selectedQuizCount.value
        set(value) {
            _selectedQuizCount.value = value
            prefs.edit().putInt("selectedQuizCount", value).apply()
        }

    private val _darkThemeSelected = mutableStateOf(prefs.getBoolean("darkThemeSelected", false))
    var darkThemeSelected: Boolean
        get() = _darkThemeSelected.value
        set(value) {
            _darkThemeSelected.value = value
            prefs.edit().putBoolean("darkThemeSelected", value).apply()
        }

    private val _studyDirectionForward = mutableStateOf(prefs.getBoolean("studyDirectionForward", true))
    var studyDirectionForward: Boolean
        get() = if (!studyMultipleChoice) false else _studyDirectionForward.value
        set(value) {
            val finalVal = if (!studyMultipleChoice) false else value
            _studyDirectionForward.value = finalVal
            prefs.edit().putBoolean("studyDirectionForward", finalVal).apply()
        }

    private val _studyMultipleChoice = mutableStateOf(prefs.getBoolean("studyMultipleChoice", true))
    var studyMultipleChoice: Boolean
        get() = _studyMultipleChoice.value
        set(value) {
            _studyMultipleChoice.value = value
            prefs.edit().putBoolean("studyMultipleChoice", value).apply()
            if (!value) {
                // If typing, force Japanese -> English direction
                studyDirectionForward = false
            }
        }

    private val _filterMode = mutableStateOf(prefs.getString("filterMode", "all") ?: "all")
    var filterMode: String
        get() = if (_filterMode.value == "range") "all" else _filterMode.value
        set(value) {
            _filterMode.value = value
            prefs.edit().putString("filterMode", value).apply()
        }

    private val _selectedTagConstraint = mutableStateOf(prefs.getString("selectedTagConstraint", "すべて") ?: "すべて")
    var selectedTagConstraint: String
        get() = _selectedTagConstraint.value
        set(value) {
            _selectedTagConstraint.value = value
            prefs.edit().putString("selectedTagConstraint", value).apply()
        }

    private val _rangeStart = mutableStateOf(prefs.getInt("rangeStart", 1))
    var rangeStart: Int
        get() = _rangeStart.value
        set(value) {
            _rangeStart.value = value
            prefs.edit().putInt("rangeStart", value).apply()
        }

    private val _rangeEnd = mutableStateOf(prefs.getInt("rangeEnd", -1))
    var rangeEnd: Int
        get() = _rangeEnd.value
        set(value) {
            _rangeEnd.value = value
            prefs.edit().putInt("rangeEnd", value).apply()
        }

    private val _useRangeConstraint = mutableStateOf(prefs.getBoolean("useRangeConstraint", false))
    var useRangeConstraint: Boolean
        get() = _useRangeConstraint.value
        set(value) {
            _useRangeConstraint.value = value
            prefs.edit().putBoolean("useRangeConstraint", value).apply()
        }

    // Active screen selection or group list
    val groups: Flow<List<StudyGroup>> = wordDao.getGroups()

    private val _selectedGroup = MutableStateFlow<StudyGroup?>(null)
    val selectedGroup: StateFlow<StudyGroup?> = _selectedGroup.asStateFlow()

    // Words associated with the selected group for stats/display
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedGroupWords: StateFlow<List<Word>> = _selectedGroup
        .flatMapLatest { group ->
            if (group != null) {
                wordDao.getWords(group.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Loading states
    var isImporting by mutableStateOf(false)
    var isConcatenating by mutableStateOf(false)

    // --- Active Study Session States ---
    var isActiveSession by mutableStateOf(false)
    var sessionQuestions by mutableStateOf<List<StudyQuestion>>(emptyList())
    var currentIndex by mutableStateOf(0)
    var currentQuestion by mutableStateOf<StudyQuestion?>(null)
    var userAnswerText by mutableStateOf("")
    var hasCheckedAnswer by mutableStateOf(false)
    var isAnswerCorrect by mutableStateOf(false)
    var sessionResults by mutableStateOf<List<QuizResult>>(emptyList())
    var sessionScore by mutableStateOf(0)
    var isLoadingSession by mutableStateOf(false)

    fun changeSelectedGroup(group: StudyGroup?) {
        _selectedGroup.value = group
        if (group != null) {
            prefs.edit().putLong("lastSelectedGroupId", group.id).apply()
        } else {
            prefs.edit().remove("lastSelectedGroupId").apply()
        }
    }

    init {
        // Restore last selected group or auto-select first group if available
        viewModelScope.launch {
            val savedId = prefs.getLong("lastSelectedGroupId", -1L)
            var restored = false
            if (savedId != -1L) {
                val list = groups.firstOrNull() ?: emptyList()
                val matched = list.find { it.id == savedId }
                if (matched != null) {
                    _selectedGroup.value = matched
                    restored = true
                }
            }
            if (!restored) {
                groups.firstOrNull()?.firstOrNull()?.let { firstGroup ->
                    changeSelectedGroup(firstGroup)
                }
            }
        }
    }

    fun selectGroup(group: StudyGroup) {
        changeSelectedGroup(group)
        // If an active session is running, close it
        isActiveSession = false
    }

    // --- CSV Importing Operations ---
    fun importCsvFromUri(context: Context, uri: Uri, customName: String, language: String = "en", onComplete: (Boolean, String) -> Unit) {
        isImporting = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    withContext(Dispatchers.Main) {
                        isImporting = false
                        onComplete(false, "ファイルを開けませんでした。")
                    }
                    return@launch
                }

                val wordsToInsert = mutableListOf<Word>()
                val name = customName.ifBlank() { "インポート_${System.currentTimeMillis() % 1000}" }

                val groupId = wordDao.insertGroup(StudyGroup(name = name, language = language))

                var readCount = 0
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        if (line.isNotBlank()) {
                            // Support standard word separator e.g., English,Japanese,Tag
                            val parts = line.split(",")
                            if (parts.size >= 2) {
                                val eng = parts[0].trim()
                                val jpn = parts[1].trim()
                                val tag = if (parts.size >= 3) parts[2].trim() else ""
                                val pron = if (parts.size >= 4) parts[3].trim() else ""
                                if (eng.isNotEmpty() && jpn.isNotEmpty()) {
                                    wordsToInsert.add(
                                        Word(
                                            groupId = groupId,
                                            english = eng,
                                            japanese = jpn,
                                            tag = tag,
                                            pronunciation = pron
                                        )
                                    )
                                    readCount++
                                }
                            }
                        }

                        // Save memory. Insert in sub-batches
                        if (wordsToInsert.size >= 5000) {
                            wordDao.insertWords(wordsToInsert.toList())
                            wordsToInsert.clear()
                        }
                        line = reader.readLine()
                    }
                }

                if (wordsToInsert.isNotEmpty()) {
                    wordDao.insertWords(wordsToInsert)
                }

                if (readCount == 0) {
                    wordDao.deleteGroupAndWords(groupId)
                    withContext(Dispatchers.Main) {
                        isImporting = false
                        onComplete(false, "有効な単語データが見つかりませんでした。")
                    }
                    return@launch
                }

                val newGroup = wordDao.getGroupById(groupId)
                withContext(Dispatchers.Main) {
                    isImporting = false
                    if (newGroup != null) {
                        changeSelectedGroup(newGroup)
                        onComplete(true, "${readCount}個の単語をインポートしました！")
                    } else {
                        onComplete(false, "グループの作成に失敗しました。")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isImporting = false
                    onComplete(false, "エラー: ${e.localizedMessage ?: "読み込み失敗"}")
                }
            }
        }
    }

    fun importCsvFromText(customName: String, rawText: String, language: String = "en", onComplete: (Boolean, String) -> Unit) {
        if (rawText.isBlank()) {
            onComplete(false, "テキストが空です。")
            return
        }
        isImporting = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val name = customName.ifBlank() { "貼付インポート_${System.currentTimeMillis() % 1000}" }
                val groupId = wordDao.insertGroup(StudyGroup(name = name, language = language))
                val wordsToInsert = mutableListOf<Word>()
                var readCount = 0

                rawText.lineSequence().forEach { line ->
                    if (line.isNotBlank()) {
                        val parts = line.split(",")
                        if (parts.size >= 2) {
                            val eng = parts[0].trim()
                            val jpn = parts[1].trim()
                            val tag = if (parts.size >= 3) parts[2].trim() else ""
                            val pron = if (parts.size >= 4) parts[3].trim() else ""
                            if (eng.isNotEmpty() && jpn.isNotEmpty()) {
                                wordsToInsert.add(
                                    Word(
                                        groupId = groupId,
                                        english = eng,
                                        japanese = jpn,
                                        tag = tag,
                                        pronunciation = pron
                                    )
                                )
                                readCount++
                            }
                        }
                    }
                    if (wordsToInsert.size >= 5000) {
                        wordDao.insertWords(wordsToInsert.toList())
                        wordsToInsert.clear()
                    }
                }

                if (wordsToInsert.isNotEmpty()) {
                    wordDao.insertWords(wordsToInsert)
                }

                if (readCount == 0) {
                    wordDao.deleteGroupAndWords(groupId)
                    withContext(Dispatchers.Main) {
                        isImporting = false
                        onComplete(false, "有効な単語データが見つかりませんでした。")
                    }
                    return@launch
                }

                val newGroup = wordDao.getGroupById(groupId)
                withContext(Dispatchers.Main) {
                    isImporting = false
                    if (newGroup != null) {
                        changeSelectedGroup(newGroup)
                        onComplete(true, "${readCount}個の単語をインポートしました！")
                    } else {
                        onComplete(false, "グループの作成に失敗しました。")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isImporting = false
                    onComplete(false, "エラー: ${e.localizedMessage ?: "処理失敗"}")
                }
            }
        }
    }

    fun updateGroupLanguage(group: StudyGroup, newLanguage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedGroup = group.copy(language = newLanguage)
            wordDao.updateGroup(updatedGroup)
            if (_selectedGroup.value?.id == group.id) {
                withContext(Dispatchers.Main) {
                    changeSelectedGroup(updatedGroup)
                }
            }
        }
    }

    // --- Delete and Reset ---
    fun deleteGroupAndWords(groupId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            wordDao.deleteGroupAndWords(groupId)
            withContext(Dispatchers.Main) {
                // Return to first available group, or null
                val groupsList = groups.firstOrNull() ?: emptyList()
                val nextGroup = groupsList.firstOrNull { it.id != groupId }
                changeSelectedGroup(nextGroup)
            }
        }
    }

    fun resetGroupProgress(groupId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            wordDao.resetGroupProgress(groupId)
        }
    }

    // --- Concatenate CSV CSV連結 ---
    fun combineGroups(combinedName: String, selectedIds: List<Long>, language: String = "en", onComplete: (Boolean, String) -> Unit) {
        if (selectedIds.isEmpty()) {
            onComplete(false, "連結対象を選択してください。")
            return
        }
        val finalName = combinedName.ifBlank() { "連結単語帳" }
        isConcatenating = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newGroupId = wordDao.insertGroup(StudyGroup(name = finalName, language = language))
                val wordsToInsert = mutableListOf<Word>()
                var totalWordsCount = 0

                for (gId in selectedIds) {
                    val words = wordDao.getWordsDirect(gId)
                    for (w in words) {
                        // Note: Shares performance statistics according to spec ("成績を共有したり")
                        wordsToInsert.add(
                            Word(
                                groupId = newGroupId,
                                english = w.english,
                                japanese = w.japanese,
                                tag = w.tag,
                                studyCount = w.studyCount,
                                isCorrectLast = w.isCorrectLast,
                                lastStudiedAt = w.lastStudiedAt
                            )
                        )
                        totalWordsCount++
                    }

                    if (wordsToInsert.size >= 5000) {
                        wordDao.insertWords(wordsToInsert.toList())
                        wordsToInsert.clear()
                    }
                }

                if (wordsToInsert.isNotEmpty()) {
                    wordDao.insertWords(wordsToInsert)
                }

                if (totalWordsCount == 0) {
                    wordDao.deleteGroupAndWords(newGroupId)
                    withContext(Dispatchers.Main) {
                        isConcatenating = false
                        onComplete(false, "有効な単語データが見つかりませんでした。")
                    }
                    return@launch
                }

                val newGroup = wordDao.getGroupById(newGroupId)
                withContext(Dispatchers.Main) {
                    isConcatenating = false
                    if (newGroup != null) {
                        changeSelectedGroup(newGroup)
                        onComplete(true, "${selectedIds.size}個のCSVを連結し、成績を共有した新しいCSV「$finalName」を作成しました（合計${totalWordsCount}単語）。")
                    } else {
                        onComplete(false, "連結データの作成に失敗しました。")
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isConcatenating = false
                    onComplete(false, "エラー: ${e.localizedMessage}")
                }
            }
        }
    }

    // --- STUDY SESSION CONTROLLER ---
    fun startStudySession(
        group: StudyGroup,
        directionForward: Boolean, // true = Eng -> Jpn (順方向), false = Jpn -> Eng (逆方向)
        isMultipleChoice: Boolean, // true = 4択, false = タイピング
        filterMode: String,        // "all", "unstudied", "incorrect", "learned_once", "weak", "learned_random"
        useRangeConstraint: Boolean = false, // Apply range/tag constraints
        selectedTag: String = "",       // Category constraint
        rangeStart: Int = 1,            // Range constraint (1-based index)
        rangeEnd: Int = 100             // Range constraint (1-based index)
    ) {
        isLoadingSession = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allWords = wordDao.getWordsDirect(group.id)
                if (allWords.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isLoadingSession = false
                    }
                    return@launch
                }

                var filtered = allWords

                // Apply specific performance categorization flags FIRST
                filtered = when (filterMode) {
                    "unstudied" -> filtered.filter { it.studyCount == 0 }
                    "incorrect" -> filtered.filter { it.studyCount > 0 && !it.isCorrectLast }
                    "learned_once" -> filtered.filter { it.studyCount == 1 }
                    "weak" -> filtered.filter { it.studyCount == 1 || (it.studyCount > 0 && !it.isCorrectLast) }
                    "learned_random" -> filtered.filter { it.studyCount > 0 }.shuffled()
                    else -> filtered // "all"
                }

                // If range / tag constraints are enabled, filter them NEXT
                if (useRangeConstraint) {
                    if (selectedTag.isNotBlank() && selectedTag != "すべて") {
                        filtered = filtered.filter { it.tag.equals(selectedTag, ignoreCase = true) }
                    }
                    val s = maxOf(0, rangeStart - 1)
                    val e = minOf(filtered.size, rangeEnd)
                    if (s < e) {
                        filtered = filtered.subList(s, e)
                    } else {
                        filtered = emptyList()
                    }
                }

                // Unless we run sequential all in range or recommend, shuffle questions for active recall
                val shouldShuffle = if (useRangeConstraint && filterMode == "all") {
                    false
                } else {
                    filterMode != "learned_random" && filterMode != "recommend"
                }

                if (shouldShuffle) {
                    filtered = filtered.shuffled()
                }

                // Apply limit matching setting (selectedQuizCount)
                val limit = if (selectedQuizCount == 100000) filtered.size else selectedQuizCount
                
                val sessionWords = if (filterMode == "recommend") {
                    val selected = mutableListOf<Word>()
                    val remaining = mutableListOf<Word>()
                    val random = java.util.Random()
                    for (word in filtered) {
                        val statusWeight = when {
                            // 未学習 = 間違えた問題
                            word.studyCount == 0 || (word.studyCount > 0 && !word.isCorrectLast) -> 0.85
                            // うろ覚え
                            word.studyCount == 1 -> 0.45
                            // 学習済み
                            else -> 0.15
                        }
                        if (random.nextDouble() < statusWeight) {
                            selected.add(word)
                        } else {
                            remaining.add(word)
                        }
                    }
                    if (selected.size >= limit) {
                        selected.take(limit)
                    } else {
                        selected + remaining.take(limit - selected.size)
                    }
                } else {
                    filtered.take(limit)
                }

                if (sessionWords.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isLoadingSession = false
                        // Let screen show that no words matched this state
                        sessionQuestions = emptyList()
                        isActiveSession = true
                        currentQuestion = null
                    }
                    return@launch
                }

                val questions = sessionWords.map { word ->
                    val questionText = if (directionForward) word.english else word.japanese
                    val correctAns = if (directionForward) word.japanese else word.english

                    val choices = if (isMultipleChoice) {
                        // Take wrong answers: other distinct translations in the database for the SAME group
                        val otherAnswers = allWords
                            .filter { it.id != word.id }
                            .map { if (directionForward) it.japanese else it.english }
                            .distinct()
                            .shuffled()

                        val finalChoices = mutableListOf<String>()
                        finalChoices.add(correctAns)
                        if (otherAnswers.size >= 3) {
                            finalChoices.addAll(otherAnswers.take(3))
                        } else {
                            // Fallback dummy choices matched to output language
                            val fallbacks = if (directionForward) {
                                listOf("りんご", "本", "走る", "犬", "机", "山", "海", "学校", "花", "車")
                            } else {
                                listOf("apple", "book", "run", "dog", "desk", "mountain", "sea", "school", "flower", "car")
                            }
                            finalChoices.addAll(fallbacks.filter { it != correctAns }.shuffled().take(3 - otherAnswers.size))
                        }
                        finalChoices.shuffled()
                    } else {
                        emptyList()
                    }

                    StudyQuestion(
                        word = word,
                        questionText = questionText,
                        correctAnswer = correctAns,
                        choices = choices,
                        directionForward = directionForward,
                        isMultipleChoice = isMultipleChoice
                    )
                }

                withContext(Dispatchers.Main) {
                    sessionQuestions = questions
                    currentIndex = 0
                    currentQuestion = questions.firstOrNull()
                    sessionScore = 0
                    sessionResults = emptyList()
                    isActiveSession = true
                    hasCheckedAnswer = false
                    isLoadingSession = false
                    userAnswerText = ""
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isLoadingSession = false
                }
            }
        }
    }

    fun submitAnswer(ans: String) {
        val q = currentQuestion ?: return
        if (hasCheckedAnswer) return

        userAnswerText = ans.trim()
        val isCorrect = if (q.isMultipleChoice) {
            ans.trim() == q.correctAnswer.trim()
        } else {
            // Typing mode: match ignoring case and full/half width variations for english typing
            ans.trim().equals(q.correctAnswer.trim(), ignoreCase = true)
        }

        isAnswerCorrect = isCorrect
        hasCheckedAnswer = true

        if (isCorrect) {
            sessionScore++
        }

        // Play feedback tone
        viewModelScope.launch {
            if (isCorrect) {
                soundPlayer.playCorrect(soundStyle, soundVolume)
            } else {
                soundPlayer.playIncorrect(soundStyle, soundVolume)
            }
        }

        // Commit progress directly in SQLite asynchronously
        viewModelScope.launch(Dispatchers.IO) {
            val updatedWord = q.word.copy(
                studyCount = q.word.studyCount + 1,
                isCorrectLast = isCorrect,
                lastStudiedAt = System.currentTimeMillis()
            )
            wordDao.updateWord(updatedWord)
        }

        // Add to result summary lists
        val result = QuizResult(
            questionText = q.questionText,
            correctAnswer = q.correctAnswer,
            userAnswer = userAnswerText.ifBlank { "(未入力)" },
            isCorrect = isCorrect,
            wordId = q.word.id
        )
        sessionResults = sessionResults + result

        // Speak the correct target word upon submission if it wasn't spoken beforehand (e.g. JA -> Target)
        val lang = _selectedGroup.value?.language ?: "en"
        if (isTtsEnabled && lang != "none") {
            if (lang == "zh") {
                if (!q.directionForward) {
                    ttsService.speak(q.correctAnswer, ttsVolume, "zh")
                }
            } else {
                if (!isEnglishString(q.questionText) && isEnglishString(q.correctAnswer)) {
                    ttsService.speak(q.correctAnswer, ttsVolume, "en")
                }
            }
        }
    }

    fun nextQuestion() {
        if (!hasCheckedAnswer) return
        val nextIdx = currentIndex + 1
        if (nextIdx < sessionQuestions.size) {
            currentIndex = nextIdx
            currentQuestion = sessionQuestions[nextIdx]
            hasCheckedAnswer = false
            userAnswerText = ""
        } else {
            // Complete session
            currentIndex = sessionQuestions.size
            currentQuestion = null
        }
    }

    fun exitStudySession() {
        isActiveSession = false
        sessionQuestions = emptyList()
        currentQuestion = null
        hasCheckedAnswer = false
    }

    // --- TEXT TO SPEECH CONTROLS ---

    fun speakCurrentQuestionIfNeeded() {
        if (!isTtsEnabled) return
        val q = currentQuestion ?: return
        if (hasCheckedAnswer) return
        
        val lang = _selectedGroup.value?.language ?: "en"
        if (lang == "none") return
        
        if (lang == "zh") {
            if (q.directionForward) {
                ttsService.speak(q.questionText, ttsVolume, "zh")
            }
        } else {
            if (isEnglishString(q.questionText)) {
                ttsService.speak(q.questionText, ttsVolume, "en")
            }
        }
    }

    fun speakCurrentQuestionManual() {
        val q = currentQuestion ?: return
        val lang = _selectedGroup.value?.language ?: "en"
        if (lang == "none") return
        
        if (lang == "zh") {
            ttsService.speak(q.word.english, ttsVolume, "zh")
        } else {
            if (isEnglishString(q.questionText)) {
                ttsService.speak(q.questionText, ttsVolume, "en")
            } else if (isEnglishString(q.correctAnswer)) {
                ttsService.speak(q.correctAnswer, ttsVolume, "en")
            } else {
                ttsService.speak(q.word.english, ttsVolume, "en")
            }
        }
    }

    fun isEnglishString(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val hasLetter = trimmed.any { it in 'a'..'z' || it in 'A'..'Z' }
        val hasJapanese = trimmed.any { it.code in 0x3040..0x309F || it.code in 0x30A0..0x30FF || it.code in 0x4E00..0x9FFF }
        return hasLetter && !hasJapanese
    }

    override fun onCleared() {
        super.onCleared()
        ttsService.stop()
        ttsService.shutdown()
    }
}
