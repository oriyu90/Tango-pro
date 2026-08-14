package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import com.example.domain.StudyLanguage
import com.example.viewmodel.MainViewModel

@Composable
fun StudySessionScreen(viewModel: MainViewModel) {
    if (viewModel.isLoadingSession) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (viewModel.sessionQuestions.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "出題可能な単語がありません",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "設定された条件（未学習のみ、前回間違い等）にマッチする単語リストが見つかりませんでした。",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { viewModel.exitStudySession() }) {
                    Text("ダッシュボードに戻る")
                }
            }
        }
        return
    }

    val total = viewModel.sessionQuestions.size
    val currentIdx = viewModel.currentIndex

    LaunchedEffect(currentIdx) {
        viewModel.speakCurrentQuestionIfNeeded()
    }

    if (currentIdx >= total) {
        QuizSummaryScreen(viewModel = viewModel)
        return
    }

    val question = viewModel.currentQuestion ?: return
    val progress = (currentIdx + 1).toFloat() / total

    val kbController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${currentIdx + 1} / ${total} 問目",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "正答率: ${viewModel.sessionScore} / ${currentIdx}問中",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = { viewModel.exitStudySession() }) {
                Text("学習を中断", color = MaterialTheme.colorScheme.error)
            }
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (question.word.tag.isNotBlank()) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(question.word.tag, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clickable(onClickLabel = "単語を読み上げる") {
                            viewModel.speakCurrentQuestionManual()
                        }
                        .testTag("question_speak_area")
                ) {
                    Text(
                        text = question.questionText,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 40.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (question.word.english.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.speakCurrentQuestionManual() },
                            modifier = Modifier.testTag("tts_speak_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "音声読み上げ",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (question.word.pronunciation.isNotBlank() && question.directionForward && question.isMultipleChoice) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "/ ${question.word.pronunciation} /",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                val language = StudyLanguage.fromCode(viewModel.selectedGroup.value?.language)
                val langStr = if (language.code == "none") "対象言語" else language.displayName
                val targetText = if (langStr == "英語") "英語スペル" else "${langStr}表記"
                Text(
                    text = if (question.directionForward) "この${langStr}の日本語訳を答えてください" else "この日本語の${targetText}を答えてください",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (question.isMultipleChoice) {
                question.choices.forEach { option ->
                    val colorScheme = MaterialTheme.colorScheme
                    val isUserSelection = viewModel.userAnswerText == option

                    val btnColors = when {
                        viewModel.hasCheckedAnswer && option == question.correctAnswer -> {
                            ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White)
                        }
                        viewModel.hasCheckedAnswer && isUserSelection && !viewModel.isAnswerCorrect -> {
                            ButtonDefaults.buttonColors(containerColor = colorScheme.error, contentColor = colorScheme.onError)
                        }
                        else -> {
                            ButtonDefaults.buttonColors(containerColor = colorScheme.surfaceVariant, contentColor = colorScheme.onSurfaceVariant)
                        }
                    }

                    Button(
                        onClick = {
                            if (!viewModel.hasCheckedAnswer) {
                                viewModel.submitAnswer(option)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("quiz_choice_$option"),
                        shape = RoundedCornerShape(12.dp),
                        colors = btnColors,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = option,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                var inputTxt by remember { mutableStateOf("") }

                LaunchedEffect(viewModel.currentIndex) {
                    inputTxt = ""
                }

                if (!viewModel.hasCheckedAnswer) {
                    OutlinedTextField(
                        value = inputTxt,
                        onValueChange = { inputTxt = it },
                        label = { Text("解答を入力...") },
                        placeholder = { Text(if (question.directionForward) "日本語を入力" else "English word") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (inputTxt.isNotBlank()) {
                                    kbController?.hide()
                                    viewModel.submitAnswer(inputTxt)
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("typing_input_field")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            kbController?.hide()
                            viewModel.submitAnswer(inputTxt)
                        },
                        enabled = inputTxt.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("typing_submit_btn")
                    ) {
                        Text("判定する", fontWeight = FontWeight.Bold)
                    }
                } else {
                    val backColor = if (viewModel.darkThemeSelected) {
                        if (viewModel.isAnswerCorrect) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                    } else {
                        if (viewModel.isAnswerCorrect) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                    }
                    val textClr = if (viewModel.darkThemeSelected) {
                        if (viewModel.isAnswerCorrect) Color(0xFF81C784) else Color(0xFFE57373)
                    } else {
                        if (viewModel.isAnswerCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = backColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (viewModel.isAnswerCorrect) "正解！" else "不正解...",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = textClr
                            )
                            Text(
                                text = "あなたの回答: ${viewModel.userAnswerText}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "正しい訳: ${question.correctAnswer}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        if (viewModel.hasCheckedAnswer) {
            Button(
                onClick = { viewModel.nextQuestion() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("next_question_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("次へ", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}
