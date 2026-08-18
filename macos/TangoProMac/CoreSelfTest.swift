import Foundation

@main
struct CoreSelfTest {
    @MainActor
    static func main() throws {
        let source = "\u{FEFF}\"day in, day out\",来る日も来る日も,Phrase\r\n\"line\nbreak\",\"quote \"\"test\"\"\",tag\n"
        let parsed = try RFC4180CSV.parse(source)
        precondition(parsed.count == 2)
        precondition(parsed[0][0] == "day in, day out")
        precondition(parsed[1][0] == "line\nbreak")
        precondition(parsed[1][1] == "quote \"test\"")
        let words = [VocabWord(term: parsed[0][0], meaning: parsed[0][1], tag: parsed[0][2])]
        let roundTrip = try RFC4180CSV.parse(RFC4180CSV.serialize(words))
        precondition(roundTrip[0] == ["day in, day out", "来る日も来る日も", "Phrase", ""])

        let repetitionFolder = FileManager.default.temporaryDirectory
            .appendingPathComponent("TangoRepetitionSelfTest-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: repetitionFolder, withIntermediateDirectories: true)
        let repetitionCSV = repetitionFolder.appendingPathComponent("repeat.csv")
        try "apple,りんご\n".write(to: repetitionCSV, atomically: true, encoding: .utf8)
        let repetitionStore = TangoStore(
            storageDirectory: repetitionFolder,
            bundledCSVURL: repetitionFolder.appendingPathComponent("NoCSV")
        )
        try repetitionStore.importCSV(url: repetitionCSV, name: "repeat")
        repetitionStore.startQuiz(forward: true, multipleChoice: true, filter: .recommended, count: 5)
        for _ in 0..<2 {
            guard let answer = repetitionStore.session?.questions.first?.answer else {
                preconditionFailure("Recommended mode must create a question")
            }
            repetitionStore.submit(answer)
            repetitionStore.nextQuestion()
            repetitionStore.continueQuiz()
        }
        precondition(repetitionStore.selectedGroup?.words.first?.studyCount == 2)
        precondition(repetitionStore.selectedGroup?.currentRound == 3,
                     "Every completed pass must advance the displayed round")
        precondition(repetitionStore.session?.questions.count == 1,
                     "A fully learned book must remain repeatable in recommended mode")
        precondition(repetitionStore.session?.index == 0,
                     "Continue must start a fresh session with the previous settings")
        repetitionStore.updateStudySettings(for: repetitionStore.selectedGroupID!, settings: GroupStudySettings(
            directionForward: false, multipleChoice: true, filter: .weak, questionCount: 20
        ))
        let secondCSV = repetitionFolder.appendingPathComponent("second.csv")
        try "bonjour,こんにちは\n".write(to: secondCSV, atomically: true, encoding: .utf8)
        try repetitionStore.importCSV(url: secondCSV, name: "second", language: "fr")
        let secondID = repetitionStore.selectedGroupID!
        repetitionStore.updateStudySettings(for: secondID, settings: GroupStudySettings(
            directionForward: true, multipleChoice: true, filter: .unstudied, questionCount: 5
        ))
        repetitionStore.simpleMode = true
        repetitionStore.quizTextScale = 1.4
        repetitionStore.updatePreferences()
        repetitionStore.flushPersistence()

        let reopenedRepetitionStore = TangoStore(
            storageDirectory: repetitionFolder,
            bundledCSVURL: repetitionFolder.appendingPathComponent("NoCSV")
        )
        let firstID = reopenedRepetitionStore.groups.first(where: { $0.name == "repeat" })!.id
        precondition(reopenedRepetitionStore.studySettings(for: firstID).filter == .weak)
        precondition(reopenedRepetitionStore.studySettings(for: firstID).questionCount == 20)
        precondition(reopenedRepetitionStore.studySettings(for: secondID).filter == .unstudied)
        precondition(reopenedRepetitionStore.studySettings(for: secondID).questionCount == 5,
                     "Study settings must remain independent for each CSV book")
        precondition(reopenedRepetitionStore.simpleMode)
        precondition(reopenedRepetitionStore.quizTextScale == 1.4)
        reopenedRepetitionStore.flushPersistence()

        let legacyFolder = FileManager.default.temporaryDirectory
            .appendingPathComponent("TangoLegacySettingsSelfTest-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: legacyFolder, withIntermediateDirectories: true)
        let legacyGroup = VocabGroup(name: "legacy", words: [VocabWord(term: "old", meaning: "旧")])
        let legacyGroupObject = try JSONSerialization.jsonObject(with: JSONEncoder().encode(legacyGroup))
        let legacyState: [String: Any] = [
            "version": 1,
            "groups": [legacyGroupObject],
            "selectedGroupID": legacyGroup.id.uuidString,
            "darkMode": false,
            "ttsEnabled": true,
            "bundledImportCompleted": true
        ]
        try JSONSerialization.data(withJSONObject: legacyState)
            .write(to: legacyFolder.appendingPathComponent("study-data.json"))
        let legacyStore = TangoStore(storageDirectory: legacyFolder,
                                     bundledCSVURL: legacyFolder.appendingPathComponent("NoCSV"))
        precondition(legacyStore.groups.count == 1)
        precondition(legacyStore.studySettings(for: legacyGroup.id) == GroupStudySettings(),
                     "v1.2.0 JSON without per-book settings must use defaults")
        legacyStore.flushPersistence()

        let recoveryFolder = FileManager.default.temporaryDirectory
            .appendingPathComponent("TangoCoreSelfTest-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: recoveryFolder, withIntermediateDirectories: true)
        try Data("{broken-json".utf8).write(to: recoveryFolder.appendingPathComponent("study-data.json"))
        let recoveryStore = TangoStore(storageDirectory: recoveryFolder,
                                       bundledCSVURL: recoveryFolder.appendingPathComponent("NoCSV"))
        recoveryStore.flushPersistence()
        let protectedFiles = try FileManager.default.contentsOfDirectory(at: recoveryFolder,
            includingPropertiesForKeys: nil).filter { $0.lastPathComponent.hasPrefix("study-data-corrupt-") }
        precondition(protectedFiles.count == 1)
        precondition(recoveryStore.message?.contains("破損ファイルを保護") == true)

        let bootstrapFolder = FileManager.default.temporaryDirectory
            .appendingPathComponent("TangoBootstrapSelfTest-\(UUID().uuidString)", isDirectory: true)
        let assetsFolder = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
            .appendingPathComponent("app/src/main/assets", isDirectory: true)
        let bootstrapStore = TangoStore(storageDirectory: bootstrapFolder, bundledCSVURL: assetsFolder)
        precondition(bootstrapStore.groups.count == 17)
        while bootstrapStore.selectedGroupID != nil { bootstrapStore.deleteSelected() }
        bootstrapStore.flushPersistence()

        let reopenedStore = TangoStore(storageDirectory: bootstrapFolder, bundledCSVURL: assetsFolder)
        precondition(reopenedStore.groups.isEmpty, "Deleted bundled groups must not be re-created")
        reopenedStore.flushPersistence()

        let archiveFolder = FileManager.default.temporaryDirectory
            .appendingPathComponent("TangoArchiveSelfTest-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: archiveFolder, withIntermediateDirectories: true)
        defer {
            try? FileManager.default.removeItem(at: recoveryFolder)
            try? FileManager.default.removeItem(at: bootstrapFolder)
            try? FileManager.default.removeItem(at: archiveFolder)
            try? FileManager.default.removeItem(at: repetitionFolder)
            try? FileManager.default.removeItem(at: legacyFolder)
        }
        let archiveURL = archiveFolder.appendingPathComponent("round-trip.zip")
        let local = VocabGroup(name: "local", words: [
            VocabWord(term: "apple", meaning: "りんご")
        ])
        let remote = VocabGroup(name: "remote", words: [
            VocabWord(term: "apple", meaning: "りんご", studyCount: 3,
                      isCorrectLast: false, lastStudiedAt: Date(timeIntervalSince1970: 200))
        ])
        try StudyArchiveCodec.write(groups: [remote], to: archiveURL, appVersion: "test")
        let merged = try StudyArchiveCodec.importAndMerge(groups: [local], from: archiveURL)
        precondition(merged.groups.count == 1)
        precondition(merged.summary.mergedGroups == 1 && merged.summary.addedGroups == 0)
        precondition(merged.groups[0].words[0].studyCount == 3)
        precondition(merged.groups[0].words[0].isCorrectLast == false)

        let multilingual = [
            VocabGroup(name: "français", language: "fr", words: [VocabWord(term: "bonjour", meaning: "こんにちは")]),
            VocabGroup(name: "português", language: "pt", words: [VocabWord(term: "olá", meaning: "こんにちは")])
        ]
        try StudyArchiveCodec.write(groups: multilingual, to: archiveURL, appVersion: "test")
        let multilingualResult = try StudyArchiveCodec.importAndMerge(groups: [], from: archiveURL)
        precondition(multilingualResult.groups.map(\.language) == ["fr", "pt"])

        let changed = VocabGroup(name: "local", words: [
            VocabWord(term: "apple", meaning: "林檎", studyCount: 1,
                      isCorrectLast: true, lastStudiedAt: Date(timeIntervalSince1970: 300))
        ])
        try StudyArchiveCodec.write(groups: [changed], to: archiveURL, appVersion: "test")
        let added = try StudyArchiveCodec.importAndMerge(groups: [local], from: archiveURL)
        precondition(added.groups.count == 2)
        precondition(added.summary.addedGroups == 1 && added.groups[1].name == "local 2")

        let arguments = CommandLine.arguments
        if let index = arguments.firstIndex(of: "--android-fixture"), index + 1 < arguments.count {
            let androidArchive = URL(fileURLWithPath: arguments[index + 1])
            let crossPlatform = try StudyArchiveCodec.importAndMerge(groups: [], from: androidArchive)
            precondition(!crossPlatform.groups.isEmpty)
            precondition(crossPlatform.summary.addedGroups == crossPlatform.groups.count)
            let importFolder = archiveFolder.appendingPathComponent("ImportedStore", isDirectory: true)
            let importedStore = TangoStore(storageDirectory: importFolder,
                                           bundledCSVURL: archiveFolder.appendingPathComponent("NoCSV"))
            let summary = try importedStore.importStudyArchive(from: androidArchive)
            precondition(summary.addedGroups == crossPlatform.groups.count &&
                         importedStore.groups.count == crossPlatform.groups.count)
            importedStore.flushPersistence()
            let reopenedImportedStore = TangoStore(storageDirectory: importFolder,
                bundledCSVURL: archiveFolder.appendingPathComponent("NoCSV"))
            precondition(reopenedImportedStore.groups.count == crossPlatform.groups.count)
            reopenedImportedStore.flushPersistence()
        }
        if let index = arguments.firstIndex(of: "--write-fixture"), index + 1 < arguments.count {
            try StudyArchiveCodec.write(groups: [remote, changed],
                                        to: URL(fileURLWithPath: arguments[index + 1]), appVersion: "2.0.0")
        }
        print("TangoCore self-test: PASS")
    }
}
