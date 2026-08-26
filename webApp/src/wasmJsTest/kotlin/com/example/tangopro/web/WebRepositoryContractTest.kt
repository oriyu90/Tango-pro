package com.example.tangopro.web

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebRepositoryContractTest {
    @Test
    fun importUpdateResetAndDeleteAreConsistent() = runTest {
        val repository = WebRepository.open(":memory:")
        val groupId = repository.importCsv("契約テスト", "en", "apple,りんご,名詞,æpl\n")

        assertEquals(listOf("契約テスト"), repository.groupsDirect().map { it.name })
        val word = repository.wordsDirect(groupId).single()
        List(8) { async { repository.recordResult(word.id, correct = true) } }.awaitAll()
        assertEquals(8, repository.wordsDirect(groupId).single().studyCount)

        repository.resetProgress(groupId)
        assertEquals(0, repository.wordsDirect(groupId).single().studyCount)
        repository.deleteGroup(groupId)
        assertTrue(repository.groupsDirect().none { it.id == groupId })
    }

    @Test
    fun failedCsvImportLeavesDatabaseUnchanged() = runTest {
        val repository = WebRepository.open(":memory:")
        val before = repository.groupsDirect()
        runCatching { repository.importCsv("壊れたCSV", "en", "word,\"unfinished") }
        assertEquals(before, repository.groupsDirect())
    }

    @Test
    fun utf8CsvPreservesExtendedLatinAndMathematicalSymbols() = runTest {
        val repository = WebRepository.open(":memory:")
        val csv = buildString {
            appendLine("vanish,消える（≒ disappear）,動詞")
            appendLine("cœur,coração／ação／maçã,Français・Português")
            appendLine("Příliš,żółć／Straße,Čeština・Polski・Deutsch")
        }

        val groupId = repository.importCsv("Unicode契約テスト", "fr", csv)
        val words = repository.wordsDirect(groupId)

        assertEquals("消える（≒ disappear）", words[0].meaning)
        assertEquals("cœur", words[1].term)
        assertEquals("coração／ação／maçã", words[1].meaning)
        assertEquals("żółć／Straße", words[2].meaning)
    }
}
