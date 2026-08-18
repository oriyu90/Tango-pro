package com.example.domain

import com.example.data.Word

object StudyRound {
    fun current(words: List<Word>): Int =
        if (words.isEmpty()) 1 else words.minOf { it.studyCount.coerceAtLeast(0) } + 1

    fun label(round: Int): String = "${round.coerceAtLeast(1)}周目"

    /** 1〜4周目だけ専用配色を持ち、5周目以降はラベル表示だけにする。 */
    fun visualTier(round: Int): Int = round.takeIf { it in 1..4 } ?: 0
}
