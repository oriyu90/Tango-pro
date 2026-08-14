import AppKit
import Combine
import Foundation

enum StudyLanguage: String, CaseIterable, Codable, Identifiable, Sendable {
    case english = "en"
    case chinese = "zh"
    case french = "fr"
    case portuguese = "pt"
    case none = "none"

    var id: String { rawValue }
    var displayName: String {
        switch self {
        case .english: return "英語"
        case .chinese: return "中国語"
        case .french: return "フランス語"
        case .portuguese: return "ポルトガル語"
        case .none: return "読み上げなし"
        }
    }
    var preferredLocales: [String] {
        switch self {
        case .english: return ["en-US", "en-GB", "en"]
        case .chinese: return ["zh-CN", "zh-TW", "zh"]
        case .french: return ["fr-FR", "fr-CA", "fr"]
        case .portuguese: return ["pt-BR", "pt-PT", "pt"]
        case .none: return []
        }
    }

    static func from(_ code: String) -> StudyLanguage { StudyLanguage(rawValue: code) ?? .english }
}

struct VocabWord: Codable, Identifiable, Hashable, Sendable {
    var id = UUID()
    var term: String
    var meaning: String
    var tag: String = ""
    var pronunciation: String = ""
    var studyCount: Int = 0
    var isCorrectLast: Bool = true
    var lastStudiedAt: Date? = nil
}

struct VocabGroup: Codable, Identifiable, Hashable, Sendable {
    var id = UUID()
    var name: String
    var language: String = "en"
    var studySettings: GroupStudySettings? = nil
    var createdAt = Date()
    var words: [VocabWord]
}

enum StudyFilter: String, CaseIterable, Identifiable, Codable, Sendable {
    case recommended = "おすすめ"
    case unstudied = "未学習のみ"
    case weak = "うろ覚え＆ミスのみ"
    case vagueRandom = "うろ覚えをランダム"
    case learnedRandom = "学習済をランダム"
    var id: String { rawValue }
}

struct GroupStudySettings: Codable, Hashable, Sendable {
    var directionForward = true
    var multipleChoice = true
    var filter: StudyFilter = .recommended
    var questionCount = 10

    var normalized: GroupStudySettings {
        var result = self
        if !result.multipleChoice { result.directionForward = false }
        if ![0, 5, 10, 20, 50].contains(result.questionCount) { result.questionCount = 10 }
        return result
    }
}

struct QuizQuestion: Identifiable {
    var id = UUID()
    var wordID: UUID
    var prompt: String
    var answer: String
    var choices: [String]
    var speaksPrompt: Bool
}

struct QuizResult: Identifiable {
    var id = UUID()
    var prompt: String
    var answer: String
    var userAnswer: String
    var correct: Bool
}

struct QuizSession {
    var questions: [QuizQuestion]
    var index = 0
    var score = 0
    var checked = false
    var lastCorrect = false
    var userAnswer = ""
    var results: [QuizResult] = []
    var isMultipleChoice: Bool
    var language: String
    var directionForward: Bool
    var filter: StudyFilter
    var requestedCount: Int
}

enum CSVError: LocalizedError {
    case tooManyRows(Int)
    case unclosedQuote
    case noValidRows

    var errorDescription: String? {
        switch self {
        case .tooManyRows(let limit): return "CSVは最大\(limit)行までです。"
        case .unclosedQuote: return "CSVの引用符が閉じられていません。"
        case .noValidRows: return "有効な単語データがありません。"
        }
    }
}

