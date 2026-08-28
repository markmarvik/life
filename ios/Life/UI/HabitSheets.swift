import SwiftUI

struct HabitPickerSheet: View {
    @Environment(\.lifePalette) private var palette
    @Binding var query: String
    var suggestions: [HabitSuggestion]
    var onPick: (HabitSuggestion) -> Void
    var onEdit: (HabitSuggestion) -> Void
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Log a habit")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(palette.onSurface)
            Text("Hold a habit to edit it.")
                .font(.system(size: 13))
                .foregroundStyle(palette.onSurfaceVariant)
            TextField("Search or create…", text: $query)
                .textFieldStyle(LifeFieldStyle())
                .focused($focused)
                .submitLabel(.done)
                .onSubmit {
                    if let first = suggestions.first { onPick(first) }
                }
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    ForEach(suggestions) { s in
                        HabitLine(
                            icon: s.icon,
                            title: s.isCreate ? "Create “\(s.name)”" : s.name,
                            why: s.why,
                            titleWeight: s.isCreate ? .semibold : .medium
                        )
                        .padding(.horizontal, 8)
                        .padding(.vertical, 12)
                        .contentShape(Rectangle())
                        .onTapGesture { onPick(s) }
                        .onLongPressGesture {
                            if s.canEdit { onEdit(s) }
                        }
                    }
                }
            }
            .frame(maxHeight: 420)
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 24)
        .background(palette.surface)
        .presentationDetents([.large])
        .onAppear { focused = true }
    }
}

struct HabitEventSheet: View {
    @Environment(\.lifePalette) private var palette
    @Environment(\.dismiss) private var dismiss
    var event: LifeEvent
    var habitName: String
    var onDelete: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(habitName)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(palette.onSurface)
            Text(loggedAt)
                .foregroundStyle(palette.onSurfaceVariant)
            Button("Delete", role: .destructive, action: onDelete)
                .padding(.top, 8)
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 28)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface)
        .presentationDetents([.height(200)])
    }

    private var loggedAt: String {
        let fmt = DateFormatter()
        fmt.dateFormat = "EEE d MMM, HH:mm"
        return fmt.string(from: dateFromMs(event.createdAt))
    }
}

struct HabitLine: View {
    @Environment(\.lifePalette) private var palette
    var icon: String
    var title: String
    var why: String
    var titleWeight: Font.Weight

    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            Text(icon.isEmpty ? "•" : icon)
                .font(.system(size: 20))
                .frame(width: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 16, weight: titleWeight))
                    .foregroundStyle(palette.onSurface)
                if !why.isEmpty {
                    Text(why)
                        .font(.system(size: 12))
                        .foregroundStyle(palette.onSurfaceVariant)
                }
            }
            Spacer(minLength: 0)
        }
    }
}

private let habitIcons: [String] = {
    var seen = Set<String>()
    var out: [String] = []
    for icon in ["•"] + HabitCatalog.all.map(\.icon) {
        if seen.insert(icon).inserted { out.append(icon) }
    }
    return out
}()

struct HabitEditSheet: View {
    @Environment(\.lifePalette) private var palette
    var habit: HabitDef
    var logCount: Int
    var reassignEvent: Bool
    var otherNames: [String] = []
    var onSave: (String, String, String) -> Void
    var onSaveAsNew: (String, String, String) -> Void
    @State private var name: String
    @State private var icon: String
    @State private var why: String
    @FocusState private var focused: Bool

    init(
        habit: HabitDef,
        logCount: Int,
        reassignEvent: Bool,
        otherNames: [String] = [],
        onSave: @escaping (String, String, String) -> Void,
        onSaveAsNew: @escaping (String, String, String) -> Void
    ) {
        self.habit = habit
        self.logCount = logCount
        self.reassignEvent = reassignEvent
        self.otherNames = otherNames
        self.onSave = onSave
        self.onSaveAsNew = onSaveAsNew
        _name = State(initialValue: habit.name)
        _icon = State(initialValue: habit.icon.isEmpty ? "•" : habit.icon)
        _why = State(initialValue: habit.why)
    }

    var body: some View {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedWhy = why.trimmingCharacters(in: .whitespacesAndNewlines)
        let canSave = !trimmed.isEmpty
        let sameName = trimmed.caseInsensitiveCompare(habit.name) == .orderedSame
        let nameTaken = otherNames.contains { $0.caseInsensitiveCompare(trimmed) == .orderedSame }
        let canSaveAsNew = canSave && !sameName && !nameTaken
        let icons: [String] = {
            let current = habit.icon.isEmpty ? "•" : habit.icon
            return habitIcons.contains(current) ? habitIcons : [current] + habitIcons
        }()

        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text("Edit habit")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(palette.onSurface)
                HabitLine(
                    icon: icon,
                    title: trimmed.isEmpty ? habit.name : trimmed,
                    why: trimmedWhy,
                    titleWeight: .medium
                )
                .padding(.horizontal, 8)
                .padding(.vertical, 12)
                .background(palette.surfaceVariant, in: RoundedRectangle(cornerRadius: 12))

                TextField("Name", text: $name)
                    .textFieldStyle(LifeFieldStyle())
                    .focused($focused)
                TextField("Gray text", text: $why, prompt: Text("Shown under the name"))
                    .textFieldStyle(LifeFieldStyle())

                Text("Icon")
                    .font(.system(size: 12))
                    .foregroundStyle(palette.onSurfaceVariant)
                FlowRow(spacing: 8) {
                    ForEach(icons, id: \.self) { choice in
                        Button {
                            icon = choice
                        } label: {
                            Text(choice)
                                .font(.system(size: 18))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 8)
                                .background(
                                    (icon == choice ? palette.primary : palette.surfaceVariant),
                                    in: RoundedRectangle(cornerRadius: 12)
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }

                Button {
                    onSave(trimmed, icon, trimmedWhy)
                } label: {
                    Text("Save changes")
                        .font(.system(size: 16, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .disabled(!canSave)
                .padding(.top, 8)

                Text(saveHint(nameTaken: nameTaken, trimmed: trimmed))
                    .font(.system(size: 12))
                    .foregroundStyle(palette.onSurfaceVariant)

                Button {
                    onSaveAsNew(trimmed, icon, trimmedWhy)
                } label: {
                    Text("Save as new")
                        .font(.system(size: 16, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.bordered)
                .disabled(!canSaveAsNew)

                Text(reassignEvent
                     ? "This log becomes “\(trimmed)”. Other logs stay on “\(habit.name)”."
                     : "Creates a separate habit. Existing logs stay on “\(habit.name)”.")
                    .font(.system(size: 12))
                    .foregroundStyle(palette.onSurfaceVariant)
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 28)
        }
        .background(palette.surface)
        .presentationDetents([.large])
        .onAppear { focused = true }
    }

    private func saveHint(nameTaken: Bool, trimmed: String) -> String {
        if nameTaken { return "Merges all logs into the existing “\(trimmed)” habit." }
        if logCount <= 0 { return "No logs yet. This just updates the habit." }
        if logCount == 1 { return "Updates this habit on the one timeline log." }
        return "Updates this habit on all \(logCount) timeline logs."
    }
}

struct LifeFieldStyle: TextFieldStyle {
    @Environment(\.lifePalette) private var palette
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration
            .padding(12)
            .background(palette.background, in: RoundedRectangle(cornerRadius: 10))
            .overlay {
                RoundedRectangle(cornerRadius: 10)
                    .stroke(palette.onSurfaceVariant.opacity(0.35), lineWidth: 1)
            }
            .foregroundStyle(palette.onSurface)
    }
}
