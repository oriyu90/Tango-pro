package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.StudySettingsPreferences
import com.example.domain.StudyFilterMode
import com.example.domain.StudySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Tango pro", appName)
  }

  @Test
  fun `study settings migrate then remain independent for each book`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val preferences = context.getSharedPreferences("study-settings-test", Context.MODE_PRIVATE)
    preferences.edit().clear().commit()
    val store = StudySettingsPreferences(preferences)
    val legacy = StudySettings(
      directionForward = false,
      filterMode = StudyFilterMode.WEAK,
      quizCount = 20
    )

    val first = store.load(101, legacy)
    val second = store.load(202, legacy)
    assertFalse(first.directionForward)
    assertEquals(20, second.quizCount)

    store.save(101, first.copy(directionForward = true, quizCount = 5))
    assertTrue(store.load(101, StudySettings()).directionForward)
    assertEquals(5, store.load(101, StudySettings()).quizCount)
    assertFalse(store.load(202, StudySettings()).directionForward)
    assertEquals(20, store.load(202, StudySettings()).quizCount)
  }
}