enum RFC4180CSV {
    static func parse(_ text: String, maxRows: Int = 100_000) throws -> [[String]] {
        let input = text.replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
        var rows: [[String]] = []
        var row: [String] = []
        var field = ""
        var inQuotes = false
        var hasContent = false
        var index = input.startIndex

        func appendRow() throws {
            row.append(field)
            if hasContent || row.contains(where: { !$0.isEmpty }) {
                guard rows.count < maxRows else { throw CSVError.tooManyRows(maxRows) }
                rows.append(row)
            }
            row = []
            field = ""
            hasContent = false
        }

        while index < input.endIndex {
            let char = input[index]
            let nextIndex = input.index(after: index)
            if inQuotes {
                if char == "\"" {
                    if nextIndex < input.endIndex, input[nextIndex] == "\"" {
                        field.append("\"")
                        index = input.index(after: nextIndex)
                    } else {
                        inQuotes = false
                        index = nextIndex
                    }
                } else {
                    field.append(char)
                    hasContent = true
                    index = nextIndex
                }
                continue
            }

            switch char {
            case "\u{FEFF}":
                if !rows.isEmpty || !row.isEmpty || !field.isEmpty {
                    field.append(char)
                    hasContent = true
                }
            case "\"":
                if field.isEmpty { inQuotes = true } else { field.append(char) }
                hasContent = true
            case ",":
                row.append(field)
                field = ""
                hasContent = true
            case "\n":
                try appendRow()
            case "\r":
                if nextIndex < input.endIndex, input[nextIndex] == "\n" {
                    index = nextIndex
                }
                try appendRow()
            default:
                field.append(char)
                if !char.isWhitespace { hasContent = true }
            }
            index = input.index(after: index)
        }

        guard !inQuotes else { throw CSVError.unclosedQuote }
        if !field.isEmpty || !row.isEmpty || hasContent { try appendRow() }
        if !rows.isEmpty, !rows[0].isEmpty {
            rows[0][0] = String(rows[0][0].drop(while: { $0 == "\u{FEFF}" }))
        }
        return rows
    }

    static func escape(_ value: String) -> String {
        let escaped = value.replacingOccurrences(of: "\"", with: "\"\"")
        return value.contains(",") || value.contains("\n") || value.contains("\r") || value.contains("\"")
            ? "\"\(escaped)\"" : escaped
    }

    static func serialize(_ words: [VocabWord]) -> String {
        words.map { word in
            [word.term, word.meaning, word.tag, word.pronunciation]
                .map(escape).joined(separator: ",")
        }.joined(separator: "\n") + "\n"
    }
}

private struct SavedState: Codable, Sendable {
    var version = 1
    var groups: [VocabGroup]
    var selectedGroupID: UUID?
    var darkMode: Bool
    var ttsEnabled: Bool
    var bundledImportCompleted: Bool?
}

private enum PersistenceError: LocalizedError {
    case unsupportedVersion(Int)

    var errorDescription: String? {
        switch self {
        case .unsupportedVersion(let version):
            return "未対応の保存データ形式です（version: \(version)）。"
        }
    }
}

private final class PersistenceWriter: @unchecked Sendable {
    private let queue = DispatchQueue(label: "com.yukiorita.tangopro.persistence", qos: .utility)

    func write(_ state: SavedState, to url: URL, completion: @escaping @Sendable (Error?) -> Void) {
        queue.async {
            do {
                let encoder = JSONEncoder()
                let data = try encoder.encode(state)
                try data.write(to: url, options: .atomic)
                completion(nil)
            } catch {
                completion(error)
            }
        }
    }

    func flush() {
        queue.sync { }
    }
}

@MainActor
final class TangoStore: ObservableObject {
    @Published private(set) var groups: [VocabGroup] = []
    @Published var selectedGroupID: UUID?
    @Published var darkMode = false
    @Published var ttsEnabled = true
    @Published var session: QuizSession?
    @Published var message: String?

    private let saveURL: URL
    private let bundledCSVURL: URL?
    private let persistenceWriter = PersistenceWriter()
    private var persistenceEnabled = true
    private var bundledImportCompleted = false

    init(storageDirectory: URL? = nil, bundledCSVURL: URL? = nil) {
        let base = storageDirectory
            ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
                .appendingPathComponent("Tango pro", isDirectory: true)
        let folder = base
        saveURL = folder.appendingPathComponent("study-data.json")
        self.bundledCSVURL = bundledCSVURL ?? Bundle.main.resourceURL?.appendingPathComponent("CSV")
        do {
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        } catch {
            persistenceEnabled = false
            message = "保存フォルダを作成できません。学習結果は保存されません。\n\(error.localizedDescription)"
        }
        load()
        if !bundledImportCompleted { importBundledGroups() }
        if selectedGroupID == nil || !groups.contains(where: { $0.id == selectedGroupID }) {
            selectedGroupID = groups.first?.id
        }
    }

