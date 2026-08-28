import SwiftUI

struct CaptureTypeSheet: View {
    @Environment(\.lifePalette) private var palette
    var onPick: (CaptureKind) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("What happened?")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(palette.onSurface)
            HStack(spacing: 10) {
                typeCard("Habit", "✓") { onPick(.habit) }
                typeCard("Note", "✎") { onPick(.note) }
                typeCard("Finance", "⇄") { onPick(.finance) }
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 28)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface)
        .presentationDetents([.height(200)])
    }

    private func typeCard(_ label: String, _ glyph: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Text(glyph).font(.system(size: 26))
                Text(label).font(.system(size: 14, weight: .medium))
            }
            .foregroundStyle(palette.onSurface)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 20)
            .background(palette.surfaceVariant, in: RoundedRectangle(cornerRadius: 18))
        }
        .buttonStyle(.plain)
    }
}
