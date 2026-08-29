import SwiftUI

enum TimelineFilter: String, CaseIterable, Identifiable {
    case all, habit, note, finance
    var id: String { rawValue }
    var label: String {
        switch self {
        case .all: return "All"
        case .habit: return "Habit"
        case .note: return "Note"
        case .finance: return "Finance"
        }
    }
}

enum CaptureKind { case habit, note, finance }

enum CaptureUi: Identifiable, Equatable {
    case composer
    case pickType
    case habit
    case note(LifeEvent?)
    case finance(LifeEvent?)
    case habitEvent(LifeEvent)
    case editHabit(habit: HabitDef, sourceEventId: String?, returnToPicker: Bool)
    case reflect(LifeEvent)

    var id: String {
        switch self {
        case .composer: return "composer"
        case .pickType: return "pick"
        case .habit: return "habit"
        case .note(let e): return "note-\(e?.id ?? "new")"
        case .finance(let e): return "fin-\(e?.id ?? "new")"
        case .habitEvent(let e): return "he-\(e.id)"
        case .editHabit(let h, _, _): return "edit-\(h.id)"
        case .reflect(let e): return "ref-\(e.id)"
        }
    }
}

struct TimelineView: View {
    @EnvironmentObject private var vm: LifeViewModel
    @Environment(\.lifePalette) private var palette
    @State private var filter: TimelineFilter = .all
    @State private var capture: CaptureUi?
    @State private var habitQuery = ""

    var body: some View {
        let today = WantDomain.todayComponents()
        let upcoming = WantDomain.upcoming(vm.data, today: today)
        let filtered: [LifeEvent] = {
            switch filter {
            case .all: return vm.data.events
            case .habit: return vm.data.events.filter { $0.kind == .habit }
            case .note: return vm.data.events.filter { $0.kind == .note }
            case .finance: return vm.data.events.filter { $0.kind == .finance }
            }
        }()
        let groups = groupEvents(filtered)
        let suggestions = HabitRanker.suggest(vm.data, nowMs: nowMs(), query: habitQuery)

        NavigationStack {
            ZStack(alignment: .bottomTrailing) {
                palette.background.ignoresSafeArea()
                VStack(alignment: .leading, spacing: 0) {
                    header
                    UpcomingStrip(
                        items: upcoming,
                        today: today,
                        onDone: {
                            LifeHaptics.success()
                            vm.completeWant($0.id)
                        },
                        onDidnt: {
                            LifeHaptics.tick()
                            capture = .reflect($0)
                        },
                        onOpen: { capture = .note($0) }
                    )
                    filterRow
                    if vm.showCorruptBanner {
                        Text("Could not read saved data. Showing empty until you restore a backup.")
                            .font(.system(size: 13))
                            .foregroundStyle(palette.error)
                            .padding(.horizontal, 20)
                            .padding(.vertical, 8)
                            .onTapGesture { vm.dismissCorrupt() }
                    }
                    if groups.isEmpty {
                        Spacer()
                        VStack(spacing: 6) {
                            Text(filter == .all ? "Nothing yet." : "Nothing in this filter.")
                                .font(.system(size: 18, weight: .medium))
                                .foregroundStyle(palette.onSurface)
                            Text("Speak or type with +.")
                                .foregroundStyle(palette.onSurfaceVariant)
                        }
                        .frame(maxWidth: .infinity)
                        Spacer()
                    } else {
                        ScrollView {
                            LazyVStack(alignment: .leading, spacing: 0) {
                                ForEach(groups, id: \.date) { day in
                                    Text(day.label)
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundStyle(palette.onSurfaceVariant)
                                        .padding(.horizontal, 20)
                                        .padding(.vertical, 8)
                                    ForEach(day.events) { event in
                                        EventRow(event: event, data: vm.data)
                                            .contentShape(Rectangle())
                                            .onTapGesture {
                                                switch event.kind {
                                                case .habit: capture = .habitEvent(event)
                                                case .note: capture = .note(event)
                                                case .finance: capture = .finance(event)
                                                }
                                            }
                                            .onLongPressGesture {
                                                guard event.kind == .habit, let hid = event.habitId,
                                                      let habit = HabitDomain.defForEdit(vm.data, habitId: hid)
                                                else { return }
                                                LifeHaptics.tick()
                                                capture = .editHabit(habit: habit, sourceEventId: event.id, returnToPicker: false)
                                            }
                                    }
                                }
                            }
                            .padding(.bottom, 96)
                            .padding(.top, 8)
                        }
                    }
                }
                Button {
                    LifeHaptics.tick()
                    capture = .composer
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(palette.onPrimary)
                        .frame(width: 56, height: 56)
                        .background(palette.primary, in: Circle())
                }
                .padding(20)
            }
            .toolbar(.hidden, for: .navigationBar)
        }
        .sheet(item: $capture) { item in
            captureSheet(item, suggestions: suggestions)
                .environment(\.lifePalette, palette)
                .environmentObject(vm)
                .presentationDragIndicator(.visible)
        }
    }

