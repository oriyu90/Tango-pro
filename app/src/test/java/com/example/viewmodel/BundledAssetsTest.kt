package com.example.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.CsvParser
import com.example.domain.BundledGroupCatalog
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BundledAssetsTest {

    @Test
    fun `every bundled CSV can be opened and contains words`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        BundledGroupCatalog.all.forEach { spec ->
            val filename = spec.assetFileName
            assertTrue("Asset names must be ASCII-safe: $filename", filename.all { it.code < 128 })
            val rows = context.assets.open(filename).bufferedReader(Charsets.UTF_8).use {
                CsvParser.parse(it)
            }
            assertTrue("Bundled CSV is empty: $filename", rows.isNotEmpty())
            assertTrue("Bundled CSV has no valid word rows: $filename", rows.any { it.size >= 2 })
        }
        assertEquals(17, BundledGroupCatalog.all.size)
        assertEquals(1, BundledGroupCatalog.all.count { it.language == "zh" })
        assertEquals(16, BundledGroupCatalog.all.count { it.language == "en" })
        val packagedAssets = context.assets.list("").orEmpty().toSet()
        assertFalse("Legacy English CSV must not be packaged", "basic_english_words.csv" in packagedAssets)
        assertFalse("Legacy English CSV must not be packaged", "common_test_words.csv" in packagedAssets)
    }
}
