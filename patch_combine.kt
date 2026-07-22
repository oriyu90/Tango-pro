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
