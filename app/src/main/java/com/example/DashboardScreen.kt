package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.StudyGroup
import com.example.data.Word
import com.example.domain.StudyFilterMode
import com.example.domain.StudyLanguage
import com.example.domain.StudyProgress
import com.example.domain.StudyRound

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    selectedGroup: StudyGroup,
    words: List<Word>,
    viewModel: com.example.viewmodel.MainViewModel,
    onEditGroup: () -> Unit,
    onResetStats: () -> Unit,
    onDeleteGroup: () -> Unit,
    onExportCsv: () -> Unit
) {
    val totalCount = words.size
    val learnedCount = words.count(StudyProgress::isLearned)
    val vagueCount = words.count(StudyProgress::isVague)
    val reviewCount = totalCount - learnedCount - vagueCount
    val learnedPercent = percent(learnedCount, totalCount)
    val vaguePercent = percent(vagueCount, totalCount)
    val reviewPercent = if (totalCount == 0) 0 else 100 - learnedPercent - vaguePercent
    val touchedPercent = percent(words.count { it.studyCount > 0 }, totalCount)
    val currentRound = StudyRound.current(words)
    val simpleMode = viewModel.simpleModeEnabled

    val uniqueTags = remember(words) {
        buildList {
            add("すべて")
            addAll(words.map { it.tag }.filter { it.isNotBlank() }.distinct())
        }
    }
    val studyDirectionForward = viewModel.studyDirectionForward
    val studyMultipleChoice = viewModel.studyMultipleChoice
    val filterMode = viewModel.filterMode
    val selectedTagConstraint = viewModel.selectedTagConstraint
    var rangeStartText by remember(selectedGroup.id) { mutableStateOf(viewModel.rangeStart.toString()) }
    var rangeEndText by remember(selectedGroup.id) {
        mutableStateOf(if (viewModel.rangeEnd == -1) totalCount.toString() else viewModel.rangeEnd.toString())
    }
    var activeErrorLog by remember(selectedGroup.id) { mutableStateOf("") }
    var settingsExpanded by rememberSaveable(selectedGroup.id, simpleMode) { mutableStateOf(!simpleMode) }

    LaunchedEffect(selectedGroup.id, totalCount) {
        if (viewModel.rangeEnd == -1 || viewModel.rangeEnd !in 1..totalCount.coerceAtLeast(1)) {
            rangeEndText = totalCount.toString()
            viewModel.rangeEnd = totalCount.coerceAtLeast(1)
        } else {
            rangeEndText = viewModel.rangeEnd.toString()
        }
        if (viewModel.rangeStart !in 1..totalCount.coerceAtLeast(1)) {
            rangeStartText = "1"
            viewModel.rangeStart = 1
        } else {
            rangeStartText = viewModel.rangeStart.toString()
        }
    }

    val localDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = localDensity.density,
            fontScale = localDensity.fontScale * if (simpleMode) 1.1f else 1f
        )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compactWidth = maxWidth < 680.dp
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    SelectedGroupCard(
                        groupName = selectedGroup.name,
                        totalCount = totalCount,
                        round = currentRound,
                        compact = compactWidth,
                        onEditGroup = onEditGroup,
                        onResetStats = onResetStats,
                        onDeleteGroup = onDeleteGroup,
                        onExportCsv = onExportCsv,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 1000.dp)
                    )
                }

                item {
                    if (simpleMode) {
                        SimpleProgressCard(
                            progressPercent = touchedPercent,
                            modifier = Modifier.fillMaxWidth().widthIn(max = 1000.dp)
                        )
                    } else {
                        DetailedProgressCard(
                            total = totalCount,
                            learned = learnedCount,
                            vague = vagueCount,
                            review = reviewCount,
                            learnedPercent = learnedPercent,
                            vaguePercent = vaguePercent,
                            reviewPercent = reviewPercent,
                            modifier = Modifier.fillMaxWidth().widthIn(max = 1000.dp)
                        )
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth().widthIn(max = 1000.dp)) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (simpleMode) Modifier.clickable { settingsExpanded = !settingsExpanded } else Modifier),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "出題学習設定",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (simpleMode) {
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = if (settingsExpanded) "出題設定を閉じる" else "出題設定を開く",
                                        modifier = Modifier.rotate(if (settingsExpanded) 180f else 0f)
                                    )
                                }
                            }

                            if (settingsExpanded) {
                                StudySettingsContent(
                                    selectedGroup = selectedGroup,
                                    totalCount = totalCount,
                                    uniqueTags = uniqueTags,
                                    compact = compactWidth,
                                    studyDirectionForward = studyDirectionForward,
                                    studyMultipleChoice = studyMultipleChoice,
                                    filterMode = filterMode,
                                    selectedTagConstraint = selectedTagConstraint,
                                    rangeStartText = rangeStartText,
                                    rangeEndText = rangeEndText,
                                    onRangeStartChange = {
                                        rangeStartText = it
                                        it.toIntOrNull()?.let { value -> viewModel.rangeStart = value }
                                    },
                                    onRangeEndChange = {
                                        rangeEndText = it
                                        it.toIntOrNull()?.let { value -> viewModel.rangeEnd = value }
                                    },
                                    viewModel = viewModel
                                )
                            }

                            if (activeErrorLog.isNotBlank()) {
                                Text(
                                    activeErrorLog,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    activeErrorLog = ""
                                    val start = rangeStartText.toIntOrNull() ?: 1
                                    val end = rangeEndText.toIntOrNull() ?: totalCount
                                    var activeWords = words
                                    if (viewModel.useRangeConstraint) {
                                        if (selectedTagConstraint.isNotBlank() && selectedTagConstraint != "すべて") {
                                            activeWords = activeWords.filter {
                                                it.tag.equals(selectedTagConstraint, ignoreCase = true)
                                            }
                                        }
                                        val from = maxOf(0, start - 1)
                                        val to = minOf(activeWords.size, end)
                                        activeWords = if (from < to) activeWords.subList(from, to) else emptyList()
                                    }
                                    if (StudyFilterMode.matching(activeWords, filterMode).isEmpty()) {
                                        activeErrorLog = "選択された学習ステータスに合致する単語がありません。"
                                    } else {
                                        viewModel.startStudySession(
                                            group = selectedGroup,
                                            directionForward = studyDirectionForward,
                                            isMultipleChoice = studyMultipleChoice,
                                            filterMode = filterMode,
                                            useRangeConstraint = viewModel.useRangeConstraint,
                                            selectedTag = selectedTagConstraint,
                                            rangeStart = start,
                                            rangeEnd = end
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .testTag("start_quiz_session_btn")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("この設定で学習を開始する", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedGroupCard(
    groupName: String,
    totalCount: Int,
    round: Int,
    compact: Boolean,
    onEditGroup: () -> Unit,
    onResetStats: () -> Unit,
    onDeleteGroup: () -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (compact) {
                AdaptiveSingleLineText(
                    text = "選択中: $groupName",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AdaptiveSingleLineText(
                        text = "選択中: $groupName",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    RoundBadge(round)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "収録単語数: ${totalCount}語",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f)
                )
                if (compact) RoundBadge(round)
            }
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onEditGroup) { Icon(Icons.Default.Edit, "単語帳の編集") }
                IconButton(onClick = onExportCsv) { Icon(Icons.Default.Share, "CSVエクスポート") }
                IconButton(onClick = onResetStats) { Icon(Icons.Default.Refresh, "成績初期化") }
                IconButton(onClick = onDeleteGroup) {
                    Icon(Icons.Default.Delete, "単語帳削除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SimpleProgressCard(progressPercent: Int, modifier: Modifier = Modifier) {
    Card(modifier = modifier, elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("学習進捗度", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "$progressPercent%",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progressPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp))
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailedProgressCard(
    total: Int,
    learned: Int,
    vague: Int,
    review: Int,
    learnedPercent: Int,
    vaguePercent: Int,
    reviewPercent: Int,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("学習進捗度・成績ステータス", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("学習済: ${learned}語 (${learnedPercent}%)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("うろ覚え: ${vague}語 (${vaguePercent}%)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                Text("要復習/未学習: ${review}語 (${reviewPercent}%)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp))) {
                if (total == 0) {
                    Box(Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.3f)))
                } else {
                    if (learned > 0) Box(Modifier.weight(learned.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                    if (vague > 0) Box(Modifier.weight(vague.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                    if (review > 0) Box(Modifier.weight(review.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.errorContainer))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "※うろ覚えは薄い紫（学習1回）、学習済は濃い紫（学習2回以上かつ前回正解）、要復習/未学習は赤で表示します。",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudySettingsContent(
    selectedGroup: StudyGroup,
    totalCount: Int,
    uniqueTags: List<String>,
    compact: Boolean,
    studyDirectionForward: Boolean,
    studyMultipleChoice: Boolean,
    filterMode: String,
    selectedTagConstraint: String,
    rangeStartText: String,
    rangeEndText: String,
    onRangeStartChange: (String) -> Unit,
    onRangeEndChange: (String) -> Unit,
    viewModel: com.example.viewmodel.MainViewModel
) {
    val shortLanguage = StudyLanguage.fromCode(selectedGroup.language).shortName
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("出題方向", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (!studyMultipleChoice) Text("※タイピングは日→${shortLanguage}固定です", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = studyDirectionForward,
                    onClick = { if (studyMultipleChoice) viewModel.studyDirectionForward = true },
                    label = { Text("順方向 (${shortLanguage}→日)") },
                    enabled = studyMultipleChoice
                )
                FilterChip(
                    selected = !studyDirectionForward,
                    onClick = { viewModel.studyDirectionForward = false },
                    label = { Text("逆方向 (日→${shortLanguage})") }
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("解答形式", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(studyMultipleChoice, { viewModel.studyMultipleChoice = true }, label = { Text("4択クイズ") })
                FilterChip(!studyMultipleChoice, { viewModel.studyMultipleChoice = false }, label = { Text("タイピング") })
            }
        }

        Text("出題対象・成績条件 (成績管理)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StudyFilterMode.options.forEach { (code, title) ->
                FilterChip(
                    selected = filterMode == code,
                    onClick = { viewModel.filterMode = code },
                    label = { Text(title, fontSize = 11.sp) }
                )
            }
        }

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
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
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("範囲・品詞タグ指定", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("出題範囲", fontSize = 12.sp)
                        OutlinedTextField(
                            value = rangeStartText,
                            onValueChange = onRangeStartChange,
                            modifier = Modifier.width(76.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Text("〜", fontSize = 12.sp)
                        OutlinedTextField(
                            value = rangeEndText,
                            onValueChange = onRangeEndChange,
                            modifier = Modifier.width(76.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Text("番目 (最大$totalCount)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (uniqueTags.size > 1) {
                        var expanded by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("品詞・分類タグ", fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Box {
                                Button(onClick = { expanded = true }) { Text(selectedTagConstraint, fontSize = 11.sp) }
                                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                                    uniqueTags.forEach { tag ->
                                        DropdownMenuItem(
                                            text = { Text(tag) },
                                            onClick = {
                                                viewModel.selectedTagConstraint = tag
                                                expanded = false
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
    }
}

private fun percent(value: Int, total: Int): Int = if (total == 0) 0 else (value * 100f / total).toInt()
