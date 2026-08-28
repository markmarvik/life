import SwiftUI

struct NoteSheet: View {
    @Environment(\.lifePalette) private var palette
    var availableTags: [String]
    var editing: LifeEvent? = nil
    var onSave: (String, [String], Bool, String?) -> Void
    var onDelete: (() -> Void)? = nil
    @State private var bodyText: String
    @State private var selected: [String]
    @State private var isWant: Bool
    @State private var dueDate: String?
    @State private var newTag: String = ""
    @FocusState private var focused: Bool

    init(
        availableTags: [String],
        editing: LifeEvent? = nil,
        onSave: @escaping (String, [String], Bool, String?) -> Void,
        onDelete: (() -> Void)? = nil
    ) {
        self.availableTags = availableTags
        self.editing = editing
        self.onSave = onSave
        self.onDelete = onDelete
        _bodyText = State(initialValue: editing?.body ?? "")
        _selected = State(initialValue: editing?.tags ?? [])
        _isWant = State(initialValue: editing?.isWant == true)
        _dueDate = State(initialValue: editing?.dueDate)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(editing == nil ? "Note" : "Edit note")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(palette.onSurface)
                    Spacer()
                    if let onDelete {
                        Button("Delete", role: .destructive, action: onDelete)
                    }
                }
                TextField("What's on your mind?", text: $bodyText, axis: .vertical)
                    .lineLimit(6...10)
                    .textFieldStyle(LifeFieldStyle())
                    .focused($focused)

                Text("Want · date")
                    .font(.system(size: 12))
                    .foregroundStyle(palette.onSurfaceVariant)
                TagChip(label: "Want", selected: isWant) {
                    isWant.toggle()
                    if isWant && dueDate == nil {
                        dueDate = WantDomain.iso(WantDomain.todayComponents())
                    }
                }
                DateChipRow(dueDate: dueDate, onDueDate: { dueDate = $0 })

                Text("Tags")
                    .font(.system(size: 12))
                    .foregroundStyle(palette.onSurfaceVariant)
                    .padding(.top, 4)
                FlowRow(spacing: 8) {
                    ForEach(availableTags, id: \.self) { tag in
                        let on = selected.contains { $0.caseInsensitiveCompare(tag) == .orderedSame }
                        TagChip(label: tag, selected: on) {
                            if on {
                                selected.removeAll { $0.caseInsensitiveCompare(tag) == .orderedSame }
                            } else {
                                selected.append(tag)
                            }
                        }
                    }
                }
                TextField("Add a tag", text: $newTag)
                    .textFieldStyle(LifeFieldStyle())
                    .submitLabel(.done)
                    .onSubmit { addTag(newTag) }

                Button {
                    onSave(bodyText, selected, isWant, dueDate)
                } label: {
                    Text(editing == nil ? "Save" : "Update")
                        .font(.system(size: 16, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .disabled(bodyText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .padding(.top, 8)
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 28)
        }
        .background(palette.surface)
        .presentationDetents([.large])
        .onAppear { focused = true }
    }

    private func addTag(_ raw: String) {
        let t = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if t.isEmpty { return }
        if !selected.contains(where: { $0.caseInsensitiveCompare(t) == .orderedSame }) {
            selected.append(t)
        }
        newTag = ""
    }
}
