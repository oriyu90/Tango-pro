import re

with open("app/src/main/java/com/example/viewmodel/MainViewModel.kt", "r") as f:
    text = f.read()

# Add importCsvFromAsset
new_method = """    private suspend fun importCsvFromAsset(context: Context, filename: String, groupName: String, language: String) {
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.assets.open(filename)
                val wordsToInsert = mutableListOf<Word>()
                val groupId = wordDao.insertGroup(StudyGroup(name = groupName, language = language))
                var readCount = 0

                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
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
                        line = reader.readLine()
                    }
                }
                if (wordsToInsert.isNotEmpty()) {
                    wordDao.insertWords(wordsToInsert)
                }
                if (readCount == 0) {
                    wordDao.deleteGroupAndWords(groupId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

"""

# find "fun selectGroup(" and insert before it
text = text.replace("    fun selectGroup(group: StudyGroup)", new_method + "    fun selectGroup(group: StudyGroup)")

# now modify init block
old_init = """    init {
        // Restore last selected group or auto-select first group if available
        viewModelScope.launch {
            val savedId = prefs.getLong("lastSelectedGroupId", -1L)"""

new_init = """    init {
        // Restore last selected group or auto-select first group if available
        viewModelScope.launch {
            val preloaded = prefs.getBoolean("preloadedData", false)
            if (!preloaded) {
                val list = groups.firstOrNull() ?: emptyList()
                if (list.isEmpty()) {
                    importCsvFromAsset(getApplication(), "英語基本フレーズ.csv", "英語基本フレーズ", "en")
                    importCsvFromAsset(getApplication(), "英語基本単語.csv", "英語基本単語", "en")
                    importCsvFromAsset(getApplication(), "中国語基本単語.csv", "中国語基本単語", "zh")
                }
                prefs.edit().putBoolean("preloadedData", true).apply()
            }
            val savedId = prefs.getLong("lastSelectedGroupId", -1L)"""

text = text.replace(old_init, new_init)

with open("app/src/main/java/com/example/viewmodel/MainViewModel.kt", "w") as f:
    f.write(text)

