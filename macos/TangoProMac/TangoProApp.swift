import AppKit
import SwiftUI
import UniformTypeIdentifiers

@MainActor
final class TangoAppDelegate: NSObject, NSApplicationDelegate {
    weak var store: TangoStore?

    func applicationWillTerminate(_ notification: Notification) {
        store?.flushPersistence()
    }
}

@main
struct TangoProApp: App {
    @NSApplicationDelegateAdaptor(TangoAppDelegate.self) private var appDelegate
    @StateObject private var store = TangoStore()

    var body: some Scene {
        WindowGroup("Tango pro") {
            ContentView(store: store)
                .frame(minWidth: 920, minHeight: 620)
                .preferredColorScheme(store.darkMode ? .dark : .light)
                .onAppear { appDelegate.store = store }
                .onOpenURL { url in CSVFileActions.importCSV(url: url, into: store) }
        }
        .windowStyle(.titleBar)
        .commands {
            CommandGroup(replacing: .newItem) {
                Button("CSVをインポート…") { CSVFileActions.importCSV(into: store) }
                    .keyboardShortcut("o")
            }
        }
        Settings {
            PreferencesView(store: store)
                .frame(width: 500, height: 330)
        }
    }
}

struct ContentView: View {
    @ObservedObject var store: TangoStore

    private var selection: Binding<UUID?> {
        Binding(get: { store.selectedGroupID }, set: { store.select($0) })
    }

    var body: some View {
        NavigationSplitView {
            List(selection: selection) {
                Section("単語帳") {
                    ForEach(store.groups) { group in
                        HStack {
                            Image(systemName: group.language == "zh" ? "character.book.closed" : "text.book.closed")
                            VStack(alignment: .leading, spacing: 2) {
                                Text(group.name).lineLimit(1)
                                Text("\(group.words.count)語")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        .tag(group.id)
                    }
                }
            }
            .navigationTitle("Tango pro")
            .safeAreaInset(edge: .bottom) {
                HStack {
                    Button { CSVFileActions.importCSV(into: store) } label: {
                        Label("CSVを追加", systemImage: "plus")
                    }
                    Spacer()
                    Button {
                        NSApp.sendAction(Selector(("showSettingsWindow:")), to: nil, from: nil)
                    } label: { Image(systemName: "gearshape") }
                }
                .padding(10)
                .background(.bar)
            }
        } detail: {
            if store.session != nil {
                QuizView(store: store)
            } else if let group = store.selectedGroup {
                DashboardView(store: store, group: group)
                    .id(group.id)
            } else {
                EmptyMacView(store: store)
            }
        }
        .alert("Tango pro", isPresented: Binding(
            get: { store.message != nil },
            set: { if !$0 { store.message = nil } }
        )) {
            Button("OK") { store.message = nil }
        } message: {
            Text(store.message ?? "")
        }
    }
}

struct DashboardView: View {
    @ObservedObject var store: TangoStore
    let group: VocabGroup
    @State private var forward = true
    @State private var multipleChoice = true
    @State private var filter: StudyFilter = .recommended
    @State private var questionCount = 10
    @State private var showDeleteConfirmation = false
    @State private var showResetConfirmation = false

    private var learned: Int { group.words.filter { $0.studyCount >= 2 && $0.isCorrectLast }.count }
    private var vague: Int { group.words.filter { $0.studyCount == 1 && $0.isCorrectLast }.count }
    private var review: Int { group.words.count - learned - vague }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack(alignment: .firstTextBaseline) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(group.name).font(.largeTitle.bold())
                        Text("\(group.words.count)語・\(group.language == "zh" ? "中国語" : "英語")")
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Menu {
                        Button("CSVを書き出す…") { CSVFileActions.exportCSV(from: store) }
                        Divider()
                        Button("成績をリセット…") { showResetConfirmation = true }
                        Button("単語帳を削除…", role: .destructive) { showDeleteConfirmation = true }
                    } label: { Image(systemName: "ellipsis.circle").font(.title2) }
                    .menuStyle(.borderlessButton)
                }

                GroupBox("学習状況") {
                    VStack(spacing: 12) {
                        GeometryReader { geometry in
                            HStack(spacing: 0) {
                                if learned > 0 { Color.green.frame(width: geometry.size.width * CGFloat(learned) / CGFloat(max(group.words.count, 1))) }
                                if vague > 0 { Color.orange.frame(width: geometry.size.width * CGFloat(vague) / CGFloat(max(group.words.count, 1))) }
                                if review > 0 { Color.red.opacity(0.75) }
                            }
                            .clipShape(Capsule())
                        }
                        .frame(height: 13)
                        HStack {
                            StatusLabel(color: .green, title: "習得済み", value: learned)
                            Spacer()
                            StatusLabel(color: .orange, title: "うろ覚え", value: vague)
                            Spacer()
                            StatusLabel(color: .red, title: "未学習・要復習", value: review)
                        }
                    }
                    .padding(.top, 6)
                }

                GroupBox("出題設定") {
                    Grid(alignment: .leading, horizontalSpacing: 16, verticalSpacing: 14) {
                        GridRow {
                            Text("出題方向").fontWeight(.semibold)
                            Picker("", selection: $forward) {
                                Text("英語 → 日本語").tag(true)
                                Text("日本語 → 英語").tag(false)
                            }
                            .labelsHidden().pickerStyle(.segmented)
                            .disabled(!multipleChoice)
                        }
                        GridRow {
                            Text("回答形式").fontWeight(.semibold)
                            Picker("", selection: $multipleChoice) {
                                Text("4択").tag(true)
                                Text("タイピング").tag(false)
                            }
                            .labelsHidden().pickerStyle(.segmented)
                            .onChange(of: multipleChoice) { value in if !value { forward = false } }
                        }
                        GridRow {
                            Text("出題対象").fontWeight(.semibold)
                            Picker("", selection: $filter) {
                                ForEach(StudyFilter.allCases) { option in Text(option.rawValue).tag(option) }
                            }
                            .labelsHidden()
                        }
                        GridRow {
                            Text("問題数").fontWeight(.semibold)
                            Picker("", selection: $questionCount) {
                                Text("5問").tag(5); Text("10問").tag(10); Text("20問").tag(20)
                                Text("50問").tag(50); Text("全問").tag(0)
                            }
                            .labelsHidden().pickerStyle(.segmented)
                        }
                    }
                    .padding(.top, 6)
                }

                Button {
                    store.startQuiz(forward: forward, multipleChoice: multipleChoice,
                                    filter: filter, count: questionCount)
                } label: {
                    Label("この設定で学習を開始", systemImage: "play.fill")
                        .font(.title3.bold()).frame(maxWidth: .infinity).padding(.vertical, 8)
                }
                .buttonStyle(.borderedProminent).controlSize(.large)
            }
            .padding(28)
            .frame(maxWidth: 880)
        }
        .confirmationDialog("この単語帳を削除しますか？", isPresented: $showDeleteConfirmation) {
            Button("削除", role: .destructive) { store.deleteSelected() }
        }
        .confirmationDialog("すべての成績を未学習に戻しますか？", isPresented: $showResetConfirmation) {
            Button("リセット", role: .destructive) { store.resetSelected() }
        }
    }
}

