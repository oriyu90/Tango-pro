package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.StudyGroup
import com.example.data.Word
import com.example.domain.StudyFilterMode
import com.example.domain.StudyLanguage
import com.example.domain.StudyProgress
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    selectedGroup: StudyGroup,
    words: List<Word>,
    viewModel: MainViewModel,
    onEditGroup: () -> Unit,
    onResetStats: () -> Unit,
    onDeleteGroup: () -> Unit,
    onExportCsv: () -> Unit
) {
    val totalCount = words.size
    val studiedCount = words.count(StudyProgress::isLearned)
    val vagueCount = words.count(StudyProgress::isVague)
    val unstudiedCount = totalCount - studiedCount - vagueCount

    val studiedPercent = if (totalCount > 0) (studiedCount * 100f / totalCount).toInt() else 0
    val vaguePercent = if (totalCount > 0) (vagueCount * 100f / totalCount).toInt() else 0
    val unstudiedPercent = if (totalCount > 0) 100 - studiedPercent - vaguePercent else 0

    val uniqueTags = remember(words) {
        val tagsSet = words.map { it.tag }.filter { it.isNotBlank() }.distinct().toMutableList()
        tagsSet.add(0, "すべて")
        tagsSet
    }

    // Bind these selections directly to the persistent viewModel properties
    val studyDirectionForward = viewModel.studyDirectionForward
    val studyMultipleChoice = viewModel.studyMultipleChoice
    val filterMode = viewModel.filterMode
    val selectedTagConstraint = viewModel.selectedTagConstraint

    var rangeStartText by remember { mutableStateOf(viewModel.rangeStart.toString()) }
    var rangeEndText by remember { mutableStateOf(if (viewModel.rangeEnd == -1) totalCount.toString() else viewModel.rangeEnd.toString()) }

    var activeErrorLog by remember { mutableStateOf("") }

    LaunchedEffect(selectedGroup.id, totalCount) {
        if (viewModel.rangeEnd == -1 || viewModel.rangeEnd > totalCount || viewModel.rangeEnd <= 0) {
            rangeEndText = totalCount.toString()
            viewModel.rangeEnd = totalCount
        } else {
            rangeEndText = viewModel.rangeEnd.toString()
        }

        if (viewModel.rangeStart > totalCount || viewModel.rangeStart <= 0) {
            rangeStartText = "1"
            viewModel.rangeStart = 1
        } else {
            rangeStartText = viewModel.rangeStart.toString()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "選択中: ${selectedGroup.name}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "収録単語数: ${totalCount}語",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }

                    Row {
                        IconButton(onClick = onEditGroup) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "単語帳の編集",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onExportCsv) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "CSVエクスポート",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onResetStats) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "成績初期化",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onDeleteGroup) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "単語帳削除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "学習進捗度・成績ステータス",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("学習済: ${studiedCount}語 (${studiedPercent}%)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("うろ覚え: ${vagueCount}語 (${vaguePercent}%)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        Text("要復習/未学習: ${unstudiedCount}語 (${unstudiedPercent}%)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        if (totalCount == 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Gray.copy(alpha = 0.3f))
                            )
                        } else {
                            if (studiedCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(studiedCount.toFloat())
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                            if (vagueCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(vagueCount.toFloat())
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                )
                            }
                            if (unstudiedCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(unstudiedCount.toFloat())
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "※「うろ覚え」は薄い紫のバー（学習1回）、「学習済」は濃い紫のバー（学習2回以上且つ前回正解）、「要復習/未学習」は赤のバーで表示されます。",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "出題学習設定",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            val shortLangStr = StudyLanguage.fromCode(selectedGroup.language).shortName
                            Text("出題方向", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            if (!studyMultipleChoice) {
                                Text("※タイピングは日→${shortLangStr}固定です", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Row {
                            val shortLangStr = StudyLanguage.fromCode(selectedGroup.language).shortName
                            FilterChip(
                                selected = studyDirectionForward,
                                onClick = { if (studyMultipleChoice) viewModel.studyDirectionForward = true },
                                label = { Text("順方向 (${shortLangStr}→日)") },
                                enabled = studyMultipleChoice
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            FilterChip(
                                selected = !studyDirectionForward,
                                onClick = { viewModel.studyDirectionForward = false },
                                label = { Text("逆方向 (日→${shortLangStr})") }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("解答形式", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row {
                            FilterChip(
                                selected = studyMultipleChoice,
                                onClick = { viewModel.studyMultipleChoice = true },
                                label = { Text("4択クイズ") }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            FilterChip(
                                selected = !studyMultipleChoice,
                                onClick = { viewModel.studyMultipleChoice = false },
                                label = { Text("タイピング") }
                            )
                        }
                    }

                    Text("出題対象・成績条件 (成績管理)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        StudyFilterMode.options.forEach { (modeCode, title) ->
                            val active = filterMode == modeCode
                            FilterChip(
                                selected = active,
                                onClick = { viewModel.filterMode = modeCode },
                                label = { Text(title, fontSize = 11.sp) },
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("範囲・タグ指定の適用 (オプション)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("指定範囲やタグでの絞り込みを重ねて適用します", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = viewModel.useRangeConstraint,
                            onCheckedChange = { viewModel.useRangeConstraint = it },
                            modifier = Modifier.testTag("use_range_constraint_switch")
                        )
                    }

                    if (viewModel.useRangeConstraint) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("範囲・品詞タグ指定:", fontWeight = FontWeight.Bold, fontSize = 12.sp)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("出題範囲: ", fontSize = 12.sp)
                                    OutlinedTextField(
                                        value = rangeStartText,
                                        onValueChange = {
                                            rangeStartText = it
                                            it.toIntOrNull()?.let { startInt ->
                                                viewModel.rangeStart = startInt
                                            }
                                        },
                                        modifier = Modifier.width(70.dp).height(50.dp),
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                    )
                                    Text(" 〜 ", fontSize = 12.sp)
                                    OutlinedTextField(
                                        value = rangeEndText,
                                        onValueChange = {
                                            rangeEndText = it
                                            it.toIntOrNull()?.let { endInt ->
                                                viewModel.rangeEnd = endInt
                                            }
                                        },
                                        modifier = Modifier.width(70.dp).height(50.dp),
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("番目 (最大${totalCount})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                if (uniqueTags.size > 1) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("品詞・分類タグ:", fontSize = 12.sp)
                                        var expandedByTag by remember { mutableStateOf(false) }
                                        Box {
                                            Button(
                                                onClick = { expandedByTag = true },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
                                            ) {
                                                Text(selectedTagConstraint, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
                                            }
                                            DropdownMenu(
                                                expanded = expandedByTag,
                                                onDismissRequest = { expandedByTag = false }
                                            ) {
                                                uniqueTags.forEach { tag ->
                                                    DropdownMenuItem(
                                                        text = { Text(tag) },
                                                        onClick = {
                                                            viewModel.selectedTagConstraint = tag
                                                            expandedByTag = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (activeErrorLog.isNotBlank()) {
                        Text(
                            text = activeErrorLog,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            activeErrorLog = ""
                            val startInt = rangeStartText.toIntOrNull() ?: 1
                            val endInt = rangeEndText.toIntOrNull() ?: totalCount

                            var activeWords = words
                            if (viewModel.useRangeConstraint) {
                                if (selectedTagConstraint.isNotBlank() && selectedTagConstraint != "すべて") {
                                    activeWords = activeWords.filter { it.tag.equals(selectedTagConstraint, ignoreCase = true) }
                                }
                                val s = maxOf(0, startInt - 1)
                                val e = minOf(activeWords.size, endInt)
                                if (s < e) {
                                    activeWords = activeWords.subList(s, e)
                                } else {
                                    activeWords = emptyList()
                                }
                            }

                            val matchesCount = StudyFilterMode.matching(activeWords, filterMode).size

                            if (matchesCount == 0) {
                                activeErrorLog = "選択された学習ステータスに合致する単語がありません。"
                            } else {
                                viewModel.startStudySession(
                                    group = selectedGroup,
                                    directionForward = studyDirectionForward,
                                    isMultipleChoice = studyMultipleChoice,
                                    filterMode = filterMode,
                                    useRangeConstraint = viewModel.useRangeConstraint,
                                    selectedTag = selectedTagConstraint,
                                    rangeStart = startInt,
                                    rangeEnd = endInt
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("start_quiz_session_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("この設定で学習を開始する", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
