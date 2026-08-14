import CryptoKit
import Foundation

private let studyArchiveFormat = "tango-pro-study-archive"
private let studyArchiveVersion = 1

struct StudyArchiveImportSummary {
    var mergedGroups: Int
    var addedGroups: Int
    var mergedWords: Int
    var addedWords: Int
}

private struct ArchiveManifest: Codable {
    var format: String
    var version: Int
    var exportedAtEpochMillis: Int64
    var appVersion: String
    var groups: [ArchiveGroupManifest]
}

private struct ArchiveGroupManifest: Codable {
    var id: String
    var name: String
    var language: String
    var csvPath: String
    var progressPath: String
    var csvSha256: String
}

private struct ArchiveProgress: Codable {
    var version: Int
    var csvSha256: String
    var records: [ArchiveProgressRecord]
}

private struct ArchiveProgressRecord: Codable {
    var row: Int
    var studyCount: Int
    var isCorrectLast: Bool
    var lastStudiedAt: Int64
}

private struct ArchiveWord {
    var term: String
    var meaning: String
    var tag: String
    var pronunciation: String
    var progress: ArchiveProgressRecord
}

private struct ImportedArchiveGroup {
    var name: String
    var language: String
    var words: [ArchiveWord]
    var canonicalCSV: String
}

enum StudyArchiveError: LocalizedError {
    case invalid(String)
    case ditto(String)

    var errorDescription: String? {
        switch self {
        case .invalid(let detail): return detail
        case .ditto(let detail): return "ZIP処理に失敗しました。\n\(detail)"
        }
    }
}

enum StudyArchiveCodec {
    private static let maxGroups = 500
    private static let maxWordsPerGroup = 100_000
    private static let maxEntries = 1 + maxGroups * 2
    private static let maxEntryBytes: Int64 = 64 * 1024 * 1024
    private static let maxTotalBytes: Int64 = 256 * 1024 * 1024
    private static let validLanguages = Set(StudyLanguage.allCases.map(\.rawValue))

