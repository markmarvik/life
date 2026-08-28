import SwiftUI

struct EventRow: View {
    @Environment(\.lifePalette) private var palette
    var event: LifeEvent
    var data: AppData

    var body: some View {
        let muted = palette.onSurfaceVariant
        let time = timeString(event.timelineAt)
        let today = WantDomain.todayComponents()

        HStack(alignment: .top, spacing: 12) {
            EventGlyph(event: event, data: data)
            VStack(alignment: .leading, spacing: 2) {
                switch event.kind {
                case .habit:
                    Text(event.habitId.flatMap { data.habitById($0)?.name } ?? "Habit")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(palette.onSurface)
                case .note:
                    NoteBody(event: event, today: today, muted: muted)
                case .finance:
                    let dir = event.direction ?? .expense
                    Text(Money.formatSigned(event.amountMinor ?? 0, direction: dir, currencyCode: data.currencyCode))
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(dir == .income ? LifePalette.incomeGreen : LifePalette.expenseRed)
                    let subtitle = [event.payee, event.category].filter { !$0.isEmpty }.joined(separator: " · ")
                    if !subtitle.isEmpty {
                        Text(subtitle).font(.system(size: 13)).foregroundStyle(muted)
                    }
                    if !event.body.isEmpty {
                        Text(event.body)
                            .font(.system(size: 13))
                            .foregroundStyle(muted)
                            .lineLimit(1)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Text(time)
                .font(.system(size: 12))
                .foregroundStyle(muted)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
    }

    private func timeString(_ ms: Int64) -> String {
        let fmt = DateFormatter()
        fmt.dateFormat = "HH:mm"
        return fmt.string(from: dateFromMs(ms))
    }
}

private struct NoteBody: View {
    @Environment(\.lifePalette) private var palette
    var event: LifeEvent
    var today: DateComponents
    var muted: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(event.body)
                .font(.system(size: 15))
                .foregroundStyle(palette.onSurface)
                .lineLimit(3)
            if !chips.isEmpty {
                TagStrip(tags: chips)
                    .padding(.top, 4)
            }
            if !event.reflection.isEmpty {
                Text(event.reflection)
                    .font(.system(size: 13))
                    .foregroundStyle(muted)
                    .lineLimit(3)
                    .padding(.top, 2)
            }
        }
    }

    private var chips: [String] {
        var out: [String] = []
        if event.isWant {
            switch event.wantStatus {
            case .done: out.append("Want · done")
            case .canceled: out.append("Want · didn’t")
            case .missed: out.append("Want · missed")
            default: out.append("Want")
            }
        }
        if let due = event.dueDate {
            let label = WantDomain.historyLabel(due, today: today)
            if !label.isEmpty { out.append(label) }
        }
        out.append(contentsOf: event.tags)
        return out.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
    }
}

struct EventGlyph: View {
    @Environment(\.lifePalette) private var palette
    var event: LifeEvent
    var data: AppData

    var body: some View {
        ZStack {
            Circle().fill(palette.surfaceVariant)
            switch event.kind {
            case .habit:
                let icon = event.habitId.flatMap { data.habitById($0)?.icon } ?? ""
                Text(icon.isEmpty ? "•" : icon).font(.system(size: 18))
            case .note:
                Text(noteGlyph).font(.system(size: 16))
            case .finance:
                let dir = event.direction ?? .expense
                Text(dir == .income ? "+" : "−")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(dir == .income ? LifePalette.incomeGreen : LifePalette.expenseRed)
            }
        }
        .frame(width: 40, height: 40)
    }

    private var noteGlyph: String {
        switch event.wantStatus {
        case .done: return "✓"
        case .canceled, .missed: return "✕"
        default: return "✎"
        }
    }
}

struct TagStrip: View {
    @Environment(\.lifePalette) private var palette
    var tags: [String]

    var body: some View {
        FlowRow(spacing: 6) {
            ForEach(tags, id: \.self) { tag in
                Text(tag)
                    .font(.system(size: 11))
                    .foregroundStyle(palette.onSurfaceVariant)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(palette.surfaceVariant, in: RoundedRectangle(cornerRadius: 8))
            }
        }
    }
}