    var selectedGroup: VocabGroup? {
        guard let id = selectedGroupID else { return nil }
        return groups.first(where: { $0.id == id })
    }

    func studySettings(for groupID: UUID) -> GroupStudySettings {
        groups.first(where: { $0.id == groupID })?.studySettings?.normalized ?? GroupStudySettings()
    }

    func updateStudySettings(for groupID: UUID, settings: GroupStudySettings) {
        guard let index = groups.firstIndex(where: { $0.id == groupID }) else { return }
        groups[index].studySettings = settings.normalized
        save()
    }

    func updateSelectedLanguage(_ language: StudyLanguage) {
        mutateSelected { group in group.language = language.rawValue }
    }

    func select(_ id: UUID?) {
        selectedGroupID = id
        session = nil
        save()
    }

    func importCSV(url: URL, name: String? = nil, language: String = "en") throws {
        let data = try Data(contentsOf: url, options: .mappedIfSafe)
        guard let text = String(data: data, encoding: .utf8) else {
            throw CocoaError(.fileReadInapplicableStringEncoding)
        }
        let words = try Self.words(from: text)
        let group = VocabGroup(name: name?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
            ?? url.deletingPathExtension().lastPathComponent, language: language, words: words)
        groups.append(group)
        selectedGroupID = group.id
        save()
    }

    func exportSelected(to url: URL) throws {
        guard let group = selectedGroup else { return }
        try RFC4180CSV.serialize(group.words).write(to: url, atomically: true, encoding: .utf8)
    }

    func exportStudyArchive(to url: URL) throws {
        flushPersistence()
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.2.1"
        try StudyArchiveCodec.write(groups: groups, to: url, appVersion: version)
    }

    func importStudyArchive(from url: URL) throws -> StudyArchiveImportSummary {
        let result = try StudyArchiveCodec.importAndMerge(groups: groups, from: url)
        flushPersistence()
        let previousGroups = groups
        let previousSelectedID = selectedGroupID
        let previousSession = session
        groups = result.groups
        if let selectedID = result.selectedID { selectedGroupID = selectedID }
        session = nil
        do {
            try persistCurrentStateSynchronously()
        } catch {
            groups = previousGroups
            selectedGroupID = previousSelectedID
            session = previousSession
            throw error
        }
        return result.summary
    }

    func resetSelected() {
        mutateSelected { group in
            for index in group.words.indices {
                group.words[index].studyCount = 0
                group.words[index].isCorrectLast = true
                group.words[index].lastStudiedAt = nil
            }
        }
    }

    func deleteSelected() {
        guard let id = selectedGroupID else { return }
        groups.removeAll { $0.id == id }
        selectedGroupID = groups.first?.id
        session = nil
        save()
    }

    func startQuiz(forward: Bool, multipleChoice: Bool, filter: StudyFilter, count: Int) {
        guard let group = selectedGroup else { return }
        updateStudySettings(for: group.id, settings: GroupStudySettings(
            directionForward: forward,
            multipleChoice: multipleChoice,
            filter: filter,
            questionCount: count
        ))
        var candidates = group.words
        switch filter {
        case .recommended:
            candidates.sort {
                Self.priority($0) == Self.priority($1)
                    ? $0.studyCount < $1.studyCount : Self.priority($0) > Self.priority($1)
            }
        case .unstudied: candidates = candidates.filter { $0.studyCount == 0 }
        case .weak: candidates = candidates.filter {
            ($0.studyCount == 1 && $0.isCorrectLast) || ($0.studyCount > 0 && !$0.isCorrectLast)
        }
        case .vagueRandom: candidates = candidates.filter { $0.studyCount == 1 && $0.isCorrectLast }
        case .learnedRandom: candidates = candidates.filter { $0.studyCount >= 2 && $0.isCorrectLast }
        }
        if filter != .recommended { candidates.shuffle() }
        if count > 0 { candidates = Array(candidates.prefix(count)) }
        guard !candidates.isEmpty else {
            message = "選択条件に合う単語がありません。"
            return
        }

        let questions = candidates.map { word -> QuizQuestion in
            let prompt = forward ? word.term : word.meaning
            let answer = forward ? word.meaning : word.term
            var choices: [String] = []
            if multipleChoice {
                let others = group.words.filter { $0.id != word.id }
                    .map { forward ? $0.meaning : $0.term }
                    .filter { $0 != answer }
                choices = Array(Set(others)).shuffled().prefix(3).map { $0 }
                let fallback = forward
                    ? ["りんご", "本", "走る", "犬", "机", "山", "海", "学校"]
                    : ["apple", "book", "run", "dog", "desk", "mountain", "sea", "school"]
                for value in fallback.shuffled() where choices.count < 3 && value != answer && !choices.contains(value) {
                    choices.append(value)
                }
                choices.append(answer)
                choices.shuffle()
            }
            return QuizQuestion(wordID: word.id, prompt: prompt, answer: answer,
                                choices: choices, speaksPrompt: forward)
        }
        session = QuizSession(
            questions: questions,
            isMultipleChoice: multipleChoice,
            language: group.language,
            directionForward: forward,
            filter: filter,
            requestedCount: count
        )
    }

