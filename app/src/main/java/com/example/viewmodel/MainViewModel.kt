package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.BuildConfig
import com.example.data.*
import com.squareup.moshi.Moshi
import com.example.domain.AnswerNormalizer
import com.example.domain.BundledGroupCatalog
import com.example.domain.QuizQuestionFactory
import com.example.domain.StudyArrangementMode
import com.example.domain.StudyFilterMode
import com.example.domain.StudyLanguage
import com.example.domain.StudySettings
import com.example.service.SoundPlayer
import com.example.service.TtsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.Reader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val QUIZ_COUNT_ALL = StudySettings.QUIZ_COUNT_ALL
        val QUIZ_TEXT_SCALES = listOf(0.8f, 1f, 1.2f, 1.4f)
    }

    private val database = AppDatabase.getDatabase(application)
    private val wordDao = database.wordDao()
    private val studyArchiveService = StudyArchiveService(database, wordDao)
    private val soundPlayer = SoundPlayer()
    private val ttsService = TtsService(application)

    private val prefs = application.getSharedPreferences("TangoProPrefs", Context.MODE_PRIVATE)
    private val studySettingsPreferences = StudySettingsPreferences(prefs)

    // TTS configurations
    private val _isTtsEnabled = mutableStateOf(prefs.getBoolean("isTtsEnabled", true))
    var isTtsEnabled: Boolean
        get() = _isTtsEnabled.value
        set(value) {
            _isTtsEnabled.value = value
            prefs.edit { putBoolean("isTtsEnabled", value) }
        }

    // TTS Volume (0.0f - 1.0f)
    private val _ttsVolume = mutableStateOf(prefs.getFloat("ttsVolume", 1.0f).coerceIn(0f, 1f))
    var ttsVolume: Float
        get() = _ttsVolume.value
        set(value) {
            val normalized = value.coerceIn(0f, 1f)
            _ttsVolume.value = normalized
            prefs.edit { putFloat("ttsVolume", normalized) }
        }

    // Sound Effect Volume (0.0f - 1.0f)
    private val _soundVolume = mutableStateOf(prefs.getFloat("soundVolume", 1.0f).coerceIn(0f, 1f))
    var soundVolume: Float
        get() = _soundVolume.value
        set(value) {
            val normalized = value.coerceIn(0f, 1f)
            _soundVolume.value = normalized
            prefs.edit { putFloat("soundVolume", normalized) }
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
            prefs.edit { putString("soundStyle", value.name) }
        }

    private val legacyStudySettings = StudySettings.normalize(
        StudySettings(
            directionForward = prefs.getBoolean("studyDirectionForward", true),
            multipleChoice = prefs.getBoolean("studyMultipleChoice", true),
            filterMode = prefs.getString("filterMode", StudyFilterMode.RECOMMEND) ?: StudyFilterMode.RECOMMEND,
            selectedTag = prefs.getString("selectedTagConstraint", "すべて") ?: "すべて",
            rangeStart = prefs.getInt("rangeStart", 1),
            rangeEnd = prefs.getInt("rangeEnd", -1),
            useRangeConstraint = prefs.getBoolean("useRangeConstraint", false),
            quizCount = prefs.getInt("selectedQuizCount", StudySettings.DEFAULT_QUIZ_COUNT)
        )
    )

    private val _selectedQuizCount = mutableStateOf(legacyStudySettings.quizCount)
    var selectedQuizCount: Int
        get() = _selectedQuizCount.value
        set(value) {
            val normalized = value.takeIf { it in StudySettings.allowedQuizCounts } ?: StudySettings.DEFAULT_QUIZ_COUNT
            _selectedQuizCount.value = normalized
            persistSelectedStudySettings()
        }

    private val _darkThemeSelected = mutableStateOf(prefs.getBoolean("darkThemeSelected", false))
    var darkThemeSelected: Boolean
        get() = _darkThemeSelected.value
        set(value) {
            _darkThemeSelected.value = value
            prefs.edit { putBoolean("darkThemeSelected", value) }
        }

    private val _simpleModeEnabled = mutableStateOf(prefs.getBoolean("simpleModeEnabled", false))
    var simpleModeEnabled: Boolean
        get() = _simpleModeEnabled.value
        set(value) {
            _simpleModeEnabled.value = value
            prefs.edit { putBoolean("simpleModeEnabled", value) }
        }

    private val _quizTextScale = mutableStateOf(
        normalizeQuizTextScale(prefs.getFloat("quizTextScale", 1f))
    )
    var quizTextScale: Float
        get() = _quizTextScale.value
        set(value) {
            val normalized = normalizeQuizTextScale(value)
            _quizTextScale.value = normalized
            prefs.edit { putFloat("quizTextScale", normalized) }
        }

    private fun normalizeQuizTextScale(value: Float): Float =
        QUIZ_TEXT_SCALES.minBy { kotlin.math.abs(it - value) }

    private val _quizArrangementMode = mutableStateOf(
        StudyArrangementMode.normalize(prefs.getString("quizArrangementMode", null))
    )
    var quizArrangementMode: String
        get() = _quizArrangementMode.value
        set(value) {
            val normalized = StudyArrangementMode.normalize(value)
            _quizArrangementMode.value = normalized
            prefs.edit { putString("quizArrangementMode", normalized) }
        }

    private val _studyDirectionForward = mutableStateOf(legacyStudySettings.directionForward)
    var studyDirectionForward: Boolean
        get() = if (!studyMultipleChoice) false else _studyDirectionForward.value
        set(value) {
            val finalVal = if (!studyMultipleChoice) false else value
            _studyDirectionForward.value = finalVal
            persistSelectedStudySettings()
        }

    private val _studyMultipleChoice = mutableStateOf(legacyStudySettings.multipleChoice)
    var studyMultipleChoice: Boolean
        get() = _studyMultipleChoice.value
        set(value) {
            _studyMultipleChoice.value = value
            if (!value) {
                // Typing always asks Japanese -> target language.
                _studyDirectionForward.value = false
            }
            persistSelectedStudySettings()
        }

    private val _filterMode = mutableStateOf(
        legacyStudySettings.filterMode
    )
    var filterMode: String
        get() = _filterMode.value
        set(value) {
            val normalized = StudyFilterMode.normalize(value)
            _filterMode.value = normalized
            persistSelectedStudySettings()
        }

    private val _selectedTagConstraint = mutableStateOf(legacyStudySettings.selectedTag)
    var selectedTagConstraint: String
        get() = _selectedTagConstraint.value
        set(value) {
            _selectedTagConstraint.value = value.ifBlank { "すべて" }
            persistSelectedStudySettings()
        }

    private val _rangeStart = mutableStateOf(legacyStudySettings.rangeStart)
    var rangeStart: Int
        get() = _rangeStart.value
        set(value) {
            _rangeStart.value = value.coerceAtLeast(1)
            persistSelectedStudySettings()
        }

    private val _rangeEnd = mutableStateOf(legacyStudySettings.rangeEnd)
    var rangeEnd: Int
        get() = _rangeEnd.value
        set(value) {
            _rangeEnd.value = value.takeIf { it == -1 || it >= 1 } ?: -1
            persistSelectedStudySettings()
        }

    private val _useRangeConstraint = mutableStateOf(legacyStudySettings.useRangeConstraint)
    var useRangeConstraint: Boolean
        get() = _useRangeConstraint.value
        set(value) {
            _useRangeConstraint.value = value
            persistSelectedStudySettings()
        }

    // Active screen selection or group list
    val groups: Flow<List<StudyGroup>> = wordDao.getGroups()
    val groupRounds: Flow<Map<Long, Int>> = wordDao.getGroupRoundProgress().map { rows ->
        rows.associate { it.groupId to (it.minimumStudyCount.coerceAtLeast(0) + 1) }
    }

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

    private data class StudySessionRequest(
        val group: StudyGroup,
        val directionForward: Boolean,
        val isMultipleChoice: Boolean,
        val filterMode: String,
        val useRangeConstraint: Boolean,
        val selectedTag: String,
        val rangeStart: Int,
        val rangeEnd: Int,
        val quizCount: Int
    )

    private var lastStudySessionRequest: StudySessionRequest? = null

    var pendingImportUri by mutableStateOf<Uri?>(null)

    fun changeSelectedGroup(group: StudyGroup?) {
        _selectedGroup.value = group
        if (group != null) {
            applyStudySettings(studySettingsPreferences.load(group.id, legacyStudySettings))
            prefs.edit { putLong("lastSelectedGroupId", group.id) }
        } else {
            prefs.edit { remove("lastSelectedGroupId") }
        }
    }

    private fun currentStudySettings() = StudySettings.normalize(
        StudySettings(
            directionForward = _studyDirectionForward.value,
            multipleChoice = _studyMultipleChoice.value,
            filterMode = _filterMode.value,
            selectedTag = _selectedTagConstraint.value,
            rangeStart = _rangeStart.value,
            rangeEnd = _rangeEnd.value,
            useRangeConstraint = _useRangeConstraint.value,
            quizCount = _selectedQuizCount.value
        )
    )

    private fun applyStudySettings(settings: StudySettings) {
        val normalized = StudySettings.normalize(settings)
        _studyDirectionForward.value = normalized.directionForward
        _studyMultipleChoice.value = normalized.multipleChoice
        _filterMode.value = normalized.filterMode
        _selectedTagConstraint.value = normalized.selectedTag
        _rangeStart.value = normalized.rangeStart
        _rangeEnd.value = normalized.rangeEnd
        _useRangeConstraint.value = normalized.useRangeConstraint
        _selectedQuizCount.value = normalized.quizCount
    }

    private fun persistSelectedStudySettings() {
        _selectedGroup.value?.id?.let { studySettingsPreferences.save(it, currentStudySettings()) }
    }

    init {
        // Persist migration from removed or unknown filter identifiers.
        if (prefs.getString("filterMode", null) != filterMode) {
            prefs.edit { putString("filterMode", filterMode) }
        }

        // Import only missing bundled groups. This runs on every launch so a failed
        // asset import is retried instead of leaving the database partially initialized.
        viewModelScope.launch {
            val existingNames = wordDao.getGroupsDirect().mapTo(mutableSetOf()) { it.name }
            val legacyBootstrapCompleted = prefs.getBoolean("preloadedData", false)
            for (spec in BundledGroupCatalog.all) {
                val alreadyProcessed = prefs.getBoolean(spec.preferenceKey, false) ||
                    (legacyBootstrapCompleted && spec.includedInLegacyBootstrap)
                if (alreadyProcessed) continue

                if (spec.displayName in existingNames) {
                    prefs.edit { putBoolean(spec.preferenceKey, true) }
                    continue
                }
                try {
                    importCsvFromAsset(
                        getApplication(),
                        spec.assetFileName,
                        spec.displayName,
                        spec.language
                    )
                    existingNames.add(spec.displayName)
                    prefs.edit { putBoolean(spec.preferenceKey, true) }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Bundled CSV import failed: ${spec.assetFileName}", e)
                }
            }

            normalizeGroupSortOrders()

            val savedId = prefs.getLong("lastSelectedGroupId", -1L)
            val availableGroups = wordDao.getGroupsDirect()
            val initialGroup = availableGroups.find { it.id == savedId }
                ?: availableGroups.firstOrNull()
            if (initialGroup != null) {
                changeSelectedGroup(initialGroup)
            }
        }
    }

    private suspend fun normalizeGroupSortOrders() {
        val ordered = wordDao.getGroupsDirect()
        val normalized = ordered.mapIndexedNotNull { index, group ->
            group.takeIf { it.sortOrder != index }?.copy(sortOrder = index)
        }
        if (normalized.isNotEmpty()) wordDao.updateGroups(normalized)
    }

    private fun generatedGroupName(prefix: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(Date())
        return "${prefix}_$timestamp"
    }

    private data class ImportedWord(
        val english: String,
        val japanese: String,
        val tag: String,
        val pronunciation: String
    )

    private fun parseImportedWords(reader: Reader): List<ImportedWord> =
        CsvParser.parse(reader, maxRecords = 100_000).mapNotNull { fields ->
            if (fields.size < 2) return@mapNotNull null
            val english = fields[0].trim()
            val japanese = fields[1].trim()
            if (english.isEmpty() || japanese.isEmpty()) return@mapNotNull null
            ImportedWord(
                english = english,
                japanese = japanese,
                tag = fields.getOrElse(2) { "" }.trim(),
                pronunciation = fields.getOrElse(3) { "" }.trim()
            )
        }

    private suspend fun insertImportedGroup(
        name: String,
        language: String,
        importedWords: List<ImportedWord>,
        sortOrder: Int? = null
    ): Long = database.withTransaction {
        val resolvedSortOrder = sortOrder
            ?: ((wordDao.getGroupsDirect().minOfOrNull { it.sortOrder } ?: 1) - 1)
        val groupId = wordDao.insertGroup(
            StudyGroup(name = name, language = StudyLanguage.normalize(language), sortOrder = resolvedSortOrder)
        )
        importedWords.chunked(5_000).forEach { chunk ->
            wordDao.insertWords(
                chunk.map { imported ->
                    Word(
                        groupId = groupId,
                        english = imported.english,
                        japanese = imported.japanese,
                        tag = imported.tag,
                        pronunciation = imported.pronunciation
                    )
                }
            )
        }
        groupId
    }

    private suspend fun importCsvFromAsset(context: Context, filename: String, groupName: String, language: String) {
        withContext(Dispatchers.IO) {
            val importedWords = context.assets.open(filename)
                .bufferedReader(Charsets.UTF_8)
                .use(::parseImportedWords)
            if (importedWords.isEmpty()) return@withContext
            val currentGroups = wordDao.getGroupsDirect()
            val newSortOrder = (currentGroups.maxOfOrNull { it.sortOrder } ?: 0) + 1
            insertImportedGroup(groupName, language, importedWords, newSortOrder)
        }
    }



    fun moveGroupUp(group: StudyGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentGroups = wordDao.getGroupsDirect()
            val index = currentGroups.indexOfFirst { it.id == group.id }
            if (index > 0) {
                val above = currentGroups[index - 1]
                val newGroup = group.copy(sortOrder = above.sortOrder)
                val newAbove = above.copy(sortOrder = group.sortOrder)
                wordDao.updateGroups(listOf(newGroup, newAbove))
            }
        }
    }

    fun moveGroupDown(group: StudyGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentGroups = wordDao.getGroupsDirect()
            val index = currentGroups.indexOfFirst { it.id == group.id }
            if (index >= 0 && index < currentGroups.size - 1) {
                val below = currentGroups[index + 1]
                val newGroup = group.copy(sortOrder = below.sortOrder)
                val newBelow = below.copy(sortOrder = group.sortOrder)
                wordDao.updateGroups(listOf(newGroup, newBelow))
            }
        }
    }

    fun selectGroup(group: StudyGroup) {
        changeSelectedGroup(group)
        // If an active session is running, close it
        isActiveSession = false
    }


    // --- Save Data Export / Import ---
    fun exportSaveDataJson(onComplete: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val groupsList = wordDao.getGroupsDirect()
                val exportGroups = mutableListOf<StudyGroupExport>()
                for (group in groupsList) {
                    val wordList = wordDao.getWordsDirect(group.id)
                    val exportWords = wordList.map { w ->
                        WordExport(
                            english = w.english,
                            japanese = w.japanese,
                            tag = w.tag,
                            pronunciation = w.pronunciation,
                            studyCount = w.studyCount,
                            isCorrectLast = w.isCorrectLast,
                            lastStudiedAt = w.lastStudiedAt
                        )
                    }
                    exportGroups.add(StudyGroupExport(group.name, group.language, exportWords))
                }
                val saveData = SaveDataExport(version = 1, groups = exportGroups)
                val moshi = Moshi.Builder().build()
                val jsonAdapter = moshi.adapter(SaveDataExport::class.java)
                val json = jsonAdapter.toJson(saveData)
                withContext(Dispatchers.Main) {
                    onComplete(json)
                }
            } catch (e: Exception) {
                Log.e("TangoPro", "Save-data export failed", e)
                withContext(Dispatchers.Main) {
                    onComplete(null)
                }
            }
        }
    }

    fun importSaveDataJson(context: Context, uri: Uri, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    withContext(Dispatchers.Main) { onComplete(false, "ファイルを開けませんでした。") }
                    return@launch
                }
                val json = inputStream.bufferedReader().use { it.readText() }
                val moshi = Moshi.Builder().build()
                val jsonAdapter = moshi.adapter(SaveDataExport::class.java)
                val saveData = jsonAdapter.fromJson(json)

                if (saveData == null) {
                    withContext(Dispatchers.Main) { onComplete(false, "データが不正です。") }
                    return@launch
                }

                if (saveData.version != 1) {
                    withContext(Dispatchers.Main) { onComplete(false, "未対応のセーブデータ形式です。") }
                    return@launch
                }

                database.withTransaction {
                    val currentGroups = wordDao.getGroupsDirect()
                    var newSortOrder = currentGroups.maxOfOrNull { it.sortOrder } ?: 0
                    val usedNames = currentGroups.map { it.name }.toMutableSet()
                    fun nextUniqueName(baseName: String): String {
                        if (usedNames.add(baseName)) return baseName
                        var suffix = 2
                        while (!usedNames.add("$baseName $suffix")) suffix++
                        return "$baseName $suffix"
                    }

                    for (exportedGroup in saveData.groups) {
                        val existingGroup = currentGroups.find { it.name == exportedGroup.name }
                        if (existingGroup != null) {
                            val existingWords = wordDao.getWordsDirect(existingGroup.id)
                            val existingEngSet = existingWords.map { it.english }.toSet()
                            val importedByEnglish = exportedGroup.words.associateBy { it.english }

                            if (
                                existingWords.size == existingEngSet.size &&
                                exportedGroup.words.size == importedByEnglish.size &&
                                existingEngSet == importedByEnglish.keys
                            ) {
                                val mergedWords = existingWords.map { existingWord ->
                                    val importedWord = importedByEnglish[existingWord.english]
                                        ?: return@map existingWord
                                    val importedIsNewer = importedWord.lastStudiedAt > existingWord.lastStudiedAt ||
                                        (importedWord.lastStudiedAt == existingWord.lastStudiedAt &&
                                            importedWord.studyCount > existingWord.studyCount)
                                    if (importedIsNewer) {
                                        existingWord.copy(
                                            japanese = importedWord.japanese,
                                            tag = importedWord.tag,
                                            pronunciation = importedWord.pronunciation,
                                            studyCount = importedWord.studyCount,
                                            isCorrectLast = importedWord.isCorrectLast,
                                            lastStudiedAt = importedWord.lastStudiedAt
                                        )
                                    } else {
                                        existingWord
                                    }
                                }
                                wordDao.updateWords(mergedWords)
                            } else {
                                newSortOrder++
                                val newGroupId = wordDao.insertGroup(
                                    StudyGroup(
                                        name = nextUniqueName(exportedGroup.name),
                                        language = StudyLanguage.normalize(exportedGroup.language),
                                        sortOrder = newSortOrder
                                    )
                                )
                                wordDao.insertWords(exportedGroup.words.map { word ->
                                    Word(
                                        groupId = newGroupId,
                                        english = word.english,
                                        japanese = word.japanese,
                                        tag = word.tag,
                                        pronunciation = word.pronunciation,
                                        studyCount = word.studyCount,
                                        isCorrectLast = word.isCorrectLast,
                                        lastStudiedAt = word.lastStudiedAt
                                    )
                                })
                            }
                        } else {
                            newSortOrder++
                            val newGroupId = wordDao.insertGroup(
                                StudyGroup(
                                    name = nextUniqueName(exportedGroup.name),
                                    language = StudyLanguage.normalize(exportedGroup.language),
                                    sortOrder = newSortOrder
                                )
                            )
                            wordDao.insertWords(exportedGroup.words.map { word ->
                                Word(
                                    groupId = newGroupId,
                                    english = word.english,
                                    japanese = word.japanese,
                                    tag = word.tag,
                                    pronunciation = word.pronunciation,
                                    studyCount = word.studyCount,
                                    isCorrectLast = word.isCorrectLast,
                                    lastStudiedAt = word.lastStudiedAt
                                )
                            })
                        }
                    }
                }

                withContext(Dispatchers.Main) { onComplete(true, "セーブデータをインポートしました。") }
            } catch (e: Exception) {
                Log.e("TangoPro", "Save-data import failed", e)
                withContext(Dispatchers.Main) { onComplete(false, "インポートエラー: ${e.message}") }
            }
        }
    }

    fun exportStudyArchive(context: Context, uri: Uri, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val temporary = kotlin.runCatching {
                java.io.File.createTempFile("tango-study-", ".zip", context.cacheDir)
            }.getOrNull()
            try {
                requireNotNull(temporary) { "一時ファイルを作成できませんでした。" }
                temporary.outputStream().use { output ->
                    studyArchiveService.exportTo(output, BuildConfig.VERSION_NAME)
                }
                context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    temporary.inputStream().use { input -> input.copyTo(output) }
                } ?: error("出力先を開けませんでした。")
                withContext(Dispatchers.Main) {
                    onComplete(true, "学習記録ZIPを書き出しました。")
                }
            } catch (e: Exception) {
                Log.e("TangoPro", "Study archive export failed", e)
                withContext(Dispatchers.Main) {
                    onComplete(false, "学習記録ZIPの書き出しに失敗しました: ${e.localizedMessage ?: "不明なエラー"}")
                }
            } finally {
                temporary?.delete()
            }
        }
    }

    fun importStudyArchive(context: Context, uri: Uri, onComplete: (Boolean, String) -> Unit) {
        isImporting = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val summary = context.contentResolver.openInputStream(uri)?.use { input ->
                    studyArchiveService.importFrom(input)
                } ?: error("ファイルを開けませんでした。")
                val addedGroup = summary.firstAddedGroupId?.let { wordDao.getGroupById(it) }
                withContext(Dispatchers.Main) {
                    isImporting = false
                    if (addedGroup != null) changeSelectedGroup(addedGroup)
                    onComplete(
                        true,
                        "学習記録ZIPを読み込みました。統合: ${summary.mergedGroups}冊（${summary.mergedWords}語）、追加: ${summary.addedGroups}冊（${summary.addedWords}語）"
                    )
                }
            } catch (e: Exception) {
                Log.e("TangoPro", "Study archive import failed", e)
                withContext(Dispatchers.Main) {
                    isImporting = false
                    onComplete(false, "学習記録ZIPを読み込めませんでした: ${e.localizedMessage ?: "不正なファイル"}")
                }
            }
        }
    }

    // --- CSV Importing Operations ---
    fun importCsvFromUri(context: Context, uri: Uri, customName: String, language: String = "en", onComplete: (Boolean, String) -> Unit) {
        isImporting = true
        Log.d("MainViewModel", "Starting CSV import from URI: $uri")
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

                val name = customName.trim().ifBlank { generatedGroupName("インポート") }
                val importedWords = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use(::parseImportedWords)
                val readCount = importedWords.size

                if (readCount == 0) {
                    withContext(Dispatchers.Main) {
                        isImporting = false
                        onComplete(false, "有効な単語データが見つかりませんでした。")
                    }
                    return@launch
                }

                val groupId = insertImportedGroup(name, language, importedWords)
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
                Log.e("TangoPro", "CSV file import failed", e)
                val errorMessage = when (e) {
                    is java.io.IOException -> "ファイル読み込みエラー: ${e.localizedMessage}"
                    is java.lang.IllegalArgumentException -> "無効なCSV形式: ${e.localizedMessage}"
                    is java.lang.OutOfMemoryError -> "ファイルが大きすぎます"
                    else -> "エラー: ${e.localizedMessage ?: "読み込み失敗"}"
                }
                withContext(Dispatchers.Main) {
                    isImporting = false
                    onComplete(false, errorMessage)
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
                val name = customName.trim().ifBlank { generatedGroupName("貼付インポート") }
                val importedWords = rawText.reader().use(::parseImportedWords)
                val readCount = importedWords.size

                if (readCount == 0) {
                    withContext(Dispatchers.Main) {
                        isImporting = false
                        onComplete(false, "有効な単語データが見つかりませんでした。")
                    }
                    return@launch
                }

                val groupId = insertImportedGroup(name, language, importedWords)
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
                Log.e("TangoPro", "CSV text import failed", e)
                withContext(Dispatchers.Main) {
                    isImporting = false
                    onComplete(false, "エラー: ${e.localizedMessage ?: "処理失敗"}")
                }
            }
        }
    }

    fun updateGroup(group: StudyGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalizedGroup = group.copy(language = StudyLanguage.normalize(group.language))
            wordDao.updateGroup(normalizedGroup)
            if (_selectedGroup.value?.id == group.id) {
                withContext(Dispatchers.Main) {
                    changeSelectedGroup(normalizedGroup)
                }
            }
        }
    }

    // --- Delete and Reset ---
    fun deleteGroupAndWords(groupId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            wordDao.deleteGroupAndWords(groupId)
            studySettingsPreferences.delete(groupId)
            withContext(Dispatchers.Main) {
                // Return to first available group, or null
                val groupsList = wordDao.getGroupsDirect()
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
        val finalName = combinedName.trim().ifBlank { generatedGroupName("連結単語帳") }
        isConcatenating = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val distinctIds = selectedIds.distinct()
                val sourceWords = buildList {
                    for (groupId in distinctIds) {
                        addAll(wordDao.getWordsDirect(groupId))
                    }
                }
                if (sourceWords.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isConcatenating = false
                        onComplete(false, "有効な単語データが見つかりませんでした。")
                    }
                    return@launch
                }

                val newGroupId = database.withTransaction {
                    val groups = wordDao.getGroupsDirect()
                    val newSortOrder = (groups.minOfOrNull { it.sortOrder } ?: 1) - 1
                    val groupId = wordDao.insertGroup(
                        StudyGroup(name = finalName, language = StudyLanguage.normalize(language), sortOrder = newSortOrder)
                    )
                    sourceWords.chunked(5_000).forEach { chunk ->
                        wordDao.insertWords(chunk.map { word ->
                            Word(
                                groupId = groupId,
                                english = word.english,
                                japanese = word.japanese,
                                tag = word.tag,
                                pronunciation = word.pronunciation,
                                studyCount = word.studyCount,
                                isCorrectLast = word.isCorrectLast,
                                lastStudiedAt = word.lastStudiedAt
                            )
                        })
                    }
                    groupId
                }

                val newGroup = wordDao.getGroupById(newGroupId)
                withContext(Dispatchers.Main) {
                    isConcatenating = false
                    if (newGroup != null) {
                        changeSelectedGroup(newGroup)
                        onComplete(true, "${distinctIds.size}個のCSVを連結し、成績を共有した新しいCSV「$finalName」を作成しました（合計${sourceWords.size}単語）。")
                    } else {
                        onComplete(false, "連結データの作成に失敗しました。")
                    }
                }

            } catch (e: Exception) {
                Log.e("TangoPro", "Group combine failed", e)
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
        filterMode: String,
        useRangeConstraint: Boolean = false, // Apply range/tag constraints
        selectedTag: String = "",       // Category constraint
        rangeStart: Int = 1,            // Range constraint (1-based index)
        rangeEnd: Int = 100,             // Range constraint (1-based index)
        quizCount: Int = selectedQuizCount
    ) {
        val normalizedFilter = StudyFilterMode.normalize(filterMode)
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

                var filtered = StudyFilterMode.matching(allWords, normalizedFilter)

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

                filtered = StudyFilterMode.orderForSession(filtered, normalizedFilter)

                // Apply limit matching setting (selectedQuizCount)
                val normalizedQuizCount = quizCount.takeIf {
                    it in setOf(5, 10, 20, 50, QUIZ_COUNT_ALL)
                } ?: 10
                val limit = if (normalizedQuizCount == QUIZ_COUNT_ALL) filtered.size else normalizedQuizCount
                val sessionWords = filtered.take(limit)

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

                val questions = QuizQuestionFactory.create(
                    sessionWords = sessionWords,
                    allWords = allWords,
                    directionForward = directionForward,
                    isMultipleChoice = isMultipleChoice
                )

                withContext(Dispatchers.Main) {
                    lastStudySessionRequest = StudySessionRequest(
                        group = group,
                        directionForward = directionForward,
                        isMultipleChoice = isMultipleChoice,
                        filterMode = normalizedFilter,
                        useRangeConstraint = useRangeConstraint,
                        selectedTag = selectedTag,
                        rangeStart = rangeStart,
                        rangeEnd = rangeEnd,
                        quizCount = normalizedQuizCount
                    )
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
                Log.e("TangoPro", "Study-session setup failed", e)
                withContext(Dispatchers.Main) {
                    isLoadingSession = false
                }
            }
        }
    }

    fun continueStudySession() {
        val request = lastStudySessionRequest ?: return
        startStudySession(
            group = request.group,
            directionForward = request.directionForward,
            isMultipleChoice = request.isMultipleChoice,
            filterMode = request.filterMode,
            useRangeConstraint = request.useRangeConstraint,
            selectedTag = request.selectedTag,
            rangeStart = request.rangeStart,
            rangeEnd = request.rangeEnd,
            quizCount = request.quizCount
        )
    }

    fun submitAnswer(ans: String) {
        val q = currentQuestion ?: return
        if (hasCheckedAnswer) return

        userAnswerText = ans.trim()
        val isCorrect = if (q.isMultipleChoice) {
            ans.trim() == q.correctAnswer.trim()
        } else {
            // Typing mode: match ignoring case and full/half width variations for english typing
            AnswerNormalizer.matches(ans, q.correctAnswer)
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
            val updatedRows = wordDao.recordStudyResult(
                wordId = q.word.id,
                isCorrect = isCorrect,
                studiedAt = System.currentTimeMillis()
            )
            if (updatedRows != 1) {
                Log.w("MainViewModel", "Study result target disappeared: wordId=${q.word.id}")
            }
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
        val lang = StudyLanguage.normalize(_selectedGroup.value?.language)
        if (isTtsEnabled && lang != "none") {
            if (!q.directionForward) {
                ttsService.speak(q.correctAnswer, ttsVolume, lang)
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

        val lang = StudyLanguage.normalize(_selectedGroup.value?.language)
        if (lang == "none") return
        if (q.directionForward) {
            ttsService.speak(q.questionText, ttsVolume, lang)
        }
    }

    fun speakCurrentQuestionManual() {
        val q = currentQuestion ?: return
        val lang = StudyLanguage.normalize(_selectedGroup.value?.language)
        if (lang == "none") return
        ttsService.speak(q.word.english, ttsVolume, lang)
    }

    override fun onCleared() {
        super.onCleared()
        ttsService.stop()
        ttsService.shutdown()
    }
}
