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
