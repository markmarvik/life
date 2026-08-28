import SwiftUI

struct UpcomingStrip: View {
    @Environment(\.lifePalette) private var palette
    var items: [LifeEvent]
    var today: DateComponents
    var onDone: (LifeEvent) -> Void
    var onDidnt: (LifeEvent) -> Void
    var onOpen: (LifeEvent) -> Void

    var body: some View {
        if !items.isEmpty {
            upcomingContent
        }
    }

    private var upcomingContent: some View {
        let shown = Array(items.prefix(5))
        let extra = items.count - shown.count
        return VStack(alignment: .leading, spacing: 8) {
            Text("Upcoming")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(palette.onSurfaceVariant)
                .padding(.leading, 4)
            ForEach(shown) { event in
                WantCard(
                    event: event,
                    today: today,
                    onDone: { onDone(event) },
                    onDidnt: { onDidnt(event) },
                    onOpen: { onOpen(event) }
                )
            }
            if extra > 0 {
                Text("and \(extra) more")
                    .font(.system(size: 12))
                    .foregroundStyle(palette.onSurfaceVariant)
                    .padding(.leading, 4)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 4)
    }
}

private struct WantCard: View {
    @Environment(\.lifePalette) private var palette
    var event: LifeEvent
    var today: DateComponents
    var onDone: () -> Void
    var onDidnt: () -> Void
    var onOpen: () -> Void

    var body: some View {
        let overdue = WantDomain.isOverdue(event, today: today)
        let label = WantDomain.upcomingLabel(event.dueDate, today: today)
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .center) {
                Text(event.body)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(palette.onSurface)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(label)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(overdue ? LifePalette.expenseRed : palette.primary)
            }
            HStack {
                Spacer()
                Button("Didn’t", action: onDidnt)
                    .font(.system(size: 15, weight: .medium))
                Button("Done", action: onDone)
                    .font(.system(size: 15, weight: .medium))
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(palette.surfaceVariant, in: RoundedRectangle(cornerRadius: 16))
        .contentShape(Rectangle())
        .onTapGesture(perform: onOpen)
    }
}
