import SwiftUI
import UIKit

/// "Share your run": the picture in its two looks, swiped or picked, then the system share sheet.
struct RunShareSheet: View {
    let run: RunRecord

    @Environment(\.dismiss) private var dismiss
    @State private var style: RunShareStyle = .dark
    @State private var images: [RunShareStyle: UIImage] = [:]
    @State private var rendered = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                TabView(selection: $style) {
                    ForEach(RunShareStyle.allCases) { option in
                        RunSharePreview(image: images[option], failed: rendered)
                            .padding(.horizontal, 28)
                            .padding(.vertical, 12)
                            .tag(option)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                stylePicker
                Text(L("Swipe to change the look."))
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
            }
            .padding(.top, 8)
            .padding(.bottom, 12)
            .background(Theme.surface.ignoresSafeArea())
            .safeAreaInset(edge: .bottom, spacing: 0) {
                BottomBar {
                    shareButton
                }
            }
            .navigationTitle(L("Share your run"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L("Close")) {
                        Haptics.tap()
                        dismiss()
                    }
                }
            }
        }
        .task(id: run.id) {
            await render()
        }
    }

    @MainActor
    private func render() async {
        var result: [RunShareStyle: UIImage] = [:]
        for option in RunShareStyle.allCases {
            if let image = RunShareRenderer.image(for: run, style: option) {
                result[option] = image
            }
        }
        images = result
        rendered = true
    }

    private var stylePicker: some View {
        HStack(spacing: 10) {
            ForEach(RunShareStyle.allCases) { option in
                styleChip(option)
            }
        }
    }

    private func styleChip(_ option: RunShareStyle) -> some View {
        let selected: Bool = option == style
        return Button(action: {
            Haptics.select()
            withAnimation(.easeInOut(duration: 0.25)) {
                style = option
            }
        }) {
            HStack(spacing: 8) {
                Circle()
                    .fill(option.background)
                    .frame(width: 16, height: 16)
                    .overlay(Circle().stroke(Theme.controlBorder, lineWidth: 1))
                    .accessibilityHidden(true)
                Text(option.name)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.ink)
            }
            .padding(.horizontal, 16)
            .frame(height: 44)
            .background(Color.white, in: Capsule())
            .overlay(Capsule().stroke(selected ? Theme.ink : Theme.cardBorder, lineWidth: selected ? 1.5 : 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private var shareLabel: some View {
        HStack(spacing: 8) {
            Image(systemName: "square.and.arrow.up")
                .font(.system(size: 16, weight: .semibold))
            Text(L("Share"))
        }
    }

    @ViewBuilder
    private var shareButton: some View {
        let text: String = RunSummaryShare.text(for: run)
        if let picture = images[style] {
            let image: Image = Image(uiImage: picture)
            ShareLink(item: image,
                      message: Text(text),
                      preview: SharePreview(RunSummaryShare.title(for: run), image: image)) {
                shareLabel
            }
            .buttonStyle(PrimaryButtonStyle())
        } else {
            ShareLink(item: text) {
                shareLabel
            }
            .buttonStyle(PrimaryButtonStyle())
        }
    }
}

/// One look of the picture, as it will be sent.
private struct RunSharePreview: View {
    let image: UIImage?
    /// The rendering is over and gave nothing: only the text will be shared.
    let failed: Bool

    private var shape: RoundedRectangle { RoundedRectangle(cornerRadius: 22, style: .continuous) }

    var body: some View {
        if let image {
            Image(uiImage: image)
                .resizable()
                .scaledToFit()
                .clipShape(shape)
                .overlay(shape.stroke(Theme.cardBorder, lineWidth: 1))
                .shadow(color: Color.black.opacity(0.14), radius: 18, x: 0, y: 10)
                .accessibilityLabel(L("Picture of your run"))
        } else if failed {
            Text(L("The picture could not be prepared. Your run will be shared as text."))
                .font(.system(size: 15))
                .foregroundStyle(Theme.muted)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}

#Preview {
    RunShareSheet(run: RunSummarySample.run)
}
