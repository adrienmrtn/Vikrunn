import SwiftUI
import UIKit

/// What is shared from a run summary: a short text and a rendered card.
enum RunSummaryShare {
    static func title(for run: RunRecord) -> String {
        run.title.isEmpty ? "Run" : run.title
    }

    /// "Easy run · 6.10 km in 35:12 (5:46/km) · Tuesday 17 November · Atala"
    static func text(for run: RunRecord) -> String {
        var parts: [String] = [title(for: run)]
        var figures = "\(RunFormat.distance(run.distanceM)) in \(Fmt.duration(run.durationSec))"
        if run.avgPace > 0 { figures += " (\(Fmt.pace(run.avgPace))/km)" }
        parts.append(figures)
        parts.append(Fmt.day(run.date))
        parts.append("Atala")
        return parts.joined(separator: " · ")
    }

    /// The card as an image, or nil when it could not be rendered (the text is shared instead).
    @MainActor
    static func image(for run: RunRecord) -> Image? {
        let renderer = ImageRenderer(content: RunSummaryShareCard(run: run))
        renderer.scale = 3
        guard let rendered = renderer.uiImage else { return nil }
        return Image(uiImage: rendered)
    }
}

/// The picture sent by the share sheet: title, date and the figures of the run on the brand green.
/// Self-contained (no environment), so it can be rendered off screen.
struct RunSummaryShareCard: View {
    let run: RunRecord

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            BrandLogo(style: .full, height: 20)
                .foregroundStyle(Theme.cream)
            VStack(alignment: .leading, spacing: 4) {
                Text(Fmt.day(run.date))
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.greenOnDark)
                Text(RunSummaryShare.title(for: run))
                    .font(.system(size: 28, weight: .bold))
                    .foregroundStyle(Color.white)
                    .lineLimit(2)
            }
            HStack(alignment: .top, spacing: 12) {
                figure(RunFormat.distance(run.distanceM), "Distance")
                figure(Fmt.duration(run.durationSec), "Duration")
                figure(run.avgPace > 0 ? "\(Fmt.pace(run.avgPace))/km" : "--", "Average pace")
            }
        }
        .padding(24)
        .frame(width: 360, alignment: .leading)
        .background(Theme.green)
    }

    private func figure(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.system(size: 20, weight: .bold))
                .monospacedDigit()
                .foregroundStyle(Color.white)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(label)
                .font(.system(size: 12))
                .foregroundStyle(Theme.greenOnDark)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Actions under a run summary: count the run as the session of its day, share it, talk to the coach,
/// delete it.
struct RunSummaryActions: View {
    let run: RunRecord
    /// Closes the summary, wherever it is shown (end of the run flow, or pushed from the history).
    let onClose: () -> Void

    @EnvironmentObject private var store: AppStore
    @State private var confirmDelete = false
    @State private var shareImage: Image?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let session = store.mainSessionToCount(for: run) {
                MainCountRunCard(run: run, session: session)
            }
            HStack(spacing: 10) {
                shareButton
                Button(action: talkToCoach) {
                    Label("Talk to my coach", systemImage: "bubble.left")
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
                .buttonStyle(SecondaryButtonStyle())
            }
            Button(role: .destructive) {
                Haptics.warning()
                confirmDelete = true
            } label: {
                Text("Delete this run")
                    .font(.system(size: 15, weight: .semibold))
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 44)
            }
            .confirmationDialog("Delete this run?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Delete", role: .destructive) { deleteRun() }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text(deleteMessage)
            }
        }
        .task(id: run.id) {
            await renderShareImage()
        }
    }

    @MainActor
    private func renderShareImage() async {
        shareImage = RunSummaryShare.image(for: run)
    }

    @ViewBuilder
    private var shareButton: some View {
        let text = RunSummaryShare.text(for: run)
        if let shareImage {
            ShareLink(item: shareImage,
                      message: Text(text),
                      preview: SharePreview(RunSummaryShare.title(for: run), image: shareImage)) {
                Label("Share", systemImage: "square.and.arrow.up")
            }
            .buttonStyle(SecondaryButtonStyle())
        } else {
            ShareLink(item: text) {
                Label("Share", systemImage: "square.and.arrow.up")
            }
            .buttonStyle(SecondaryButtonStyle())
        }
    }

    private var deleteMessage: String {
        var text = "It is removed from your history and your totals."
        if let id = run.workoutID, store.workout(id: id) != nil {
            text += " Its session goes back to not done."
        }
        if run.source == .health {
            text += " It stays in Apple Health and will not be imported again."
        }
        return text
    }

    private func deleteRun() {
        if run.source == .health {
            HealthKitService.shared.ignoreRun(startingAt: run.date)
        }
        store.deleteRun(run.id)
        onClose()
    }

    /// Closes the summary, then brings the Coach tab to the front.
    private func talkToCoach() {
        onClose()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) {
            NotificationCenter.default.post(name: .mainOpenCoachTab, object: nil)
        }
    }
}