    static func write(groups: [VocabGroup], to destination: URL, appVersion: String) throws {
        guard groups.count <= maxGroups else { throw StudyArchiveError.invalid("単語帳数が上限を超えています。") }
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("TangoStudyExport-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: root) }

        var manifests: [ArchiveGroupManifest] = []
        for (index, group) in groups.enumerated() {
            guard !group.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                  group.name.count <= 200 else {
                throw StudyArchiveError.invalid("単語帳名が不正です。")
            }
            guard validLanguages.contains(group.language) else {
                throw StudyArchiveError.invalid("未対応の言語です。")
            }
            guard group.words.count <= maxWordsPerGroup else {
                throw StudyArchiveError.invalid("1冊あたり最大\(maxWordsPerGroup)語までです。")
            }

            let id = String(format: "group-%04d", index + 1)
            let csvPath = "groups/\(id)/words.csv"
            let progressPath = "groups/\(id)/progress.json"
            let csv = RFC4180CSV.serialize(group.words)
            let csvData = Data(csv.utf8)
            let hash = sha256(csvData)
            let records = group.words.enumerated().map { row, word in
                ArchiveProgressRecord(
                    row: row,
                    studyCount: word.studyCount,
                    isCorrectLast: word.isCorrectLast,
                    lastStudiedAt: milliseconds(word.lastStudiedAt)
                )
            }
            try validate(records: records)
            let progress = ArchiveProgress(version: studyArchiveVersion, csvSha256: hash, records: records)

            try write(csvData, relativePath: csvPath, under: root)
            try write(try JSONEncoder().encode(progress), relativePath: progressPath, under: root)
            manifests.append(ArchiveGroupManifest(
                id: id,
                name: group.name,
                language: group.language,
                csvPath: csvPath,
                progressPath: progressPath,
                csvSha256: hash
            ))
        }

        let manifest = ArchiveManifest(
            format: studyArchiveFormat,
            version: studyArchiveVersion,
            exportedAtEpochMillis: milliseconds(Date()),
            appVersion: appVersion,
            groups: manifests
        )
        try write(try JSONEncoder().encode(manifest), relativePath: "manifest.json", under: root)

        let temporaryArchive = destination.deletingLastPathComponent()
            .appendingPathComponent(".TangoStudy-\(UUID().uuidString).zip")
        defer { try? FileManager.default.removeItem(at: temporaryArchive) }
        try runDitto(["-c", "-k", "--norsrc", root.path, temporaryArchive.path])
        if FileManager.default.fileExists(atPath: destination.path) {
            _ = try FileManager.default.replaceItemAt(destination, withItemAt: temporaryArchive)
        } else {
            try FileManager.default.moveItem(at: temporaryArchive, to: destination)
        }
    }

    static func importAndMerge(groups: [VocabGroup], from archive: URL) throws ->
        (groups: [VocabGroup], selectedID: UUID?, summary: StudyArchiveImportSummary) {
        try merge(groups: groups, imported: read(from: archive))
    }

    private static func read(from archive: URL) throws -> [ImportedArchiveGroup] {
        let archiveSize = try archive.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard archiveSize <= Int(maxTotalBytes) else {
            throw StudyArchiveError.invalid("ZIPファイルが大きすぎます。")
        }
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("TangoStudyImport-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: root) }
        try preflight(archive: archive)
        try runDitto(["-x", "-k", archive.path, root.path])

        let files = try validatedFiles(under: root)
        guard let manifestURL = files["manifest.json"] else {
            throw StudyArchiveError.invalid("manifest.jsonがありません。")
        }
        let manifest = try JSONDecoder().decode(ArchiveManifest.self, from: Data(contentsOf: manifestURL))
        guard manifest.format == studyArchiveFormat else {
            throw StudyArchiveError.invalid("Tango proの学習記録ZIPではありません。")
        }
        guard manifest.version == studyArchiveVersion else {
            throw StudyArchiveError.invalid("未対応のZIP形式versionです。")
        }
        guard manifest.exportedAtEpochMillis >= 0, manifest.groups.count <= maxGroups else {
            throw StudyArchiveError.invalid("manifestの値が不正です。")
        }

        var ids = Set<String>()
        var expectedPaths: Set<String> = ["manifest.json"]
        var result: [ImportedArchiveGroup] = []
        for group in manifest.groups {
            guard group.id.range(of: #"^group-[0-9]{4}$"#, options: .regularExpression) != nil,
                  ids.insert(group.id).inserted else {
                throw StudyArchiveError.invalid("単語帳IDが不正または重複しています。")
            }
            guard !group.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                  group.name.count <= 200,
                  validLanguages.contains(group.language) else {
                throw StudyArchiveError.invalid("単語帳のmetadataが不正です。")
            }
            let csvPath = "groups/\(group.id)/words.csv"
            let progressPath = "groups/\(group.id)/progress.json"
            guard group.csvPath == csvPath, group.progressPath == progressPath,
                  group.csvSha256.range(of: #"^[0-9a-f]{64}$"#, options: .regularExpression) != nil else {
                throw StudyArchiveError.invalid("単語帳pathまたはhashが不正です。")
            }
            expectedPaths.formUnion([csvPath, progressPath])
            guard let csvURL = files[csvPath], let progressURL = files[progressPath] else {
                throw StudyArchiveError.invalid("CSVまたは学習記録が不足しています。")
            }
            let csvData = try Data(contentsOf: csvURL)
            guard sha256(csvData) == group.csvSha256,
                  let csv = String(data: csvData, encoding: .utf8), Data(csv.utf8) == csvData else {
                throw StudyArchiveError.invalid("CSVのSHA-256またはUTF-8形式が不正です。")
            }
            let rows = try RFC4180CSV.parse(csv, maxRows: maxWordsPerGroup)
            guard rows.allSatisfy({ $0.count == 4 && !$0[0].trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                && !$0[1].trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) else {
                throw StudyArchiveError.invalid("アーカイブCSVは4列の有効な単語データである必要があります。")
            }
            let progress = try JSONDecoder().decode(ArchiveProgress.self, from: Data(contentsOf: progressURL))
            guard progress.version == studyArchiveVersion, progress.csvSha256 == group.csvSha256,
                  progress.records.count == rows.count else {
                throw StudyArchiveError.invalid("学習記録とCSVが一致しません。")
            }
            let records = progress.records.sorted { $0.row < $1.row }
            guard records.enumerated().allSatisfy({ $0.offset == $0.element.row }) else {
                throw StudyArchiveError.invalid("学習記録の行番号が不正です。")
            }
            try validate(records: records)
            let words = rows.enumerated().map { row, fields in
                ArchiveWord(term: fields[0], meaning: fields[1], tag: fields[2],
                            pronunciation: fields[3], progress: records[row])
            }
            let canonical = canonicalCSV(words)
            guard canonical == csv else {
                throw StudyArchiveError.invalid("CSVがTango proの正規形式ではありません。")
            }
            result.append(ImportedArchiveGroup(name: group.name, language: group.language,
                                                words: words, canonicalCSV: canonical))
        }
        guard Set(files.keys) == expectedPaths else {
            throw StudyArchiveError.invalid("manifestにないファイル、または不足ファイルがあります。")
        }
        return result
    }

    private static func merge(groups: [VocabGroup], imported: [ImportedArchiveGroup]) ->
        (groups: [VocabGroup], selectedID: UUID?, summary: StudyArchiveImportSummary) {
        var updated = groups
        var indicesByCSV: [String: [Int]] = [:]
        for (index, group) in groups.enumerated() {
            indicesByCSV[RFC4180CSV.serialize(group.words), default: []].append(index)
        }
        var usedNames = Set(groups.map(\.name))
        var selectedID: UUID?
        var summary = StudyArchiveImportSummary(mergedGroups: 0, addedGroups: 0, mergedWords: 0, addedWords: 0)

        for incoming in imported {
            if let index = indicesByCSV[incoming.canonicalCSV]?.first {
                for row in updated[index].words.indices {
                    let local = updated[index].words[row]
                    let localRecord = ArchiveProgressRecord(
                        row: row, studyCount: local.studyCount, isCorrectLast: local.isCorrectLast,
                        lastStudiedAt: milliseconds(local.lastStudiedAt)
                    )
                    let merged = merge(local: localRecord, incoming: incoming.words[row].progress)
                    updated[index].words[row].studyCount = merged.studyCount
                    updated[index].words[row].isCorrectLast = merged.isCorrectLast
                    updated[index].words[row].lastStudiedAt = date(merged.lastStudiedAt)
                }
                summary.mergedGroups += 1
                summary.mergedWords += incoming.words.count
            } else {
                let group = VocabGroup(
                    name: uniqueName(incoming.name, used: &usedNames),
                    language: incoming.language,
                    words: incoming.words.map { word in
                        VocabWord(term: word.term, meaning: word.meaning, tag: word.tag,
                                  pronunciation: word.pronunciation,
                                  studyCount: word.progress.studyCount,
                                  isCorrectLast: word.progress.isCorrectLast,
                                  lastStudiedAt: date(word.progress.lastStudiedAt))
                    }
                )
                updated.append(group)
                indicesByCSV[incoming.canonicalCSV, default: []].append(updated.count - 1)
                if selectedID == nil { selectedID = group.id }
                summary.addedGroups += 1
                summary.addedWords += incoming.words.count
            }
        }
        return (updated, selectedID, summary)
    }

    private static func merge(local: ArchiveProgressRecord,
                              incoming: ArchiveProgressRecord) -> ArchiveProgressRecord {
        if local.studyCount == 0 && incoming.studyCount > 0 {
            var result = incoming; result.row = local.row; return result
        }
        if incoming.studyCount == 0 { return local }
        if local.studyCount == 0 {
            var result = incoming; result.row = local.row; return result
        }
        let latest: ArchiveProgressRecord
        if incoming.lastStudiedAt > local.lastStudiedAt {
            latest = incoming
        } else if incoming.lastStudiedAt < local.lastStudiedAt {
            latest = local
        } else {
            latest = incoming.studyCount > local.studyCount ? incoming : local
        }
        return ArchiveProgressRecord(row: local.row,
            studyCount: max(local.studyCount, incoming.studyCount),
            isCorrectLast: latest.isCorrectLast,
            lastStudiedAt: max(local.lastStudiedAt, incoming.lastStudiedAt))
    }

    private static func canonicalCSV(_ words: [ArchiveWord]) -> String {
        words.map { [$0.term, $0.meaning, $0.tag, $0.pronunciation].map(RFC4180CSV.escape).joined(separator: ",") }
            .joined(separator: "\n") + "\n"
    }

    private static func validate(records: [ArchiveProgressRecord]) throws {
        guard records.allSatisfy({ $0.studyCount >= 0 && $0.lastStudiedAt >= 0 }) else {
            throw StudyArchiveError.invalid("学習記録に負の値があります。")
        }
    }

    private static func validatedFiles(under root: URL) throws -> [String: URL] {
        guard let enumerator = FileManager.default.enumerator(at: root,
            includingPropertiesForKeys: [.isDirectoryKey, .isRegularFileKey, .isSymbolicLinkKey, .fileSizeKey],
            options: [], errorHandler: nil) else {
            throw StudyArchiveError.invalid("ZIPの内容を列挙できません。")
        }
        var files: [String: URL] = [:]
        var total: Int64 = 0
        for case let url as URL in enumerator {
            let values = try url.resourceValues(forKeys: [.isDirectoryKey, .isRegularFileKey, .isSymbolicLinkKey, .fileSizeKey])
            guard values.isSymbolicLink != true else { throw StudyArchiveError.invalid("ZIP内のsymbolic linkは許可されません。") }
            if values.isDirectory == true { continue }
            guard values.isRegularFile == true else { throw StudyArchiveError.invalid("ZIP内に通常ファイル以外があります。") }
            let rootPath = root.standardizedFileURL.path + "/"
            let path = url.standardizedFileURL.path
            guard path.hasPrefix(rootPath) else { throw StudyArchiveError.invalid("ZIP entry pathが不正です。") }
            let relative = String(path.dropFirst(rootPath.count))
            guard !relative.isEmpty, !relative.hasPrefix("/"), !relative.contains("\\"),
                  !relative.split(separator: "/").contains("..") else {
                throw StudyArchiveError.invalid("ZIP entry pathが不正です。")
            }
            let size = Int64(values.fileSize ?? 0)
            guard size <= maxEntryBytes else { throw StudyArchiveError.invalid("ZIP内のファイルが大きすぎます。") }
            total += size
            guard total <= maxTotalBytes, files.count < maxEntries else {
                throw StudyArchiveError.invalid("ZIPの展開サイズまたはファイル数が上限を超えています。")
            }
            guard files.updateValue(url, forKey: relative) == nil else {
                throw StudyArchiveError.invalid("ZIP内のファイル名が重複しています。")
            }
        }
        return files
    }

    private static func write(_ data: Data, relativePath: String, under root: URL) throws {
        let url = root.appendingPathComponent(relativePath)
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try data.write(to: url, options: .atomic)
    }

    private static func preflight(archive: URL) throws {
        let namesData = try runTool("/usr/bin/zipinfo", ["-1", archive.path])
        guard let namesText = String(data: namesData, encoding: .utf8) else {
            throw StudyArchiveError.invalid("ZIP entry名をUTF-8として検査できません。")
        }
        let names = namesText.split(separator: "\n", omittingEmptySubsequences: true).map(String.init)
        guard names.count <= maxEntries * 2 else {
            throw StudyArchiveError.invalid("ZIP内のファイル数が上限を超えています。")
        }
        var seen = Set<String>()
        for rawName in names {
            let name = rawName.hasSuffix("/") ? String(rawName.dropLast()) : rawName
            let components = name.split(separator: "/", omittingEmptySubsequences: false)
            guard !name.isEmpty, !name.hasPrefix("/"), !name.contains("\\"),
                  !components.contains(where: { $0.isEmpty || $0 == "." || $0 == ".." }),
                  seen.insert(rawName).inserted else {
                throw StudyArchiveError.invalid("ZIP entry pathが不正または重複しています。")
            }
        }

        let detailData = try runTool("/usr/bin/zipinfo", ["-l", archive.path])
        guard let detail = String(data: detailData, encoding: .utf8) else {
            throw StudyArchiveError.invalid("ZIP metadataを検査できません。")
        }
        var total: Int64 = 0
        var entryLines = 0
        for line in detail.split(separator: "\n").map(String.init)
            where line.first == "-" || line.first == "d" || line.first == "l" {
            entryLines += 1
            guard line.first != "l" else {
                throw StudyArchiveError.invalid("ZIP内のsymbolic linkは許可されません。")
            }
            let fields = line.split(whereSeparator: { $0.isWhitespace })
            guard fields.count >= 4, let size = Int64(fields[3]), size >= 0 else {
                throw StudyArchiveError.invalid("ZIP entryのサイズを検査できません。")
            }
            guard size <= maxEntryBytes else {
                throw StudyArchiveError.invalid("ZIP内のファイルが大きすぎます。")
            }
            total += size
            guard total <= maxTotalBytes else {
                throw StudyArchiveError.invalid("ZIPの展開サイズが上限を超えています。")
            }
        }
        guard entryLines == names.count else {
            throw StudyArchiveError.invalid("ZIPのentry一覧が一致しません。")
        }
    }

    private static func runDitto(_ arguments: [String]) throws {
        _ = try runTool("/usr/bin/ditto", arguments)
    }

    private static func runTool(_ executable: String, _ arguments: [String]) throws -> Data {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: executable)
        process.arguments = arguments
        let outputPipe = Pipe()
        process.standardOutput = outputPipe
        process.standardError = outputPipe
        try process.run()
        let output = outputPipe.fileHandleForReading.readDataToEndOfFile()
        process.waitUntilExit()
        guard process.terminationStatus == 0 else {
            let detail = String(data: output, encoding: .utf8) ?? "archive tool error"
            throw StudyArchiveError.ditto(detail)
        }
        return output
    }

    private static func sha256(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    private static func milliseconds(_ date: Date?) -> Int64 {
        guard let date else { return 0 }
        return Int64((date.timeIntervalSince1970 * 1000).rounded(.towardZero))
    }

    private static func date(_ milliseconds: Int64) -> Date? {
        milliseconds > 0 ? Date(timeIntervalSince1970: Double(milliseconds) / 1000) : nil
    }

    private static func uniqueName(_ base: String, used: inout Set<String>) -> String {
        let trimmed = base.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalized = trimmed.isEmpty ? "インポートした単語帳" : trimmed
        if used.insert(normalized).inserted { return normalized }
        var suffix = 2
        while !used.insert("\(normalized) \(suffix)").inserted { suffix += 1 }
        return "\(normalized) \(suffix)"
    }
}
