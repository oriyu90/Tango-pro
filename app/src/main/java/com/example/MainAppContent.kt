package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.CsvExporter
import com.example.data.StudyGroup
import com.example.domain.StudyArrangementMode
import com.example.domain.StudyLanguage
import com.example.domain.StudyRound
import com.example.service.SoundPlayer
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val groups by viewModel.groups.collectAsState(initial = emptyList())
    val groupRounds by viewModel.groupRounds.collectAsState(initial = emptyMap())
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val words by viewModel.selectedGroupWords.collectAsState()

    var showUriImportDialog by rememberSaveable { mutableStateOf(false) }
    var showTextImportDialog by rememberSaveable { mutableStateOf(false) }
    var showCombineDialog by rememberSaveable { mutableStateOf(false) }
    var showEditGroupDialog by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showResetConfirmation by rememberSaveable { mutableStateOf(false) }
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var groupToDelete by remember { mutableStateOf<StudyGroup?>(null) }

    var temporaryImportName by remember { mutableStateOf("") }
    var temporaryImportLanguage by remember { mutableStateOf("en") }
    var pendingBackupJson by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel.pendingImportUri) {
        if (viewModel.pendingImportUri != null) {
            showUriImportDialog = true
        }
    }

    val jsonImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importSaveDataJson(context, uri) { success, message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }
    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val json = pendingBackupJson
        pendingBackupJson = null
        if (uri != null && json != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("出力先を開けませんでした")
                Toast.makeText(context, "バックアップを書き出しました", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "バックアップの書き出しに失敗しました", Toast.LENGTH_LONG).show()
            }
        }
    }
    val studyArchiveImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importStudyArchive(context, uri) { _, message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }
    val studyArchiveExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportStudyArchive(context, uri) { _, message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }
    val csvFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importCsvFromUri(context, uri, temporaryImportName, temporaryImportLanguage) { success, message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                if (success) {
                    showUriImportDialog = false
                    temporaryImportName = ""
                    temporaryImportLanguage = "en"
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.9f).widthIn(max = 360.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "CSV単語帳リスト",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "成績は各単語帳ごとに個別に保存・管理されます。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (groups.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "情報",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "インポートされたCSVがありません。右下のボタンかテキスト貼付から最初のデータを作成してください。",
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            items(groups) { group ->
                                val isSelected = selectedGroup?.id == group.id
                                val round = groupRounds[group.id] ?: 1
                                val roundTint = if (StudyRound.visualTier(round) in 2..4) {
                                    roundAccentColor(round).copy(alpha = if (isSelected) 0.18f else 0.1f)
                                } else {
                                    Color.Transparent
                                }
                                var dragOffset by remember { mutableStateOf(0f) }
                                NavigationDrawerItem(
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = "並び替え",
                                            modifier = Modifier.pointerInput(group) {
                                                detectDragGestures(
                                                    onDragEnd = { dragOffset = 0f },
                                                    onDragCancel = { dragOffset = 0f }
                                                ) { change, dragAmount ->
                                                    change.consume()
                                                    dragOffset += dragAmount.y
                                                    if (dragOffset > 80f) {
                                                        viewModel.moveGroupDown(group)
                                                        dragOffset = 0f
                                                    } else if (dragOffset < -80f) {
                                                        viewModel.moveGroupUp(group)
                                                        dragOffset = 0f
                                                    }
                                                }
                                            }
                                        )
                                    },
                                    label = {
                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            AdaptiveSingleLineText(
                                                text = group.name,
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            RoundBadge(round)
                                        }
                                    },
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.selectGroup(group)
                                        scope.launch { drawerState.close() }
                                    },

                                    badge = {
                                        IconButton(
                                            onClick = {
                                                groupToDelete = group
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "単語帳削除",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(roundTint)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showTextImportDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("add_from_text_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("テキスト貼付で追加", fontSize = 13.sp)
                    }

                    Button(
                        onClick = { showUriImportDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("add_from_file_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CSVファイルから選択", fontSize = 13.sp)
                    }

                    Button(
                        onClick = { showCombineDialog = true },
                        enabled = groups.size >= 2,
                        modifier = Modifier.fillMaxWidth().testTag("combine_csv_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("複数のCSVを連結する", fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Tango pro",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("menu_drawer_button")
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "メニューを開く")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "アプリ設定")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (viewModel.isActiveSession) {
                    StudySessionScreen(viewModel)
                } else if (selectedGroup != null) {
                    DashboardScreen(
                        selectedGroup = selectedGroup!!,
                        words = words,
                        viewModel = viewModel,
                        onEditGroup = { showEditGroupDialog = true },
                        onResetStats = { showResetConfirmation = true },
                        onDeleteGroup = { groupToDelete = selectedGroup },
                        onExportCsv = { showExportDialog = true }
                    )
                } else {
                    EmptyStateScreen(
                        onPasteClick = { showTextImportDialog = true },
                        onFileClick = { showUriImportDialog = true }
                    )
                }
            }
        }
    }

    // --- POPUP DIALOGS ---

    if (showExportDialog && selectedGroup != null) {
        val activeGroup = selectedGroup!!
        val currentWords = words
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Text(
                    text = "CSVエクスポート・書き出し",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "「${activeGroup.name}」（${currentWords.size}単語）データをCSV形式（UTF-8）で出力します。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "クリップボードへコピーして他のアプリに直接貼り付けるか、ファイルとして共有またはGoogleドライブなどに保存できます。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = {
                            val csvText = CsvExporter.serialize(currentWords)
                            try {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Tango pro CSV Export", csvText)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "CSVデータをクリップボードにコピーしました！", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "コピー失敗: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                            showExportDialog = false
                        }
                    ) {
                        Text("クリップボードにコピー", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            try {
                                val csvText = CsvExporter.serialize(currentWords)

                                val safeGroupName = activeGroup.name.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                                val filename = "Tango_pro_${safeGroupName}.csv"
                                val cacheFile = java.io.File(context.cacheDir, filename)
                                java.io.FileOutputStream(cacheFile).use { fos ->
                                    fos.write(csvText.toByteArray(Charsets.UTF_8))
                                }

                                val fileUri: Uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    cacheFile
                                )

                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, fileUri)
                                    val langName = StudyLanguage.fromCode(activeGroup.language).displayName
                                    putExtra(Intent.EXTRA_SUBJECT, "${activeGroup.name} の${langName}単語リスト (CSV)")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "CSVを保存・共有"))
                            } catch (e: Exception) {
                                Log.e("TangoPro", "CSV export failed", e)
                                val errorMsg = when (e) {
                                    is java.io.IOException -> "ファイルの保存中にエラーが発生しました"
                                    is java.lang.SecurityException -> "ファイルアクセスが拒否されました"
                                    else -> "エクスポート中にエラーが発生しました"
                                }
                                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                            }
                            showExportDialog = false
                        }
                    ) {
                        Icon(ExportCsvIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CSVファイルで共有", fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("キャンセル", fontSize = 12.sp)
                }
            }
        )
    }

    if (showTextImportDialog) {
        var rawText by remember { mutableStateOf("") }
        var folderName by remember { mutableStateOf("") }
        var selectedLanguage by remember { mutableStateOf("en") }

        Dialog(onDismissRequest = { showTextImportDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("テキストを直接貼り付けて追加", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "対象言語と日本語をカンマ(,)で区切って1行に入力します。品詞/タグは3番目に任意で入力できます。\n(例)\napple,りんご,名詞\nrun,走る,動詞",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        label = { Text("単語帳の名前") },
                        placeholder = { Text("例: 共通テスト頻出単語") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LanguageSelector(selectedLanguage) { selectedLanguage = it }

                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        label = { Text("CSVテキスト (1列:対象言語, 2列:日本語, 3列:タグ)") },
                        placeholder = { Text("dog,犬,名詞\nhappy,幸せな,形容詞") },
                        modifier = Modifier.fillMaxWidth().height(150.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showTextImportDialog = false }) {
                            Text("キャンセル")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.importCsvFromText(folderName, rawText, selectedLanguage) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    if (success) showTextImportDialog = false
                                }
                            },
                            enabled = rawText.isNotBlank()
                        ) {
                            Text("登録")
                        }
                    }
                }
            }
        }
    }

    if (showUriImportDialog) {
        var importName by remember { mutableStateOf("") }
        var selectedLanguage by remember { mutableStateOf("en") }
        Dialog(onDismissRequest = {
            showUriImportDialog = false
            viewModel.pendingImportUri = null
        }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("CSVファイルをインポート (Googleドライブ対応)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("端末のフォルダやGoogleドライブ等からCSVファイルを選択してインポートします。最大10万行のデータまで読み込みが可能です。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedTextField(
                        value = importName,
                        onValueChange = { importName = it },
                        label = { Text("単語帳の名前") },
                        placeholder = { Text("例: TOEIC英単語") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LanguageSelector(selectedLanguage) { selectedLanguage = it }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            showUriImportDialog = false
                            viewModel.pendingImportUri = null
                        }) {
                            Text("キャンセル")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            temporaryImportName = importName
                            temporaryImportLanguage = selectedLanguage
                            val sharedUri = viewModel.pendingImportUri
                            if (sharedUri != null) {
                                viewModel.pendingImportUri = null
                                viewModel.importCsvFromUri(context, sharedUri, importName, selectedLanguage) { success, message ->
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    if (success) showUriImportDialog = false
                                }
                                return@Button
                            }
                            try {
                                csvFileLauncher.launch(
                                    arrayOf(
                                        "text/comma-separated-values",
                                        "text/csv",
                                        "text/plain",
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                            } catch (e: Exception) {
                                Log.e("TangoPro", "File picker launch failed", e)
                                Toast.makeText(
                                    context,
                                    "ファイル選択機能の起動に失敗したか、端末がサポートしていません。直接テキスト貼り付けでのインポートをご利用ください。",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }) {
                            Text(if (viewModel.pendingImportUri != null) "このCSVを読み込む" else "ファイルを選択")
                        }
                    }
                }
            }
        }
    }

    if (showEditGroupDialog && selectedGroup != null) {
        var editName by remember { mutableStateOf(selectedGroup!!.name) }
        var editLanguage by remember { mutableStateOf(selectedGroup!!.language) }

        Dialog(onDismissRequest = { showEditGroupDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("単語帳の編集", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("単語帳の名前") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LanguageSelector(editLanguage) { editLanguage = it }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showEditGroupDialog = false }) {
                            Text("キャンセル")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val updatedGroup = selectedGroup!!.copy(
                                    name = editName.ifBlank { "無題" },
                                    language = editLanguage
                                )
                                viewModel.updateGroup(updatedGroup)
                                showEditGroupDialog = false
                                Toast.makeText(context, "更新しました", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }

    if (showCombineDialog) {
        var mergedName by remember { mutableStateOf("") }
        var selectedLanguage by remember { mutableStateOf("en") }
        val checkedState = remember { mutableStateMapOf<Long, Boolean>() }

        Dialog(onDismissRequest = { showCombineDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("複数のCSVを連結して新規作成", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("選択されたCSVを連結し、現在の成績情報(未学習・学習済)を共有したまま新しい成績表を連結作成します。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedTextField(
                        value = mergedName,
                        onValueChange = { mergedName = it },
                        label = { Text("新しい単語帳の名前") },
                        placeholder = { Text("連結英単語帳") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LanguageSelector(selectedLanguage) { selectedLanguage = it }

                    Text("連結するCSVを選択してください:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    Box(modifier = Modifier.height(150.dp)) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(groups) { gp ->
                                val isChecked = checkedState[gp.id] ?: false
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { checkedState[gp.id] = !isChecked }
                                        .padding(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checkedState[gp.id] = it }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val langLabel = StudyLanguage.fromCode(gp.language).displayName
                                    Text("${gp.name} ($langLabel)", fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCombineDialog = false }) {
                            Text("キャンセル")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val selectedIds = checkedState.filter { it.value }.keys.toList()
                                viewModel.combineGroups(mergedName, selectedIds, selectedLanguage) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    if (success) showCombineDialog = false
                                }
                            },
                            enabled = checkedState.filter { it.value }.size >= 2
                        ) {
                            Text("連結して作成")
                        }
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        Dialog(onDismissRequest = { showSettingsDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .widthIn(max = 720.dp)
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("アプリの設定", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("ダークテーマの適用", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("ダーク(黒)とホワイト(白)を切り替えます", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = viewModel.darkThemeSelected,
                                onCheckedChange = { viewModel.darkThemeSelected = it },
                                modifier = Modifier.testTag("dark_theme_switch")
                            )
                        }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("シンプルモード", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "進捗と出題設定をすっきり表示し、文字を少し大きくします",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = viewModel.simpleModeEnabled,
                            onCheckedChange = { viewModel.simpleModeEnabled = it },
                            modifier = Modifier.testTag("simple_mode_switch")
                        )
                    }

                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("問題・4択の文字サイズ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "問題文と4択の文字を同時に変更します。長い選択肢は複数行で表示されます。",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val sizes = listOf(
                                0.8f to "小",
                                1f to "標準",
                                1.2f to "大",
                                1.4f to "特大"
                            )
                            sizes.forEach { (scale, label) ->
                                FilterChip(
                                    selected = viewModel.quizTextScale == scale,
                                    onClick = { viewModel.quizTextScale = scale },
                                    label = { Text(label, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.testTag("quiz_text_scale_$label")
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("問題画面の配置", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "上寄せは内容が短いと下に余白が出ます。均等配置は問題カードが画面いっぱいに広がります。",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StudyArrangementMode.options.forEach { option ->
                                FilterChip(
                                    selected = viewModel.quizArrangementMode == option.id,
                                    onClick = { viewModel.quizArrangementMode = option.id },
                                    label = { Text(option.label, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.testTag("quiz_arrangement_mode_${option.id}")
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val selectedLanguage = StudyLanguage.fromCode(selectedGroup?.language)
                            val ttsLangLabel = if (selectedLanguage.code == "none") "対象言語" else selectedLanguage.displayName
                            Text("${ttsLangLabel}の自動読み上げ (TTS)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("出題テキストが${ttsLangLabel}の時、自動で発音します", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = viewModel.isTtsEnabled,
                            onCheckedChange = { viewModel.isTtsEnabled = it },
                            modifier = Modifier.testTag("tts_auto_switch")
                        )
                    }

                    if (viewModel.isTtsEnabled) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("読み上げ音声の音量", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${(viewModel.ttsVolume * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                            Slider(
                                value = viewModel.ttsVolume,
                                onValueChange = { viewModel.ttsVolume = it },
                                valueRange = 0f..1f,
                                modifier = Modifier.testTag("tts_volume_slider")
                            )
                        }
                    }

                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("正解・不正解時の効果音", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("軽量なシンセ音声を即時に合成して出力します", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SoundPlayer.SoundStyle.entries.forEach { style ->
                                val active = viewModel.soundStyle == style
                                FilterChip(
                                    selected = active,
                                    onClick = { viewModel.soundStyle = style },
                                    label = { Text(style.name, fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (viewModel.soundStyle != SoundPlayer.SoundStyle.MUTE) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("効果音の音量", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${(viewModel.soundVolume * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                                Slider(
                                    value = viewModel.soundVolume,
                                    onValueChange = { viewModel.soundVolume = it },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.testTag("sound_volume_slider")
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("選択中の単語帳の問題数", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("単語帳ごとに、前回選んだ最大問題数を保存します", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        val counts = listOf(5, 10, 20, 50, MainViewModel.QUIZ_COUNT_ALL)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            counts.forEach { c ->
                                val text = if (c == MainViewModel.QUIZ_COUNT_ALL) "全問" else "${c}問"
                                val active = viewModel.selectedQuizCount == c
                                FilterChip(
                                    selected = active,
                                    onClick = { viewModel.selectedQuizCount = c },
                                    label = { Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("学習記録ZIP", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "すべての単語帳CSVと学習記録をZIPで移行します。同一CSVは成績を統合し、内容が異なるCSVは新しい単語帳として追加します。",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    studyArchiveImportLauncher.launch(
                                        arrayOf("application/zip", "application/octet-stream")
                                    )
                                },
                                enabled = !viewModel.isImporting,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("ZIPを読み込む")
                            }
                            Button(
                                onClick = {
                                    studyArchiveExportLauncher.launch("Tango-pro-study-records-v2.0.0.zip")
                                },
                                enabled = !viewModel.isImporting,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("ZIPを書き出す")
                            }
                        }
                    }

                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("従来のJSONバックアップ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "単語帳と成績をJSONファイルへ保存、または以前のバックアップから復元します。",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { jsonImportLauncher.launch(arrayOf("application/json", "text/plain")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("復元")
                            }
                            Button(
                                onClick = {
                                    viewModel.exportSaveDataJson { json ->
                                        if (json == null) {
                                            Toast.makeText(context, "バックアップを作成できませんでした", Toast.LENGTH_LONG).show()
                                        } else {
                                            pendingBackupJson = json
                                            jsonExportLauncher.launch("Tango-pro-backup.json")
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("書き出す")
                            }
                        }
                    }

                    HorizontalDivider()

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("ライセンス情報", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = "Licensed under GPL v3\nCopyright (C) 2026 Yuki Orita",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(onClick = { showSettingsDialog = false }) {
                            Text("閉じる")
                        }
                    }
                }
            }
        }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("成績のリセット") },
            text = { Text("この単語帳のすべての学習回数・成績データを消去して、「未学習」の状態に戻しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedGroup?.let { viewModel.resetGroupProgress(it.id) }
                        showResetConfirmation = false
                    }
                ) {
                    Text("リセットする", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    if (groupToDelete != null) {
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text("単語帳の削除") },
            text = { Text("単語帳「${groupToDelete?.name}」と成績データをアプリ内から完全に削除しますか？ (この操作は取り消せません)") },
            confirmButton = {
                TextButton(
                    onClick = {
                        groupToDelete?.let { viewModel.deleteGroupAndWords(it.id) }
                        groupToDelete = null
                    }
                ) {
                    Text("削除する", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageSelector(selectedLanguage: String, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("対象言語:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StudyLanguage.supported.forEach { language ->
                FilterChip(
                    selected = selectedLanguage == language.code,
                    onClick = { onSelected(language.code) },
                    label = { Text(language.displayName) }
                )
            }
        }
    }
}
