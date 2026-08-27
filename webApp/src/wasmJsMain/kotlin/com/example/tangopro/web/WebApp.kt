package com.example.tangopro.web

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tangopro.domain.StudyFilterMode
import com.example.tangopro.domain.StudyLanguage
import com.example.tangopro.domain.StudyRound
import com.example.tangopro.model.StudyGroupRecord
import com.example.tangopro.model.WordRecord
import com.example.tangopro.web.generated.resources.Res
import com.example.tangopro.web.generated.resources.tango_pro_unicode
import org.jetbrains.compose.resources.Font

private val TangoLight = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF386A20),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFD9F7D8),
    onSecondaryContainer = Color.Black,
    background = Color(0xFFF9F7FC),
    onBackground = Color.Black,
    surface = Color(0xFFFFFBFF),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color.Black,
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color.Black,
)

private val TangoDark = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFA5D58C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF143A1D),
    onSecondaryContainer = Color.White,
    background = Color(0xFF121116),
    onBackground = Color.White,
    surface = Color(0xFF1A191E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color.White,
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF4A1717),
    onErrorContainer = Color.White,
)

@Composable
private fun neutralOutlinedButtonColors() = ButtonDefaults.outlinedButtonColors(
    contentColor = MaterialTheme.colorScheme.onSurface,
    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
)

@Composable
private fun neutralTextButtonColors() = ButtonDefaults.textButtonColors(
    contentColor = MaterialTheme.colorScheme.onSurface,
    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
)

@Composable
private fun neutralFilterChipColors() = FilterChipDefaults.filterChipColors(
    labelColor = MaterialTheme.colorScheme.onSurface,
    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
    disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
)

@Composable
fun TangoWebApp(state: WebAppState) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(it)
            state.clearNotice()
        }
    }
    TangoTheme(darkTheme = state.darkTheme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).safeContentPadding()) {
                when (state.screen) {
                    WebScreen.DASHBOARD -> Dashboard(state)
                    WebScreen.WORDS -> WordEditor(state)
                    WebScreen.STUDY -> StudyScreen(state)
                    WebScreen.SUMMARY -> SummaryScreen(state)
                }
                if (state.isBusy) BusyOverlay(state.bootstrapProgress)
            }
        }
        state.pendingCsvImport?.let { pending -> CsvImportDialog(state, pending) }
    }
}

