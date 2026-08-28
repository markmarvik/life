import SwiftUI

struct TagChip: View {
    @Environment(\.lifePalette) private var palette
    let label: String
    let selected: Bool
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 13))
                .foregroundStyle(selected ? palette.onPrimary : palette.onSurface)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(selected ? palette.primary : palette.surfaceVariant, in: Capsule())
                .overlay {
                    if !selected {
                        Capsule().stroke(palette.onSurfaceVariant.opacity(0.35), lineWidth: 1)
                    }
                }
        }
        .buttonStyle(.plain)
    }
}

struct DateChipRow: View {
    @Environment(\.lifePalette) private var palette
    var dueDate: String?
    var today: DateComponents = WantDomain.todayComponents()
    var onDueDate: (String?) -> Void
    @State private var showPicker = false

    var body: some View {
        let tomorrow = WantDomain.addDays(today, 1)
        let due = WantDomain.parseDue(dueDate)
        let custom = due != nil
            && WantDomain.compare(due!, today) != .orderedSame
            && WantDomain.compare(due!, tomorrow) != .orderedSame

        FlowRow(spacing: 8) {
            TagChip(label: "Today", selected: due.map { WantDomain.compare($0, today) == .orderedSame } ?? false) {
                if let due, WantDomain.compare(due, today) == .orderedSame {
                    onDueDate(nil)
                } else {
                    onDueDate(WantDomain.iso(today))
                }
            }
            TagChip(label: "Tomorrow", selected: due.map { WantDomain.compare($0, tomorrow) == .orderedSame } ?? false) {
                if let due, WantDomain.compare(due, tomorrow) == .orderedSame {
                    onDueDate(nil)
                } else {
                    onDueDate(WantDomain.iso(tomorrow))
                }
            }
            if custom, let due {
                TagChip(label: WantDomain.historyLabel(WantDomain.iso(due), today: today), selected: true) {
                    onDueDate(nil)
                }
            }
            TagChip(label: "Pick…", selected: false) {
                showPicker = true
            }
        }
        .sheet(isPresented: $showPicker) {
            DatePickerSheet(
                seed: due.map { WantDomain.addDays($0, 0) } ?? today
            ) { picked in
                onDueDate(WantDomain.iso(picked))
            }
            .environment(\.lifePalette, palette)
        }
    }
}

struct DatePickerSheet: View {
    @Environment(\.lifePalette) private var palette
    @Environment(\.dismiss) private var dismiss
    var seed: DateComponents
    var onPick: (DateComponents) -> Void
    @State private var date: Date

    init(seed: DateComponents, onPick: @escaping (DateComponents) -> Void) {
        self.seed = seed
        self.onPick = onPick
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = .current
        let d = cal.date(from: DateComponents(year: seed.year, month: seed.month, day: seed.day)) ?? Date()
        _date = State(initialValue: d)
    }

    var body: some View {
        NavigationStack {
            DatePicker("Date", selection: $date, displayedComponents: .date)
                .datePickerStyle(.graphical)
                .padding()
                .navigationTitle("Pick a day")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { dismiss() }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("OK") {
                            onPick(WantDomain.todayComponents(date))
                            dismiss()
                        }
                    }
                }
        }
        .presentationDetents([.medium, .large])
        .background(palette.background)
    }
}

/// Simple wrapping row (iOS 16+ Layout).
struct FlowRow<Content: View>: View {
    var spacing: CGFloat = 8
    @ViewBuilder var content: () -> Content

    var body: some View {
        FlexibleWrap(spacing: spacing) {
            content()
        }
    }
}

private struct FlexibleWrap: Layout {
    var spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let result = arrange(proposal: proposal, subviews: subviews)
        return result.size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = arrange(proposal: proposal, subviews: subviews)
        for (sub, origin) in zip(subviews, result.origins) {
            sub.place(at: CGPoint(x: bounds.minX + origin.x, y: bounds.minY + origin.y), proposal: .unspecified)
        }
    }

    private func arrange(proposal: ProposedViewSize, subviews: Subviews) -> (size: CGSize, origins: [CGPoint]) {
        let maxWidth = proposal.width ?? 390
        var origins: [CGPoint] = []
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        var width: CGFloat = 0
        for sub in subviews {
            let size = sub.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > maxWidth {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            origins.append(CGPoint(x: x, y: y))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
            width = max(width, x - spacing)
        }
        return (CGSize(width: width, height: y + rowHeight), origins)
    }
}
