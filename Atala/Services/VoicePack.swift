import Foundation

/// The coach's recorded voice in the app's language. One pack per language and voice is kept on
/// Supabase (public bucket `coach-voice`); the app downloads the one it needs, once, and plays
/// from the phone afterwards. Until a pack is complete the coach falls back to the English clips
/// shipped with the app, then to the phone's synthesized voice.
/// Empty file written in a pack's folder once every clip is there.
private let voicePackMarker = ".complete"

@MainActor
enum VoicePack {
    private static let version = "v1"
    private static var ready: Set<String> = []
    private static var running: Set<String> = []

    /// The two recorded voices: "m" for the men, "f" for the women.
    static func voiceKey(coachID: Int) -> String {
        Coach.maleIDs.contains(coachID) ? "m" : "f"
    }

    /// A downloaded clip, nil while the pack of this language and voice is not complete.
    static func localURL(id: String, coachID: Int) -> URL? {
        let voice = voiceKey(coachID: coachID)
        let language = AppLanguage.current
        guard isReady(language: language, voice: voice),
              let folder = folder(language: language, voice: voice) else { return nil }
        let url = folder.appendingPathComponent(id + ".mp3")
        return FileManager.default.fileExists(atPath: url.path) ? url : nil
    }

    /// Downloads what is missing from the pack of this coach, in the background. Safe to call often.
    static func prepare(coachID: Int) {
        let voice = voiceKey(coachID: coachID)
        let language = AppLanguage.current
        let key = language + "/" + voice
        guard !isReady(language: language, voice: voice), !running.contains(key),
              let folder = folder(language: language, voice: voice),
              let base = URL(string: SupabaseConfig.urlString + "/storage/v1/object/public/coach-voice/"
                             + version + "/" + key) else { return }
        running.insert(key)
        let ids = VoiceClip.allIDs
        Task {
            let complete = await fetch(ids: ids, from: base, into: folder)
            running.remove(key)
            if complete { ready.insert(key) }
        }
    }

    private static func isReady(language: String, voice: String) -> Bool {
        let key = language + "/" + voice
        if ready.contains(key) { return true }
        guard let folder = folder(language: language, voice: voice),
              FileManager.default.fileExists(atPath: folder.appendingPathComponent(voicePackMarker).path) else { return false }
        ready.insert(key)
        return true
    }

    private static func folder(language: String, voice: String) -> URL? {
        guard let root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else {
            return nil
        }
        return root.appendingPathComponent("CoachVoice").appendingPathComponent(version)
            .appendingPathComponent(language).appendingPathComponent(voice)
    }

    /// True when every clip of the pack is on the phone at the end.
    nonisolated private static func fetch(ids: [String], from base: URL, into folder: URL) async -> Bool {
        let manager = FileManager.default
        do {
            try manager.createDirectory(at: folder, withIntermediateDirectories: true)
        } catch {
            return false
        }
        let missing = ids.filter { !manager.fileExists(atPath: folder.appendingPathComponent($0 + ".mp3").path) }
        let complete = await withTaskGroup(of: Bool.self, returning: Bool.self) { group in
            var all = true
            var next = 0
            // A few downloads at a time: the clips are small, the pack is about a megabyte.
            while next < min(6, missing.count) {
                let id = missing[next]
                group.addTask { await fetchOne(id: id, from: base, into: folder) }
                next += 1
            }
            while let succeeded = await group.next() {
                if !succeeded { all = false }
                if next < missing.count {
                    let id = missing[next]
                    group.addTask { await fetchOne(id: id, from: base, into: folder) }
                    next += 1
                }
            }
            return all
        }
        guard complete else { return false }
        manager.createFile(atPath: folder.appendingPathComponent(voicePackMarker).path, contents: Data())
        // The pack can be downloaded again: no need to carry it in the phone's backups.
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var target = folder
        try? target.setResourceValues(values)
        return true
    }

    nonisolated private static func fetchOne(id: String, from base: URL, into folder: URL) async -> Bool {
        let name = id + ".mp3"
        do {
            let (data, response) = try await URLSession.shared.data(from: base.appendingPathComponent(name))
            guard let http = response as? HTTPURLResponse, http.statusCode == 200, data.count > 500 else { return false }
            try data.write(to: folder.appendingPathComponent(name), options: .atomic)
            return true
        } catch {
            return false
        }
    }
}