@Composable
fun DatabaseLockedScreen() {
    TangoTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp), Alignment.Center) {
            Card(Modifier.widthIn(max = 560.dp)) {
                Column(Modifier.padding(28.dp), Arrangement.spacedBy(12.dp), Alignment.CenterHorizontally) {
                    Text("Tango pro は別のタブで開いています", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("学習記録を安全に保存するため、同時に開けるタブは1つです。既存のタブへ戻るか、そのタブを閉じてから再読み込みしてください。", textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun TangoTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    // A generated OFL derivative covering CJK, extended Latin, math, and common
    // symbols used by arbitrary UTF-8 CSV vocabulary.
    val fontFamily = FontFamily(Font(Res.font.tango_pro_unicode))
    val colors = if (darkTheme) TangoDark else TangoLight
    MaterialTheme(colorScheme = colors) {
        val base = MaterialTheme.typography
        val typography = base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
            displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
            displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
            headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
            headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
            headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
            titleLarge = base.titleLarge.copy(fontFamily = fontFamily),
            titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
            titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
            bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily),
            bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
            bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
            labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
            labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
            labelSmall = base.labelSmall.copy(fontFamily = fontFamily),
            displayLargeEmphasized = base.displayLargeEmphasized.copy(fontFamily = fontFamily),
            displayMediumEmphasized = base.displayMediumEmphasized.copy(fontFamily = fontFamily),
            displaySmallEmphasized = base.displaySmallEmphasized.copy(fontFamily = fontFamily),
            headlineLargeEmphasized = base.headlineLargeEmphasized.copy(fontFamily = fontFamily),
            headlineMediumEmphasized = base.headlineMediumEmphasized.copy(fontFamily = fontFamily),
            headlineSmallEmphasized = base.headlineSmallEmphasized.copy(fontFamily = fontFamily),
            titleLargeEmphasized = base.titleLargeEmphasized.copy(fontFamily = fontFamily),
            titleMediumEmphasized = base.titleMediumEmphasized.copy(fontFamily = fontFamily),
            titleSmallEmphasized = base.titleSmallEmphasized.copy(fontFamily = fontFamily),
            bodyLargeEmphasized = base.bodyLargeEmphasized.copy(fontFamily = fontFamily),
            bodyMediumEmphasized = base.bodyMediumEmphasized.copy(fontFamily = fontFamily),
            bodySmallEmphasized = base.bodySmallEmphasized.copy(fontFamily = fontFamily),
            labelLargeEmphasized = base.labelLargeEmphasized.copy(fontFamily = fontFamily),
            labelMediumEmphasized = base.labelMediumEmphasized.copy(fontFamily = fontFamily),
            labelSmallEmphasized = base.labelSmallEmphasized.copy(fontFamily = fontFamily),
        )
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}

@Composable
private fun BusyOverlay(label: String) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f)),
        contentAlignment = Alignment.Center,
    ) {
        Card {
            Row(Modifier.padding(22.dp), Arrangement.spacedBy(14.dp), Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Text(label, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Dashboard(state: WebAppState) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 900.dp) {
            Row(Modifier.fillMaxSize()) {
                GroupRail(state, Modifier.width(310.dp).fillMaxHeight())
                DashboardContent(state, Modifier.weight(1f))
            }
        } else {
            var showGroups by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically,
                ) {
                    Text("Tango pro", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                    OutlinedButton(onClick = { showGroups = !showGroups }, colors = neutralOutlinedButtonColors()) { Text(if (showGroups) "閉じる" else "単語帳") }
                }
                if (showGroups) GroupRail(state, Modifier.fillMaxSize())
                else DashboardContent(state, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun GroupRail(state: WebAppState, modifier: Modifier = Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("Tango pro", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                Text("端末内で学ぶ単語帳", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = state::importCsv, colors = neutralTextButtonColors()) { Text("＋ CSV") }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.groups, key = { it.id }) { group ->
                val selected = group.id == state.selectedGroup?.id
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { state.selectGroup(group) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
                        Text(StudyLanguage.fromCode(group.language).shortName, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                        Column(Modifier.weight(1f)) {
                            Text(group.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                            Text(StudyRound.label(state.rounds[group.id] ?: 1), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(state: WebAppState, modifier: Modifier = Modifier) {
    val group = state.selectedGroup
    if (group == null) {
        Box(modifier.padding(24.dp), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("単語帳がありません", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Button(onClick = state::importCsv) { Text("CSVから単語帳を追加") }
            }
        }
        return
    }
    var showSettings by remember { mutableStateOf(false) }
    var showGroupEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showCombine by remember { mutableStateOf(false) }
    var showStudyOptions by remember(group.id) { mutableStateOf(!state.simpleMode) }
    LaunchedEffect(state.simpleMode) { if (state.simpleMode) showStudyOptions = false }
    Column(modifier.verticalScroll(rememberScrollState()).padding(18.dp), Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(group.name, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                Text("${state.words.size}語・${StudyRound.label(state.currentRound)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { showGroupEdit = true }, colors = neutralTextButtonColors()) { Text("編集") }
            TextButton(onClick = { showSettings = true }, colors = neutralTextButtonColors()) { Text("設定") }
        }
        if (state.simpleMode) {
            SimpleProgressCard(state)
            OutlinedButton(onClick = { showStudyOptions = !showStudyOptions }, modifier = Modifier.fillMaxWidth(), colors = neutralOutlinedButtonColors()) {
                Text(if (showStudyOptions) "出題設定を閉じる" else "出題設定を開く")
            }
            if (showStudyOptions) StudyOptions(state)
        } else {
            ProgressCard(state)
            StudyOptions(state)
        }
        Button(
            onClick = state::startStudy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
            enabled = state.words.isNotEmpty(),
        ) { Text("学習を始める", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = state::showWords, colors = neutralOutlinedButtonColors()) { Text("単語一覧・編集") }
            OutlinedButton(onClick = { showCombine = true }, colors = neutralOutlinedButtonColors()) { Text("単語帳を連結") }
            OutlinedButton(onClick = { state.exportCsv(false) }, colors = neutralOutlinedButtonColors()) { Text("CSV保存") }
            OutlinedButton(onClick = { state.exportCsv(true) }, colors = neutralOutlinedButtonColors()) { Text("共有") }
            OutlinedButton(onClick = state::exportStudyArchive, colors = neutralOutlinedButtonColors()) { Text("学習記録ZIP保存") }
            OutlinedButton(onClick = state::importStudyArchive, colors = neutralOutlinedButtonColors()) { Text("学習記録ZIP読込") }
            OutlinedButton(onClick = { showReset = true }, colors = neutralOutlinedButtonColors()) { Text("進捗リセット") }
            OutlinedButton(onClick = { showDelete = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("単語帳を削除") }
        }
        StorageCard(state)
        ExternalWebsiteFooter()
    }
    if (showSettings) SettingsDialog(state) { showSettings = false }
    if (showGroupEdit) GroupEditDialog(state, group) { showGroupEdit = false }
    if (showDelete) ConfirmDialog("単語帳を削除", "「${group.name}」と学習記録を削除します。この操作は元に戻せません。", {
        showDelete = false; state.deleteSelectedGroup()
    }) { showDelete = false }
    if (showReset) ConfirmDialog("学習記録をリセット", "この単語帳の正誤・周回記録を0に戻します。", {
        showReset = false; state.resetProgress()
    }) { showReset = false }
    if (showCombine) CombineDialog(state) { showCombine = false }
}

@Composable
private fun ExternalWebsiteFooter() {
    // Stay in the dashboard's scroll flow; never cover the study controls.
    Column(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp), Alignment.CenterHorizontally) {
        OutlinedButton(
            onClick = { openExternalWebsite("https://studio-rizi.pages.dev/") },
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(min = 48.dp),
            colors = neutralOutlinedButtonColors(),
        ) {
            Text("開発者サイトを開く", textAlign = TextAlign.Center)
        }
        OutlinedButton(
            onClick = { openExternalWebsite("https://studio-rizi.pages.dev/projects/tango-pro/") },
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(min = 48.dp),
            colors = neutralOutlinedButtonColors(),
        ) {
            Text("Tango pro ホームページを開く", textAlign = TextAlign.Center)
        }
        Text("新しいタブで開きます", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ProgressCard(state: WebAppState) {
    val total = state.words.size.coerceAtLeast(1)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(18.dp), Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("学習状況", fontWeight = FontWeight.ExtraBold)
                Text("${state.learned * 100 / total}%", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            }
            LinearProgressIndicator(progress = { state.learned.toFloat() / total }, Modifier.fillMaxWidth().height(10.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                Stat("学習済", state.learned, Color(0xFF2E7D32))
                Stat("うろ覚え", state.vague, Color(0xFFE09200))
                Stat("復習", state.review, MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SimpleProgressCard(state: WebAppState) {
    val total = state.words.size.coerceAtLeast(1)
    val studied = state.words.count { it.studyCount > 0 }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(18.dp), Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("学習進捗", fontWeight = FontWeight.ExtraBold)
                Text("${studied * 100 / total}%", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            }
            LinearProgressIndicator(progress = { studied.toFloat() / total }, Modifier.fillMaxWidth().height(12.dp))
            Text("$studied / ${state.words.size}語を学習", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Stat(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 11.sp)
    }
}

@Composable
private fun StudyOptions(state: WebAppState) {
    Card {
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(14.dp)) {
            Text("出題設定", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text("回答方法", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(state.multipleChoice, { state.setMultipleChoice(true) }, { Text("4択") }, colors = neutralFilterChipColors())
                FilterChip(!state.multipleChoice, { state.setMultipleChoice(false) }, { Text("タイピング") }, colors = neutralFilterChipColors())
            }
            Text("出題方向", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(state.directionForward, { state.setDirection(true) }, { Text("対象言語 → 日本語") }, enabled = state.multipleChoice, colors = neutralFilterChipColors())
                FilterChip(!state.directionForward, { state.setDirection(false) }, { Text("日本語 → 対象言語") }, colors = neutralFilterChipColors())
            }
            DropdownSelector(
                label = "出題条件",
                selected = StudyFilterMode.options.first { it.id == state.filterMode }.label,
                options = StudyFilterMode.options.map { it.id to it.label },
                onSelect = state::setFilter,
            )
            DropdownSelector("タグ", state.selectedTag, state.tags.map { it to it }, state::setTag)
            Text("問題数", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 20, 50, 100_000).forEach { count ->
                    FilterChip(state.quizCount == count, { state.setQuizCount(count) }, { Text(if (count == 100_000) "すべて" else "$count") }, colors = neutralFilterChipColors())
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.useRange,
                    onCheckedChange = state::setUseRange,
                    modifier = Modifier.semantics { contentDescription = "出題範囲を指定" },
                )
                Text("出題範囲を指定")
            }
            if (state.useRange) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(state.rangeStart, state::setRangeStart, Modifier.weight(1f).semantics { contentDescription = "出題範囲の開始" }, label = { Text("開始") }, singleLine = true)
                    OutlinedTextField(state.rangeEnd, state::setRangeEnd, Modifier.weight(1f).semantics { contentDescription = "出題範囲の終了" }, label = { Text("終了（空欄=末尾）") }, singleLine = true)
                }
            }
        }
    }
}

@Composable
private fun DropdownSelector(label: String, selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label: $selected" },
                colors = neutralOutlinedButtonColors(),
            ) { Text(selected, Modifier.weight(1f)); Text("▼") }
            DropdownMenu(expanded, { expanded = false }, modifier = Modifier.widthIn(min = 240.dp)) {
                options.forEach { (id, text) -> DropdownMenuItem({ Text(text) }, { onSelect(id); expanded = false }) }
            }
        }
    }
}

@Composable
private fun StorageCard(state: WebAppState) {
    OutlinedCard {
        Column(Modifier.padding(14.dp), Arrangement.spacedBy(4.dp)) {
            Text("保存先: この端末のブラウザ (SQLite / OPFS)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                when (state.persistentStorage) {
                    true -> "永続ストレージが許可されています。"
                    false -> "ブラウザの容量整理で削除される可能性があります。大切な単語帳はCSVでも保存してください。"
                    null -> "保存状態を確認しています。"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsDialog(state: WebAppState, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        confirmButton = { TextButton(onClick = dismiss, colors = neutralTextButtonColors()) { Text("閉じる") } },
        title = { Text("表示・音声設定") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), Arrangement.spacedBy(12.dp)) {
                SettingSwitch("ダークテーマ", state.darkTheme, state::setDarkTheme)
                SettingSwitch("シンプルモード", state.simpleMode, state::setSimpleMode)
                SettingSwitch("問題画面を均等配置", state.evenStudyLayout, state::setEvenStudyLayout)
                SettingSwitch("問題を読み上げる", state.ttsEnabled, state::setTtsEnabled)
                Text("問題文字サイズ ${(state.textScale * 100).toInt()}%")
                Slider(state.textScale, state::setTextScale, Modifier.semantics { contentDescription = "問題文字サイズ" }, valueRange = 0.8f..1.4f, steps = 2)
                Text("読み上げ音量 ${(state.ttsVolume * 100).toInt()}%")
                Slider(state.ttsVolume.toFloat(), { state.setTtsVolume(it.toDouble()) }, Modifier.semantics { contentDescription = "読み上げ音量" })
                Text("効果音量 ${(state.soundVolume * 100).toInt()}%")
                Slider(state.soundVolume.toFloat(), { state.setSoundVolume(it.toDouble()) }, Modifier.semantics { contentDescription = "効果音量" })
            }
        },
    )
}

@Composable
private fun CsvImportDialog(state: WebAppState, pending: PendingCsvImport) {
    var name by remember(pending) { mutableStateOf(pending.suggestedName) }
    var language by remember(pending) { mutableStateOf("en") }
    AlertDialog(
        onDismissRequest = state::cancelCsvImport,
        confirmButton = {
            TextButton(onClick = { state.confirmCsvImport(name, language) }, enabled = name.isNotBlank(), colors = neutralTextButtonColors()) { Text("追加") }
        },
        dismissButton = { TextButton(onClick = state::cancelCsvImport, colors = neutralTextButtonColors()) { Text("キャンセル") } },
        title = { Text("CSV単語帳を追加") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(200) }, Modifier.semantics { contentDescription = "単語帳名" }, label = { Text("単語帳名") }, singleLine = true)
                DropdownSelector("読み上げ言語", StudyLanguage.fromCode(language).displayName, StudyLanguage.supported.map { it.code to it.displayName }) { language = it }
            }
        },
    )
}

@Composable
private fun CombineDialog(state: WebAppState, dismiss: () -> Unit) {
    var selected by remember { mutableStateOf(setOfNotNull(state.selectedGroup?.id)) }
    var name by remember { mutableStateOf("連結単語帳") }
    var language by remember { mutableStateOf(state.selectedGroup?.language ?: "en") }
    AlertDialog(
        onDismissRequest = dismiss,
        confirmButton = {
            TextButton(onClick = { state.combineGroups(selected.toList(), name, language); dismiss() }, enabled = selected.size >= 2 && name.isNotBlank(), colors = neutralTextButtonColors()) { Text("連結") }
        },
        dismissButton = { TextButton(onClick = dismiss, colors = neutralTextButtonColors()) { Text("キャンセル") } },
        title = { Text("単語帳を連結") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
                Text("連結する単語帳を2冊以上選択してください。", fontSize = 12.sp)
                state.groups.forEach { group ->
                    Row(Modifier.fillMaxWidth().clickable {
                        selected = if (group.id in selected) selected - group.id else selected + group.id
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = group.id in selected,
                            onCheckedChange = { checked -> selected = if (checked) selected + group.id else selected - group.id },
                            modifier = Modifier.semantics { contentDescription = "${group.name}を選択" },
                        )
                        Text(group.name, Modifier.weight(1f))
                    }
                }
                OutlinedTextField(name, { name = it.take(200) }, Modifier.semantics { contentDescription = "新しい単語帳名" }, label = { Text("新しい単語帳名") }, singleLine = true)
                DropdownSelector("読み上げ言語", StudyLanguage.fromCode(language).displayName, StudyLanguage.supported.map { it.code to it.displayName }) { language = it }
            }
        },
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = if (checked) "オン" else "オフ"
            },
        )
    }
}

@Composable
private fun GroupEditDialog(state: WebAppState, group: StudyGroupRecord, dismiss: () -> Unit) {
    var name by remember(group.id) { mutableStateOf(group.name) }
    var language by remember(group.id) { mutableStateOf(group.language) }
    AlertDialog(
        onDismissRequest = dismiss,
        confirmButton = { TextButton(onClick = { state.renameGroup(name, language); dismiss() }, enabled = name.isNotBlank(), colors = neutralTextButtonColors()) { Text("保存") } },
        dismissButton = { TextButton(onClick = dismiss, colors = neutralTextButtonColors()) { Text("キャンセル") } },
        title = { Text("単語帳を編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(200) }, Modifier.semantics { contentDescription = "単語帳名" }, label = { Text("単語帳名") }, singleLine = true)
                DropdownSelector("読み上げ言語", StudyLanguage.fromCode(language).displayName, StudyLanguage.supported.map { it.code to it.displayName }) { language = it }
            }
        },
    )
}

@Composable
private fun ConfirmDialog(title: String, message: String, confirm: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        confirmButton = { TextButton(onClick = confirm, colors = neutralTextButtonColors()) { Text("実行", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = dismiss, colors = neutralTextButtonColors()) { Text("キャンセル") } },
        title = { Text(title) },
        text = { Text(message) },
    )
}

@Composable
private fun WordEditor(state: WebAppState) {
    var editing by remember { mutableStateOf<WordRecord?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
            OutlinedButton(onClick = state::showDashboard, colors = neutralOutlinedButtonColors()) { Text("← 戻る") }
            Column(Modifier.weight(1f)) {
                Text(state.selectedGroup?.name.orEmpty(), fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${state.words.size}語", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { state.exportCsv(false) }, colors = neutralOutlinedButtonColors()) { Text("CSV保存") }
        }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(state.words, key = { it.id }) { word ->
                OutlinedCard(Modifier.fillMaxWidth().clickable { editing = word }) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
                        Text(word.term, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(word.meaning, Modifier.weight(1f))
                        if (word.tag.isNotBlank()) Text(word.tag, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("編集", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                    }
                }
            }
        }
    }
    editing?.let { word -> WordEditDialog(word, { editing = null }) { updated -> state.updateWord(updated); editing = null } }
}

@Composable
private fun WordEditDialog(word: WordRecord, dismiss: () -> Unit, save: (WordRecord) -> Unit) {
    var term by remember(word.id) { mutableStateOf(word.term) }
    var meaning by remember(word.id) { mutableStateOf(word.meaning) }
    var tag by remember(word.id) { mutableStateOf(word.tag) }
    var pronunciation by remember(word.id) { mutableStateOf(word.pronunciation) }
    AlertDialog(
        onDismissRequest = dismiss,
        confirmButton = { TextButton(onClick = { save(word.copy(term = term.trim(), meaning = meaning.trim(), tag = tag.trim(), pronunciation = pronunciation.trim())) }, enabled = term.isNotBlank() && meaning.isNotBlank(), colors = neutralTextButtonColors()) { Text("保存") } },
        dismissButton = { TextButton(onClick = dismiss, colors = neutralTextButtonColors()) { Text("キャンセル") } },
        title = { Text("単語を編集") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(term, { term = it }, Modifier.semantics { contentDescription = "対象言語" }, label = { Text("対象言語") })
                OutlinedTextField(meaning, { meaning = it }, Modifier.semantics { contentDescription = "日本語" }, label = { Text("日本語") })
                OutlinedTextField(tag, { tag = it }, Modifier.semantics { contentDescription = "タグ" }, label = { Text("タグ") })
                OutlinedTextField(pronunciation, { pronunciation = it }, Modifier.semantics { contentDescription = "発音" }, label = { Text("発音") })
            }
        },
    )
}

@Composable
private fun StudyScreen(state: WebAppState) {
    val question = state.questions.getOrNull(state.currentIndex) ?: return
    var typed by remember(state.currentIndex) { mutableStateOf("") }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 600.dp
        val tightPhone = phone && maxHeight < 700.dp
        val edgePadding = when {
            tightPhone -> 8.dp
            phone -> 10.dp
            else -> 16.dp
        }
        val sectionSpacing = when {
            tightPhone -> 6.dp
            phone -> 8.dp
            else -> 12.dp
        }
        Column(Modifier.fillMaxSize().padding(edgePadding), Arrangement.spacedBy(sectionSpacing)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("${state.currentIndex + 1} / ${state.questions.size}", fontWeight = FontWeight.Bold)
                Text("正解 ${state.score}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                TextButton(onClick = state::stopStudy, colors = neutralTextButtonColors()) { Text("中断", color = MaterialTheme.colorScheme.error) }
            }
            LinearProgressIndicator({ (state.currentIndex + 1f) / state.questions.size }, Modifier.fillMaxWidth())
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth >= 760.dp && maxWidth > maxHeight
                if (wide) {
                    Row(Modifier.fillMaxSize(), Arrangement.spacedBy(14.dp)) {
                        QuestionCard(state, question.questionText, question.word.tag, modifier = Modifier.weight(1f).fillMaxHeight())
                        AnswerArea(
                            state,
                            typed,
                            { typed = it },
                            compactWide = true,
                            compactPhone = false,
                            showNextButton = true,
                            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        )
                    }
                } else {
                    val questionHeight = when {
                        tightPhone && state.checked -> (
                            (maxHeight * 0.15f).coerceIn(72.dp, 88.dp) +
                                ((state.textScale - 1f).coerceAtLeast(0f) * 30).dp
                            ).coerceAtMost(100.dp)
                        phone && state.checked -> (maxHeight * 0.22f).coerceIn(112.dp, 150.dp)
                        tightPhone -> (maxHeight * 0.25f).coerceIn(118.dp, 150.dp)
                        phone -> (maxHeight * 0.30f).coerceIn(150.dp, 210.dp)
                        else -> 240.dp
                    }
                    Column(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = if (!phone && state.evenStudyLayout) Arrangement.SpaceEvenly else Arrangement.spacedBy(if (tightPhone) 5.dp else if (phone) 8.dp else 14.dp),
                        ) {
                            QuestionCard(
                                state,
                                question.questionText,
                                question.word.tag,
                                compact = phone && (tightPhone || state.checked),
                                showHint = !(tightPhone && state.checked),
                                modifier = Modifier.fillMaxWidth().height(questionHeight),
                            )
                            AnswerArea(
                                state,
                                typed,
                                { typed = it },
                                compactWide = false,
                                compactPhone = phone,
                                tightPhone = tightPhone,
                                showNextButton = false,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (state.checked) {
                            Spacer(Modifier.height(if (tightPhone) 3.dp else if (phone) 6.dp else 10.dp))
                            NextQuestionButton(state, compact = phone)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(
    state: WebAppState,
    text: String,
    tag: String,
    compact: Boolean = false,
    showHint: Boolean = true,
    modifier: Modifier,
) {
    Card(modifier.clickable { state.speakCurrent() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxSize().padding(if (compact) 6.dp else 16.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            if (tag.isNotBlank()) Text(tag, fontSize = if (compact) 10.sp else 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(if (compact) 3.dp else 10.dp))
            Text(
                text,
                fontSize = ((if (compact) 29 else 34) * state.textScale).sp,
                lineHeight = ((if (compact) 34 else 42) * state.textScale).sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (showHint) {
                Spacer(Modifier.height(if (compact) 4.dp else 12.dp))
                Text("タップして読み上げ", fontSize = if (compact) 9.sp else 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AnswerArea(
    state: WebAppState,
    typed: String,
    setTyped: (String) -> Unit,
    compactWide: Boolean,
    compactPhone: Boolean,
    tightPhone: Boolean = false,
    showNextButton: Boolean,
    modifier: Modifier,
) {
    val question = state.questions[state.currentIndex]
    val compact = compactWide || compactPhone
    Column(modifier, Arrangement.spacedBy(if (tightPhone) 5.dp else if (compact) 8.dp else 10.dp)) {
        if (question.isMultipleChoice) {
            if (compactWide) {
                question.choices.chunked(2).forEach { choices ->
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                        choices.forEach { choice ->
                            AnswerChoice(state, choice, question.correctAnswer, compact = true, modifier = Modifier.weight(1f))
                        }
                        if (choices.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                question.choices.forEach { choice ->
                    AnswerChoice(state, choice, question.correctAnswer, compact = compactPhone, modifier = Modifier.fillMaxWidth())
                }
            }
        } else if (!state.checked) {
            OutlinedTextField(
                typed,
                setTyped,
                Modifier.fillMaxWidth().semantics { contentDescription = "解答を入力" },
                label = { Text("解答を入力") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { state.submitAnswer(typed) }),
            )
            Button({ state.submitAnswer(typed) }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = typed.isNotBlank()) { Text("判定する") }
        }
        if (state.checked) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (state.correct) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(if (tightPhone) 7.dp else if (compact) 10.dp else 16.dp),
                    Arrangement.spacedBy(if (tightPhone) 2.dp else if (compact) 3.dp else 5.dp),
                    Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (state.correct) "正解！" else "不正解",
                        fontSize = if (tightPhone) 17.sp else if (compact) 18.sp else 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (state.correct) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "あなたの回答: ${state.answer}",
                        fontSize = if (tightPhone) 13.sp else if (compact) 14.sp else 16.sp,
                        lineHeight = if (tightPhone) 16.sp else if (compact) 20.sp else 24.sp,
                        maxLines = if (compactWide) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "正解: ${question.correctAnswer}",
                        fontSize = if (tightPhone) 13.sp else if (compact) 14.sp else 16.sp,
                        lineHeight = if (tightPhone) 16.sp else if (compact) 20.sp else 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = if (compactWide) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showNextButton) NextQuestionButton(state, compact)
        }
    }
}

@Composable
private fun AnswerChoice(state: WebAppState, choice: String, correctAnswer: String, compact: Boolean, modifier: Modifier) {
    val containerColor = when {
        state.checked && choice == correctAnswer -> MaterialTheme.colorScheme.secondaryContainer
        state.checked && choice == state.answer && !state.correct -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Button(
        onClick = { state.submitAnswer(choice) },
        enabled = !state.checked,
        modifier = modifier.heightIn(min = if (compact) 48.dp else 54.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = containerColor,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(choice, fontSize = ((if (compact) 14 else 15) * state.textScale).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = if (compact) 2 else 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NextQuestionButton(state: WebAppState, compact: Boolean) {
    Button(state::nextQuestion, Modifier.fillMaxWidth().heightIn(min = if (compact) 48.dp else 54.dp)) {
        Text("次へ →", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SummaryScreen(state: WebAppState) {
    Box(Modifier.fillMaxSize().padding(22.dp), Alignment.Center) {
        Card(Modifier.widthIn(max = 620.dp).fillMaxWidth()) {
            Column(Modifier.padding(28.dp), Arrangement.spacedBy(14.dp), Alignment.CenterHorizontally) {
                Text("学習完了", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text("${state.score} / ${state.questions.size}", fontSize = 43.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                Text("正答率 ${state.score * 100 / state.questions.size.coerceAtLeast(1)}%")
                Button(state::startStudy, Modifier.fillMaxWidth()) { Text("同じ条件でもう一度") }
                OutlinedButton(state::showDashboard, Modifier.fillMaxWidth(), colors = neutralOutlinedButtonColors()) { Text("ダッシュボードへ") }
            }
        }
    }
}
