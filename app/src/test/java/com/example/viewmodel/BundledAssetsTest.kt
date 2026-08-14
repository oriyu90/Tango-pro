package com.example.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.CsvParser
import com.example.domain.BundledGroupCatalog
import org.junit.Assert.assertTrue
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
    }
}
