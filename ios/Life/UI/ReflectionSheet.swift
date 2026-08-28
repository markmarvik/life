import SwiftUI

struct ReflectionSheet: View {
    @Environment(\.lifePalette) private var palette
    var event: LifeEvent
    var streak: Int
    var onSubmit: (String) -> Void
    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        let floor = WantDomain.wordFloor(streak)
        let count = WantDomain.wordCount(text)
        let ready = count >= floor
        VStack(alignment: .leading, spacing: 8) {
            Text("Why didn’t you?")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(palette.onSurface)
            Text(event.body)
                .font(.system(size: 14))
                .foregroundStyle(palette.onSurfaceVariant)
            TextField("At least \(floor) words.", text: $text, axis: .vertical)
                .lineLimit(6...10)
                .textFieldStyle(LifeFieldStyle())
                .focused($focused)
            Text("\(count) / \(floor) words")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(ready ? palette.primary : palette.error)
            Button {
                onSubmit(text)
            } label: {
                Text("Save")
                    .font(.system(size: 16, weight: .semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!ready)
            .padding(.top, 8)
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 28)
        .background(palette.surface)
        .presentationDetents([.medium, .large])
        .onAppear { focused = true }
    }
}