    func submit(_ answer: String) {
        guard var quiz = session, quiz.index < quiz.questions.count, !quiz.checked else { return }
        let question = quiz.questions[quiz.index]
        let normalizedUser = answer.trimmingCharacters(in: .whitespacesAndNewlines)
        let correct = normalizedUser.compare(question.answer.trimmingCharacters(in: .whitespacesAndNewlines),
                                             options: [.caseInsensitive, .widthInsensitive], locale: .current) == .orderedSame
        quiz.checked = true
        quiz.lastCorrect = correct
        quiz.userAnswer = normalizedUser
        if correct { quiz.score += 1 }
        quiz.results.append(QuizResult(prompt: question.prompt, answer: question.answer,
                                       userAnswer: normalizedUser.isEmpty ? "（未入力）" : normalizedUser,
                                       correct: correct))
        mutateSelected(saveAfter: false) { group in
            guard let index = group.words.firstIndex(where: { $0.id == question.wordID }) else { return }
            group.words[index].studyCount += 1
            group.words[index].isCorrectLast = correct
            group.words[index].lastStudiedAt = Date()
        }
        session = quiz
        save()
    }

    func nextQuestion() {
        guard var quiz = session, quiz.checked else { return }
        quiz.index += 1
        quiz.checked = false
        quiz.lastCorrect = false
        quiz.userAnswer = ""
        session = quiz
    }

    func continueQuiz() {
        guard let quiz = session else { return }
        startQuiz(
            forward: quiz.directionForward,
            multipleChoice: quiz.isMultipleChoice,
            filter: quiz.filter,
            count: quiz.requestedCount
        )
    }

    func closeQuiz() { session = nil }

    func updatePreferences() { save() }

    func flushPersistence() { persistenceWriter.flush() }

    private func mutateSelected(saveAfter: Bool = true, _ change: (inout VocabGroup) -> Void) {
        guard let id = selectedGroupID, let index = groups.firstIndex(where: { $0.id == id }) else { return }
        change(&groups[index])
        if saveAfter { save() }
    }

    private static func words(from text: String) throws -> [VocabWord] {
        let words = try RFC4180CSV.parse(text).compactMap { fields -> VocabWord? in
            guard fields.count >= 2 else { return nil }
            let term = fields[0].trimmingCharacters(in: .whitespacesAndNewlines)
            let meaning = fields[1].trimmingCharacters(in: .whitespacesAndNewlines)
            guard !term.isEmpty, !meaning.isEmpty else { return nil }
            return VocabWord(term: term, meaning: meaning,
                             tag: fields.count > 2 ? fields[2].trimmingCharacters(in: .whitespacesAndNewlines) : "",
                             pronunciation: fields.count > 3 ? fields[3].trimmingCharacters(in: .whitespacesAndNewlines) : "")
        }
        guard !words.isEmpty else { throw CSVError.noValidRows }
        return words
    }

