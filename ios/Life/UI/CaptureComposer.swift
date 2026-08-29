import SwiftUI

struct CaptureComposer: View {
    @EnvironmentObject private var vm: LifeViewModel
    @Environment(\.lifePalette) private var palette
    var onUseForm: () -> Void
    var onNeedReflection: (LifeEvent) -> Void
    var onDismiss: () -> Void

    @State private var text = ""
    @State private var listening = false
    @State private var pending: NeedleDecision?
    @State private var flash: String?
    @State private var lang = "en"
    @State private var recorder = ClipRecorder()
    @State private var status = "Typed capture ready (offline). Hold mic for on-device speech."

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("What happened?")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(palette.onSurface)
            Text(status)
                .font(.system(size: 12))
                .foregroundStyle(palette.onSurfaceVariant)
            HStack(spacing: 10) {
                TextField("popio kreatin · spent 450 lidl", text: $text)
                    .textFieldStyle(LifeFieldStyle())
                    .disabled(listening)
                Circle()
                    .fill(listening ? Color.red : palette.primary)
                    .frame(width: 56, height: 56)
                    .overlay {
                        Image(systemName: "mic.fill")
                            .foregroundStyle(palette.onPrimary)
                    }
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { _ in startMic() }
                            .onEnded { _ in Task { await finishMic() } }
                    )
            }
            HStack(spacing: 8) {
                Button {
                    runText(text)
                } label: {
                    Label("Log", systemImage: "checkmark")
                }
                .buttonStyle(.borderedProminent)
                .disabled(text.trimmingCharacters(in: .whitespaces).isEmpty || listening)
                Button("Use a form", action: onUseForm)
                    .buttonStyle(.plain)
                    .foregroundStyle(palette.primary)
            }
            if let pending {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Confirm").font(.system(size: 15, weight: .medium))
                    Text(pending.label).font(.system(size: 14))
                    HStack {
                        Button("Yes") { commit(pending) }
                            .buttonStyle(.borderedProminent)
                        Button("No") { self.pending = nil }
                            .buttonStyle(.bordered)
                    }
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(palette.surfaceVariant, in: RoundedRectangle(cornerRadius: 14))
            }
            if let flash {
                Text(flash)
                    .foregroundStyle(palette.primary)
                    .font(.system(size: 15, weight: .medium))
            }
            if listening {
                Text("Listening… release to log")
                    .foregroundStyle(.red)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 28)
        .background(palette.surface)
        .presentationDetents([.medium, .large])
    }

    private func startMic() {
        guard !listening else { return }
        listening = true
        LifeHaptics.tick()
        try? recorder.start()
    }

    private func finishMic() async {
        recorder.stop()
        listening = false
        let ok = await OnDeviceSpeech.authorize()
        guard ok else {
            flash = "Mic or speech not allowed"
            return
        }
        let spoken = await OnDeviceSpeech.transcribe(url: recorder.url, lang: lang)
        if let spoken, !spoken.isEmpty {
            text = spoken
            runText(spoken)
        } else {
            flash = "Didn't catch that — type instead"
        }
    }

    private func runText(_ input: String) {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        lang = Self.detectLang(trimmed, sticky: lang)
        let ctx = NeedleContext(data: vm.data, today: WantDomain.todayComponents(), currencyCode: vm.data.currencyCode)
        let decision = PatternNeedle.dispatch(trimmed, ctx: ctx)
        if decision.canAutoCommit { commit(decision) } else { pending = decision }
    }

    private static func detectLang(_ text: String, sticky: String) -> String {
        let lower = text.lowercased()
        let sr = ["popio", "potrosio", "potrošio", "beleška", "beleska", "sutra", "danas", "želim", "zelim", "dinara"]
        if sr.contains(where: { lower.contains($0) }) { return "sr" }
        if lower.contains(where: { "čćšžđ".contains($0) }) { return "sr" }
        if text.trimmingCharacters(in: .whitespaces).isEmpty { return sticky }
        return "en"
    }

    private func commit(_ decision: NeedleDecision) {
        switch decision {
        case .habit(let name, _, _):
            vm.logHabitNamed(name)
            flash = "Logged \(name)"
        case .note(let body, let tags, let wantDate, _, _):
            vm.logNote(body: body, tags: tags, isWant: wantDate != nil, dueDate: wantDate)
            flash = wantDate == nil ? "Note saved" : "Want saved"
        case .finance(let dir, let amt, let payee, let cat, let memo, _, _):
            guard let amt, amt > 0 else { pending = decision; return }
            vm.logFinance(direction: dir, amountMinor: amt, payee: payee, category: cat, memo: memo)
            flash = "Logged money"
        case .completeWant(let text, _, _):
            let open = vm.data.events.filter { $0.isWant && $0.wantStatus == .open }
            if let hit = open.max(by: { Fuzzy.score(text, $0.body) < Fuzzy.score(text, $1.body) }),
               Fuzzy.score(text, hit.body) >= 0.4 {
                vm.completeWant(hit.id)
                flash = "Done: \(hit.body)"
            } else {
                flash = "No matching Want"
                pending = decision
                return
            }
        case .missWant(let text, _, _):
            let open = vm.data.events.filter { $0.isWant && $0.wantStatus == .open }
            if let hit = open.max(by: { Fuzzy.score(text, $0.body) < Fuzzy.score(text, $1.body) }) {
                onNeedReflection(hit)
                return
            }
            flash = "No matching Want"
        case .balance:
            flash = "Balance \(Money.format(vm.data.balanceMinor(), currencyCode: vm.data.currencyCode))"
        case .unknown:
            pending = decision
            return
        }
        LifeHaptics.success()
        pending = nil
        text = ""
    }
}