struct StatusLabel: View {
    let color: Color
    let title: String
    let value: Int
    var body: some View {
        HStack(spacing: 7) {
            Circle().fill(color).frame(width: 9, height: 9)
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.caption).foregroundStyle(.secondary)
                Text("\(value)語").font(.headline)
            }
        }
    }
}

struct QuizView: View {
    @ObservedObject var store: TangoStore
    @State private var typedAnswer = ""
    @State private var speech = NSSpeechSynthesizer()

    var body: some View {
        if let quiz = store.session {
            if quiz.index >= quiz.questions.count {
                QuizSummaryView(store: store, quiz: quiz)
            } else {
                let question = quiz.questions[quiz.index]
                VStack(spacing: 18) {
                    HStack {
                        Button("学習を中断") { store.closeQuiz() }
                        Spacer()
                        Text("\(quiz.index + 1) / \(quiz.questions.count)").font(.headline)
                        Spacer()
                        Text("正解 \(quiz.score)").foregroundStyle(.secondary)
                    }
                    ProgressView(value: Double(quiz.index + 1), total: Double(quiz.questions.count))

                    Spacer()
                    VStack(spacing: 12) {
                        Button { speak(question.prompt, language: quiz.language) } label: {
                            HStack(spacing: 12) {
                                Text(question.prompt)
                                    .font(.system(size: 38, weight: .bold, design: .rounded))
                                    .multilineTextAlignment(.center)
                                Image(systemName: "speaker.wave.2.fill")
                                    .font(.title2)
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .help("単語を読み上げる")
                        if quiz.checked {
                            Text(quiz.lastCorrect ? "正解" : "不正解")
                                .font(.title.bold()).foregroundStyle(quiz.lastCorrect ? .green : .red)
                            Text("正解: \(question.answer)").font(.title3)
                        }
                    }
                    Spacer()

                    if quiz.isMultipleChoice {
                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                            ForEach(question.choices, id: \.self) { choice in
                                Button(choice) { store.submit(choice) }
                                    .buttonStyle(.bordered)
                                    .controlSize(.large)
                                    .disabled(quiz.checked)
                                    .frame(maxWidth: .infinity)
                            }
                        }
                    } else {
                        HStack {
                            TextField("答えを入力", text: $typedAnswer)
                                .textFieldStyle(.roundedBorder)
                                .font(.title3)
                                .onSubmit { if !quiz.checked { store.submit(typedAnswer) } }
                                .disabled(quiz.checked)
                            Button("回答") { store.submit(typedAnswer) }
                                .buttonStyle(.borderedProminent).disabled(quiz.checked)
                        }
                    }

                    if quiz.checked {
                        Button(quiz.index + 1 == quiz.questions.count ? "結果を見る" : "次の問題") {
                            typedAnswer = ""
                            store.nextQuestion()
                        }
                        .buttonStyle(.borderedProminent).controlSize(.large)
                        .keyboardShortcut(.return, modifiers: [])
                    }
                }
                .padding(28)
                .onAppear { if store.ttsEnabled && question.speaksPrompt { speak(question.prompt, language: quiz.language) } }
                .onChange(of: quiz.index) { _ in
                    if let next = store.session, next.index < next.questions.count {
                        let item = next.questions[next.index]
                        if store.ttsEnabled && item.speaksPrompt { speak(item.prompt, language: next.language) }
                    }
                }
            }
        }
    }

    private func speak(_ text: String, language: String) {
        speech.stopSpeaking()
        speech.setVoice(nil)
        if language == "zh" {
            speech.setVoice(NSSpeechSynthesizer.VoiceName(rawValue: "com.apple.voice.compact.zh-CN.Tingting"))
        }
        speech.startSpeaking(text)
    }
}

struct QuizSummaryView: View {
    @ObservedObject var store: TangoStore
    let quiz: QuizSession
    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: quiz.score * 2 >= quiz.questions.count ? "trophy.fill" : "checkmark.circle.fill")
                .font(.system(size: 60)).foregroundStyle(.yellow)
            Text("学習完了").font(.largeTitle.bold())
            Text("\(quiz.questions.count)問中 \(quiz.score)問正解")
                .font(.title2)
            List(quiz.results) { result in
                HStack {
                    Image(systemName: result.correct ? "checkmark.circle.fill" : "xmark.circle.fill")
                        .foregroundStyle(result.correct ? .green : .red)
                    VStack(alignment: .leading) {
                        Text(result.prompt).fontWeight(.semibold)
                        Text("正解: \(result.answer)　回答: \(result.userAnswer)")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            .frame(maxHeight: 300)
            HStack(spacing: 12) {
                Button("ダッシュボードに戻る") { store.closeQuiz() }
                    .buttonStyle(.bordered).controlSize(.large)
                Button("そのまま続ける") { store.continueQuiz() }
                    .buttonStyle(.borderedProminent).controlSize(.large)
            }
        }
        .padding(32).frame(maxWidth: 720)
    }
}

struct EmptyMacView: View {
    @ObservedObject var store: TangoStore
    var body: some View {
        VStack(spacing: 14) {
            Label("単語帳がありません", systemImage: "text.book.closed")
                .font(.largeTitle.bold())
            Text("CSVファイルを読み込んで学習を始めます。")
                .foregroundStyle(.secondary)
            Button("CSVをインポート…") { CSVFileActions.importCSV(into: store) }
                .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct PreferencesView: View {
    @ObservedObject var store: TangoStore
    var body: some View {
        Form {
            Toggle("ダークテーマ", isOn: Binding(get: { store.darkMode }, set: {
                store.darkMode = $0; store.updatePreferences()
            }))
            Toggle("問題を自動読み上げ", isOn: Binding(get: { store.ttsEnabled }, set: {
                store.ttsEnabled = $0; store.updatePreferences()
            }))
            GroupBox("学習記録ZIP") {
                VStack(alignment: .leading, spacing: 10) {
                    Text("すべてのCSVと学習記録を移行します。同一CSVは成績を統合し、内容が異なる場合は新しい単語帳として追加します。")
                        .font(.caption).foregroundStyle(.secondary)
                    HStack {
                        Button("ZIPを読み込む…") { StudyArchiveFileActions.importArchive(into: store) }
                        Spacer()
                        Button("ZIPを書き出す…") { StudyArchiveFileActions.exportArchive(from: store) }
                            .buttonStyle(.borderedProminent)
                    }
                }
                .padding(.top, 4)
            }
            LabeledContent("データ保存先", value: "Application Support/Tango pro")
        }
        .padding(24)
    }
}

@MainActor
enum StudyArchiveFileActions {
    static func importArchive(into store: TangoStore) {
        let panel = NSOpenPanel()
        panel.title = "学習記録ZIPを選択"
        panel.allowsMultipleSelection = false
        panel.canChooseDirectories = false
        panel.allowedContentTypes = [.zip]
        guard panel.runModal() == .OK, let url = panel.url else { return }
        do {
            let summary = try store.importStudyArchive(from: url)
            showResult("学習記録ZIPを読み込みました。\n統合: \(summary.mergedGroups)冊（\(summary.mergedWords)語）\n追加: \(summary.addedGroups)冊（\(summary.addedWords)語）")
        } catch {
            showResult("学習記録ZIPを読み込めませんでした。\n\(error.localizedDescription)", warning: true)
        }
    }

    static func exportArchive(from store: TangoStore) {
        let panel = NSSavePanel()
        panel.title = "学習記録ZIPを書き出す"
        panel.nameFieldStringValue = "Tango-pro-study-records-v1.2.0.zip"
        panel.allowedContentTypes = [.zip]
        guard panel.runModal() == .OK, let url = panel.url else { return }
        do {
            try store.exportStudyArchive(to: url)
            showResult("学習記録ZIPを書き出しました。")
        } catch {
            showResult("学習記録ZIPを書き出せませんでした。\n\(error.localizedDescription)", warning: true)
        }
    }

    private static func showResult(_ message: String, warning: Bool = false) {
        let alert = NSAlert()
        alert.messageText = "Tango pro"
        alert.informativeText = message
        alert.alertStyle = warning ? .warning : .informational
        alert.runModal()
    }
}

@MainActor
enum CSVFileActions {
    static func importCSV(into store: TangoStore) {
        let panel = NSOpenPanel()
        panel.title = "CSV単語帳を選択"
        panel.allowsMultipleSelection = false
        panel.canChooseDirectories = false
        panel.allowedContentTypes = [.commaSeparatedText, .plainText, .data]
        guard panel.runModal() == .OK, let url = panel.url else { return }
        importCSV(url: url, into: store)
    }

    static func importCSV(url: URL, into store: TangoStore) {
        let languagePopup = NSPopUpButton(frame: NSRect(x: 0, y: 0, width: 220, height: 28))
        languagePopup.addItems(withTitles: ["英語", "中国語", "読み上げなし"])

        let alert = NSAlert()
        alert.messageText = "CSV単語帳をインポート"
        alert.informativeText = "「\(url.lastPathComponent)」の学習対象言語を選択してください。"
        alert.accessoryView = languagePopup
        alert.addButton(withTitle: "インポート")
        alert.addButton(withTitle: "キャンセル")
        guard alert.runModal() == .alertFirstButtonReturn else { return }

        let language = ["en", "zh", "none"][languagePopup.indexOfSelectedItem]
        do {
            try store.importCSV(url: url, language: language)
        } catch {
            store.message = "インポートできませんでした。\n\(error.localizedDescription)"
        }
    }

    static func exportCSV(from store: TangoStore) {
        guard let group = store.selectedGroup else { return }
        let panel = NSSavePanel()
        panel.title = "CSV単語帳を書き出す"
        panel.nameFieldStringValue = "\(group.name).csv"
        panel.allowedContentTypes = [.commaSeparatedText]
        guard panel.runModal() == .OK, let url = panel.url else { return }
        do {
            try store.exportSelected(to: url)
        } catch {
            store.message = "書き出せませんでした。\n\(error.localizedDescription)"
        }
    }
}