    private static func priority(_ word: VocabWord) -> Int {
        if word.studyCount == 0 || !word.isCorrectLast { return 3 }
        if word.studyCount == 1 { return 2 }
        return 1
    }

    private func importBundledGroups() {
        let specs = [
            ("basic_english_phrases", "英語基本フレーズ", "en"),
            ("basic_english_words", "英語基本単語", "en"),
            ("basic_chinese_words", "中国語基本単語", "zh"),
            ("common_test_words", "共テ用英単語", "en"),
            ("common_test_phrases", "共テ用英熟語", "en"),
            ("advanced_words", "難関大用英単語", "en"),
            ("advanced_phrases", "難関大用英熟語", "en"),
            ("pre_high_school_vocab", "高校レベル未満の単熟語", "en")
        ]
        guard let resources = bundledCSVURL else { return }
        let availableFiles = (try? FileManager.default.contentsOfDirectory(at: resources,
            includingPropertiesForKeys: nil)) ?? []
        for (assetName, displayName, language) in specs {
            if groups.contains(where: { $0.name == displayName }) { continue }
            let normalizedName = assetName.precomposedStringWithCanonicalMapping
            guard let url = availableFiles.first(where: {
                $0.deletingPathExtension().lastPathComponent.precomposedStringWithCanonicalMapping == normalizedName
            }) else { continue }
            guard let data = try? Data(contentsOf: url), let text = String(data: data, encoding: .utf8),
                  let words = try? Self.words(from: text) else { continue }
            groups.append(VocabGroup(name: displayName, language: language, words: words))
        }
        bundledImportCompleted = specs.allSatisfy { spec in
            groups.contains(where: { $0.name == spec.1 })
        }
        selectedGroupID = groups.first?.id
        save()
    }

    private func load() {
        guard FileManager.default.fileExists(atPath: saveURL.path) else { return }
        do {
            let data = try Data(contentsOf: saveURL)
            let saved = try JSONDecoder().decode(SavedState.self, from: data)
            guard saved.version == 1 else { throw PersistenceError.unsupportedVersion(saved.version) }
            groups = saved.groups
            selectedGroupID = saved.selectedGroupID
            darkMode = saved.darkMode
            ttsEnabled = saved.ttsEnabled
            // Old local builds imported bundled data only when the database was empty.
            // Treat existing user data as already bootstrapped to avoid surprise imports.
            bundledImportCompleted = saved.bundledImportCompleted ?? !saved.groups.isEmpty
        } catch {
            let formatter = DateFormatter()
            formatter.dateFormat = "yyyyMMdd-HHmmss"
            let backupURL = saveURL.deletingLastPathComponent()
                .appendingPathComponent("study-data-corrupt-\(formatter.string(from: Date())).json")
            do {
                try FileManager.default.copyItem(at: saveURL, to: backupURL)
                message = "保存データを読み込めなかったため、破損ファイルを保護して初期状態で起動しました。\n保護先: \(backupURL.lastPathComponent)"
            } catch let backupError {
                persistenceEnabled = false
                message = "保存データが破損しています。保護コピーも作成できないため、元ファイルを上書きしません。\n\(backupError.localizedDescription)"
            }
        }
    }

    private func save() {
        guard persistenceEnabled else { return }
        let state = SavedState(groups: groups, selectedGroupID: selectedGroupID,
                               darkMode: darkMode, ttsEnabled: ttsEnabled,
                               bundledImportCompleted: bundledImportCompleted)
        persistenceWriter.write(state, to: saveURL) { [weak self] error in
            guard let error else { return }
            Task { @MainActor [weak self] in
                self?.message = "学習結果を保存できませんでした。\n\(error.localizedDescription)"
            }
        }
    }

    private func persistCurrentStateSynchronously() throws {
        guard persistenceEnabled else { throw CocoaError(.fileWriteUnknown) }
        let state = SavedState(groups: groups, selectedGroupID: selectedGroupID,
                               darkMode: darkMode, ttsEnabled: ttsEnabled,
                               bundledImportCompleted: bundledImportCompleted)
        try JSONEncoder().encode(state).write(to: saveURL, options: .atomic)
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
