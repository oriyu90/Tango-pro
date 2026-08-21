package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.StudyQuestion
import com.example.domain.StudyArrangementMode
import com.example.domain.StudyLanguage
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudySessionScreen(viewModel: MainViewModel) {
    if (viewModel.isLoadingSession) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (viewModel.sessionQuestions.isEmpty()) {
        EmptyQuestionState { viewModel.exitStudySession() }
        return
    }

    val total = viewModel.sessionQuestions.size
    val currentIndex = viewModel.currentIndex
    LaunchedEffect(currentIndex) { viewModel.speakCurrentQuestionIfNeeded() }
    if (currentIndex >= total) {
        QuizSummaryScreen(viewModel)
        return
    }
    val question = viewModel.currentQuestion ?: return

    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val useWideLayout = maxWidth >= 720.dp && maxWidth > maxHeight
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "${currentIndex + 1} / $total 問目",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    "正答率: ${viewModel.sessionScore} / ${currentIndex}問中",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = { viewModel.exitStudySession() }) {
                    Text("学習を中断", color = MaterialTheme.colorScheme.error)
                }
            }
            LinearProgressIndicator(
                progress = { (currentIndex + 1).toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
            )

            if (useWideLayout) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    QuestionPanel(question, viewModel, Modifier.weight(1f).fillMaxHeight())
                    AnswerPanel(
                        question = question,
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                    )
                }
            } else if (viewModel.quizArrangementMode == StudyArrangementMode.EVEN_FILL) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuestionPanel(question, viewModel, Modifier.fillMaxWidth().weight(1f))
                    AnswerPanel(question, viewModel, Modifier.fillMaxWidth())
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuestionPanel(question, viewModel, Modifier.fillMaxWidth().heightIn(min = 220.dp))
                    AnswerPanel(question, viewModel, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun EmptyQuestionState(onBack: () -> Unit) {
    Card(Modifier.fillMaxSize().padding(24.dp)) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Warning, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Text("出題可能な単語がありません", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "設定された条件（未学習のみ、前回間違い等）にマッチする単語リストが見つかりませんでした。",
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onBack) { Text("ダッシュボードに戻る") }
        }
    }
}

@Composable
private fun QuestionPanel(question: StudyQuestion, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val scale = viewModel.quizTextScale
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (question.word.tag.isNotBlank()) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(question.word.tag, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                )
                Spacer(Modifier.height(12.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "単語を読み上げる") { viewModel.speakCurrentQuestionManual() }
                    .testTag("question_speak_area")
            ) {
                Text(
                    question.questionText,
                    fontSize = (32 * scale).sp,
                    lineHeight = (40 * scale).sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (question.word.english.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.speakCurrentQuestionManual() },
                        modifier = Modifier.testTag("tts_speak_btn")
                    ) {
                        Icon(Icons.Default.PlayArrow, "音声読み上げ", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (question.word.pronunciation.isNotBlank() && question.directionForward && question.isMultipleChoice) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "/ ${question.word.pronunciation} /",
                    fontSize = (18 * scale.coerceAtMost(1.2f)).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(8.dp))
            val language = StudyLanguage.fromCode(viewModel.selectedGroup.value?.language)
            val languageName = if (language.code == "none") "対象言語" else language.displayName
            val targetText = if (languageName == "英語") "英語スペル" else "${languageName}表記"
            Text(
                if (question.directionForward) "この${languageName}の日本語訳を答えてください" else "この日本語の${targetText}を答えてください",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AnswerPanel(question: StudyQuestion, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    var typedAnswer by remember(viewModel.currentIndex) { mutableStateOf("") }
    val choiceSize = (15 * viewModel.quizTextScale).sp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (question.isMultipleChoice) {
            question.choices.forEach { option ->
                val selected = viewModel.userAnswerText == option
                val colors = when {
                    viewModel.hasCheckedAnswer && option == question.correctAnswer ->
                        ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White)
                    viewModel.hasCheckedAnswer && selected && !viewModel.isAnswerCorrect ->
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                    else -> ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { if (!viewModel.hasCheckedAnswer) viewModel.submitAnswer(option) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("quiz_choice_$option"),
                    shape = RoundedCornerShape(12.dp),
                    colors = colors,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        option,
                        fontSize = choiceSize,
                        lineHeight = choiceSize * 1.25f,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 3
                    )
                }
            }
        } else if (!viewModel.hasCheckedAnswer) {
            OutlinedTextField(
                value = typedAnswer,
                onValueChange = { typedAnswer = it },
                label = { Text("解答を入力...") },
                placeholder = { Text(if (question.directionForward) "日本語を入力" else "English word") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (typedAnswer.isNotBlank()) {
                        keyboard?.hide()
                        viewModel.submitAnswer(typedAnswer)
                    }
                }),
                modifier = Modifier.fillMaxWidth().testTag("typing_input_field")
            )
            Button(
                onClick = {
                    keyboard?.hide()
                    viewModel.submitAnswer(typedAnswer)
                },
                enabled = typedAnswer.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("typing_submit_btn")
            ) { Text("判定する", fontWeight = FontWeight.Bold) }
        } else {
            AnswerFeedback(question, viewModel)
        }

        if (viewModel.hasCheckedAnswer) {
            Button(
                onClick = { viewModel.nextQuestion() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("next_question_btn")
            ) {
                Text("次へ", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
            }
        }
    }
}

@Composable
private fun AnswerFeedback(question: StudyQuestion, viewModel: MainViewModel) {
    val dark = viewModel.darkThemeSelected
    val background = if (dark) {
        if (viewModel.isAnswerCorrect) Color(0xFF1B5E20) else Color(0xFFB71C1C)
    } else {
        if (viewModel.isAnswerCorrect) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
    }
    val accent = if (dark) {
        if (viewModel.isAnswerCorrect) Color(0xFF81C784) else Color(0xFFE57373)
    } else {
        if (viewModel.isAnswerCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)
    }
    Card(colors = CardDefaults.cardColors(containerColor = background), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(if (viewModel.isAnswerCorrect) "正解！" else "不正解...", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = accent)
            Text("あなたの回答: ${viewModel.userAnswerText}", fontSize = 13.sp)
            Text("正しい訳: ${question.correctAnswer}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}