    private var header: some View {
        HStack(alignment: .center, spacing: 8) {
            Text("Life")
                .font(.system(size: 28, weight: .bold))
                .foregroundStyle(palette.onBackground)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button {
                LifeHaptics.tick()
                capture = .finance(nil)
            } label: {
                Text(Money.format(vm.data.balanceMinor(), currencyCode: vm.data.currencyCode))
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(palette.onSurface)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(palette.surfaceVariant, in: Capsule())
            }
            .buttonStyle(.plain)
            Button { vm.showSettings = true } label: {
                Image(systemName: "gearshape")
                    .font(.system(size: 18))
                    .foregroundStyle(palette.onSurface)
                    .padding(8)
            }
            .buttonStyle(.plain)
        }
        .padding(.leading, 20)
        .padding(.trailing, 8)
        .padding(.top, 8)
        .padding(.bottom, 4)
    }

    private var filterRow: some View {
        HStack(spacing: 8) {
            ForEach(TimelineFilter.allCases) { f in
                TagChip(label: f.label, selected: filter == f) { filter = f }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private func captureSheet(_ item: CaptureUi, suggestions: [HabitSuggestion]) -> some View {
        switch item {
        case .composer:
            CaptureComposer(
                onUseForm: {
                    LifeHaptics.tick()
                    capture = .pickType
                },
                onNeedReflection: { capture = .reflect($0) },
                onDismiss: { capture = nil }
            )
        case .pickType:
            CaptureTypeSheet { kind in
                LifeHaptics.tick()
                switch kind {
                case .habit:
                    habitQuery = ""
                    capture = .habit
                case .note:
                    capture = .note(nil)
                case .finance:
                    capture = .finance(nil)
                }
            }
        case .habit:
            HabitPickerSheet(
                query: $habitQuery,
                suggestions: suggestions,
                onPick: {
                    LifeHaptics.success()
                    vm.logHabit($0)
                    habitQuery = ""
                    capture = nil
                },
                onEdit: { suggestion in
                    guard let id = suggestion.habitId,
                          let habit = HabitDomain.defForEdit(vm.data, habitId: id) else { return }
                    LifeHaptics.tick()
                    capture = .editHabit(habit: habit, sourceEventId: nil, returnToPicker: true)
                }
            )
        case .note(let editing):
            NoteSheet(
                availableTags: vm.data.availableTags(),
                editing: editing,
                onSave: { body, tags, isWant, dueDate in
                    LifeHaptics.success()
                    if let existing = editing {
                        vm.updateNote(existing: existing, body: body, tags: tags, isWant: isWant, dueDate: dueDate)
                    } else {
                        vm.logNote(body: body, tags: tags, isWant: isWant, dueDate: dueDate)
                    }
                    capture = nil
                },
                onDelete: editing.map { ev in
                    { vm.deleteEvent(ev.id); capture = nil }
                }
            )
        case .finance(let editing):
            FinanceSheet(
                data: vm.data,
                editing: editing,
                onSave: { dir, amt, payee, cat, memo in
                    LifeHaptics.success()
                    if let existing = editing {
                        var copy = existing
                        copy.direction = dir
                        copy.amountMinor = amt
                        copy.payee = payee.trimmingCharacters(in: .whitespacesAndNewlines)
                        copy.category = cat.trimmingCharacters(in: .whitespacesAndNewlines)
                        copy.body = memo.trimmingCharacters(in: .whitespacesAndNewlines)
                        vm.updateEvent(copy)
                    } else {
                        vm.logFinance(direction: dir, amountMinor: amt, payee: payee, category: cat, memo: memo)
                    }
                    capture = nil
                },
                onDelete: editing.map { ev in
                    { vm.deleteEvent(ev.id); capture = nil }
                }
            )
        case .habitEvent(let event):
            HabitEventSheet(
                event: event,
                habitName: event.habitId.flatMap { vm.data.habitById($0)?.name } ?? "Habit",
                onDelete: { vm.deleteEvent(event.id); capture = nil }
            )
        case .editHabit(let habit, let sourceEventId, let returnToPicker):
            HabitEditSheet(
                habit: habit,
                logCount: HabitDomain.logCount(vm.data, habitId: habit.id),
                reassignEvent: sourceEventId != nil,
                otherNames: vm.data.habits.filter { !$0.archived && $0.id != habit.id }.map(\.name),
                onSave: { name, icon, why in
                    LifeHaptics.success()
                    vm.editHabit(habitId: habit.id, name: name, icon: icon, why: why)
                    capture = returnToPicker ? .habit : nil
                },
                onSaveAsNew: { name, icon, why in
                    LifeHaptics.success()
                    vm.saveHabitAsNew(name: name, icon: icon, why: why, reassignEventId: sourceEventId)
                    capture = returnToPicker ? .habit : nil
                }
            )
        case .reflect(let event):
            ReflectionSheet(
                event: event,
                streak: vm.data.wantStrikeStreak,
                onSubmit: { text in
                    LifeHaptics.success()
                    vm.failWant(id: event.id, reflection: text)
                    capture = nil
                }
            )
        }
    }
}
