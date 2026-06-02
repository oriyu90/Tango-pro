package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.service.SoundPlayer
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.SolidColor

val ic_export_csv: ImageVector
    get() = ImageVector.Builder(
        name = "ic_export_csv",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.Black),
        stroke = null
    ) {
        moveTo(4f, 15f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(4f)
        horizontalLineToRelative(12f)
        verticalLineToRelative(-4f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(4f)
        quadToRelative(0f, 1.1f, -1.1f, 1.1f)
        horizontalLineToRelative(-12f)
        quadToRelative(-1.1f, 0f, -1.1f, -1.1f)
        close()
        moveTo(11f, 16f)
        verticalLineTo(6.8f)
        lineToRelative(-3.6f, 3.6f)
        lineToRelative(-1.4f, -1.4f)
        lineToRelative(6f, -6f)
        lineToRelative(6f, 6f)
        lineToRelative(-1.4f, 1.4f)
        lineToRelative(-3.6f, -3.6f)
        verticalLineTo(16f)
        horizontalLineToRelative(-2f)
        close()
    }.build()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            
            MyApplicationTheme(
                darkTheme = viewModel.darkThemeSelected,
                dynamicColor = false
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppContent(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val groups by viewModel.groups.collectAsState(initial = emptyList())
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val words by viewModel.selectedGroupWords.collectAsState()

    var showUriImportDialog by rememberSaveable { mutableStateOf(false) }
    var showTextImportDialog by rememberSaveable { mutableStateOf(false) }
    var showCombineDialog by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showResetConfirmation by rememberSaveable { mutableStateOf(false) }
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var groupToDelete by remember { mutableStateOf<StudyGroup?>(null) }

    var temporaryImportName by remember { mutableStateOf("") }
    val csvFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importCsvFromUri(context, uri, temporaryImportName) { success, message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                if (success) {
                    showUriImportDialog = false
                    temporaryImportName = ""
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(310.dp)
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
                                NavigationDrawerItem(
                                    label = {
                                        Text(
                                            text = group.name,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    },
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.selectGroup(group)
                                        scope.launch { drawerState.close() }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.List,
                                            contentDescription = "CSV"
                                        )
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
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
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
                            val sb = StringBuilder()
                            for (word in currentWords) {
                                val eng = word.english.replace("\n", " ").replace(",", " ")
                                val jpn = word.japanese.replace("\n", " ").replace(",", " ")
                                val tag = word.tag.replace("\n", " ").replace(",", " ")
                                sb.append("$eng,$jpn,$tag\n")
                            }
                            try {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Tango pro CSV Export", sb.toString())
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
                                val sb = StringBuilder()
                                for (word in currentWords) {
                                    val eng = word.english.replace("\n", " ").replace(",", " ")
                                    val jpn = word.japanese.replace("\n", " ").replace(",", " ")
                                    val tag = word.tag.replace("\n", " ").replace(",", " ")
                                    sb.append("$eng,$jpn,$tag\n")
                                }
                                val csvText = sb.toString()

                                val safeGroupName = activeGroup.name.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                                val filename = "Tango_pro_${safeGroupName}.csv"
                                val cacheFile = java.io.File(context.cacheDir, filename)
                                val fileOutputStream = java.io.FileOutputStream(cacheFile)
                                fileOutputStream.write(csvText.toByteArray(Charsets.UTF_8))
                                fileOutputStream.close()

                                val fileUri: Uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    cacheFile
                                )

                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, fileUri)
                                    putExtra(Intent.EXTRA_SUBJECT, "${activeGroup.name} の英単語リスト (CSV)")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "CSVを保存・共有"))
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "エクスポート失敗: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                            showExportDialog = false
                        }
                    ) {
                        Icon(ic_export_csv, contentDescription = null, modifier = Modifier.size(16.dp))
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
                        "英語と日本語をカンマ(,)で区切って1行に入力します。品詞/タグは3番目に任意で入力できます。\n(例)\napple,りんご,名詞\nrun,走る,動詞",
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

                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        label = { Text("CSVテキスト (1列:英語, 2列:日本語, 3列:タグ)") },
                        placeholder = { Text("dog,犬,名詞\nhappy,幸せな,形容詞") },
                        modifier = Modifier.fillMaxWidth().height(200.dp)
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
                                viewModel.importCsvFromText(folderName, rawText) { success, msg ->
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
        Dialog(onDismissRequest = { showUriImportDialog = false }) {
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showUriImportDialog = false }) {
                            Text("キャンセル")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            temporaryImportName = importName
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
                                e.printStackTrace()
                                Toast.makeText(
                                    context,
                                    "ファイル選択機能の起動に失敗したか、端末がサポートしていません。直接テキスト貼り付けでのインポートをご利用ください。",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }) {
                            Text("ファイルを選択")
                        }
                    }
                }
            }
        }
    }

    if (showCombineDialog) {
        var mergedName by remember { mutableStateOf("") }
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
                                    Text(gp.name, fontSize = 14.sp)
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
                                viewModel.combineGroups(mergedName, selectedIds) { success, msg ->
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
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("アプリの設定", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)

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
                            Text("英語の自動読み上げ (TTS)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("出題テキストが英語の時、自動で発音します", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("一度の学習問題数", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("1回のセッションで出題する最大問題数", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        val counts = listOf(5, 10, 20, 50, 100000)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            counts.forEach { c ->
                                val text = if (c == 100000) "全問" else "${c}問"
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

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ライセンス情報", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = "Licensed under GPL v3\nCopyright (C) 2026 Yuki Orita",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }

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

@Composable
fun EmptyStateScreen(onPasteClick: () -> Unit, onFileClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = "スタンプ",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Tango proへようこそ！",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "CSV形式（カンマ区切り）の単語リストをインポートして、4択クイズとタイピング学習を開始できます。",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("簡単お試し！テキスト貼り付け用サンプル", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "apple,りんご,名詞\nbook,本,名詞\nhappy,幸せな,形容詞\nrun,走る,動詞\nbeautiful,美しい,形容詞",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onPasteClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("この形式でテキスト貼付を行う")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onFileClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("スマホのCSVファイルを選択する")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    selectedGroup: StudyGroup,
    words: List<Word>,
    viewModel: MainViewModel,
    onResetStats: () -> Unit,
    onDeleteGroup: () -> Unit,
    onExportCsv: () -> Unit
) {
    val totalCount = words.size
    val studiedCount = words.count { it.studyCount > 1 && it.isCorrectLast }
    val vagueCount = words.count { it.studyCount == 1 }
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

    LaunchedEffect(totalCount) {
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
                            Text("出題方向", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            if (!studyMultipleChoice) {
                                Text("※タイピングは日→英固定です", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Row {
                            FilterChip(
                                selected = studyDirectionForward,
                                onClick = { if (studyMultipleChoice) viewModel.studyDirectionForward = true },
                                label = { Text("順方向 (英→日)") },
                                enabled = studyMultipleChoice
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            FilterChip(
                                selected = !studyDirectionForward,
                                onClick = { viewModel.studyDirectionForward = false },
                                label = { Text("逆方向 (日→英)") }
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
                        listOf(
                            "all" to "全問対象",
                            "recommend" to "おすすめ",
                            "unstudied" to "未学習のみ",
                            "incorrect" to "前回ミスのみ",
                            "learned_once" to "うろ覚え(1回のみ)のみ",
                            "weak" to "うろ覚え＆ミスのみ",
                            "learned_random" to "学習済をランダム"
                        ).forEach { (modeCode, title) ->
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

                            val matchesCount = when (filterMode) {
                                "unstudied" -> activeWords.count { it.studyCount == 0 }
                                "incorrect" -> activeWords.count { it.studyCount > 0 && !it.isCorrectLast }
                                "learned_once" -> activeWords.count { it.studyCount == 1 }
                                "weak" -> activeWords.count { it.studyCount == 1 || (it.studyCount > 0 && !it.isCorrectLast) }
                                "learned_random" -> activeWords.count { it.studyCount > 0 }
                                "recommend" -> activeWords.size
                                else -> activeWords.size
                            }

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
                    modifier = Modifier.padding(horizontal = 16.dp)
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

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (question.directionForward) "この英語の日本語訳を答えてください" else "この日本語の英語スペルを答えてください",
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

@Composable
fun QuizSummaryScreen(viewModel: MainViewModel) {
    val score = viewModel.sessionScore
    val total = viewModel.sessionQuestions.size
    val correctRate = if (total > 0) (score * 100f / total).toInt() else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "学習完了！",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "正答率: ${correctRate}%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Text(
                    text = "正解数: ${score} / ${total}問中",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        Text("今回出題された単語のレビュー:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.sessionResults) { r ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = r.questionText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "正解: ${r.correctAnswer}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "回答: ${r.userAnswer}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            Icon(
                                imageVector = if (r.isCorrect) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = if (r.isCorrect) "正解" else "不正解",
                                tint = if (r.isCorrect) Color(0xFF4CAF50) else Color(0xFFE53935),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.exitStudySession() },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("exit_session_btn")
        ) {
            Text("一覧ダッシュボードに戻る")
        }
    }
}
