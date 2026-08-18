package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.StudyGroup
import com.example.data.StudyQuestion
import com.example.data.Word
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [36])
class NarrowDashboardUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `simple dashboard remains usable on narrow phone`() {
        val viewModel = MainViewModel(ApplicationProvider.getApplicationContext<Application>()).apply {
            simpleModeEnabled = true
        }
        val group = StudyGroup(id = 99, name = "非常に長い名前の単語帳で縮小後に省略される表示確認用", language = "en")
        val words = listOf(
            Word(id = 1, groupId = 99, english = "apple", japanese = "りんご", studyCount = 1),
            Word(id = 2, groupId = 99, english = "book", japanese = "本")
        )
        composeRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                DashboardScreen(group, words, viewModel, {}, {}, {}, {})
            }
        }

        composeRule.onNodeWithText("この設定で学習を開始する").assertIsDisplayed()
        composeRule.onNodeWithText("50%").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/verification-images/dashboard-narrow.png")
    }

    @Test
    fun `portrait quiz scrolls to multiline final choice`() {
        val viewModel = MainViewModel(ApplicationProvider.getApplicationContext<Application>()).apply {
            quizTextScale = 1.4f
            val word = Word(id = 31, groupId = 1, english = "responsibility", japanese = "責任")
            sessionQuestions = listOf(
                StudyQuestion(
                    word,
                    word.english,
                    word.japanese,
                    listOf(
                        "責任を引き受けること",
                        "予定を変更して後日に回すこと",
                        "相手の意見を丁寧に聞き取ること",
                        "必要な資料をあらかじめ詳しく準備しておくこと"
                    ),
                    true,
                    true
                )
            )
            currentQuestion = sessionQuestions.first()
        }
        composeRule.setContent {
            MyApplicationTheme(dynamicColor = false) { StudySessionScreen(viewModel) }
        }

        composeRule.onNodeWithText("必要な資料をあらかじめ詳しく準備しておくこと")
            .performScrollTo()
            .assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h480dp-xhdpi", sdk = [36])
class LandscapeStudyUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `large multiline choices fit in landscape layout`() {
        val viewModel = MainViewModel(ApplicationProvider.getApplicationContext<Application>()).apply {
            quizTextScale = 1.4f
            val word = Word(id = 1, groupId = 1, english = "take responsibility", japanese = "責任を引き受ける")
            sessionQuestions = listOf(
                StudyQuestion(
                    word = word,
                    questionText = word.english,
                    correctAnswer = word.japanese,
                    choices = listOf(
                        "責任を引き受けて最後まできちんと対応する",
                        "予定を急に変更して別の日に先送りする",
                        "相手の意見を聞かずに一方的に決定する",
                        "必要な資料をあらかじめ丁寧に準備する"
                    ),
                    directionForward = true,
                    isMultipleChoice = true
                )
            )
            currentQuestion = sessionQuestions.first()
        }
        composeRule.setContent {
            MyApplicationTheme(dynamicColor = false) { StudySessionScreen(viewModel) }
        }

        composeRule.onNodeWithText("責任を引き受けて最後まできちんと対応する").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/verification-images/study-landscape-large.png")
    }
}
