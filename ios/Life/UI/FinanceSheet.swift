import SwiftUI

struct FinanceSheet: View {
    @Environment(\.lifePalette) private var palette
    var data: AppData
    var editing: LifeEvent? = nil
    var onSave: (MoneyDirection, Int64, String, String, String) -> Void
    var onDelete: (() -> Void)? = nil
    @State private var direction: MoneyDirection
    @State private var amountText: String
    @State private var payee: String
    @State private var category: String
    @State private var memo: String
    @FocusState private var amountFocused: Bool

    init(
        data: AppData,
        editing: LifeEvent? = nil,
        onSave: @escaping (MoneyDirection, Int64, String, String, String) -> Void,
        onDelete: (() -> Void)? = nil
    ) {
        self.data = data
        self.editing = editing
        self.onSave = onSave
        self.onDelete = onDelete
        _direction = State(initialValue: editing?.direction ?? .expense)
        _amountText = State(initialValue: editing?.amountMinor.map { Money.formatMajorPlain($0, currencyCode: data.currencyCode) } ?? "")
        _payee = State(initialValue: editing?.payee ?? "")
        _category = State(initialValue: editing?.category ?? "")
        _memo = State(initialValue: editing?.body ?? "")
    }

    var body: some View {
        let suggestion = FinanceRanker.suggest(data, direction: direction, query: payee, selectedPayee: payee)
        let amountMinor = Money.parseToMinor(amountText, currencyCode: data.currencyCode)
        let current = data.balanceMinor()
        let after: Int64 = {
            if editing == nil, let amountMinor, amountMinor > 0 {
                return current + (direction == .income ? amountMinor : -amountMinor)
            }
            return current
        }()

        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(editing == nil ? "Finance" : "Edit transaction")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(palette.onSurface)
                    Spacer()
                    if let onDelete {
                        Button("Delete", role: .destructive, action: onDelete)
                    }
                }
                Text(balanceLine(current: current, after: after, amountMinor: amountMinor))
                    .font(.system(size: 13))
                    .foregroundStyle(palette.onSurfaceVariant)

                HStack(spacing: 10) {
                    dirButton("+ Income", selected: direction == .income, color: LifePalette.incomeGreen) {
                        direction = .income
                    }
                    dirButton("− Expense", selected: direction == .expense, color: LifePalette.expenseRed) {
                        direction = .expense
                    }
                }
                .padding(.top, 6)

                TextField("Amount", text: $amountText)
                    .keyboardType(.decimalPad)
                    .textFieldStyle(LifeFieldStyle())
                    .focused($amountFocused)

                if let suggested = suggestion.suggestedAmountMinor, amountText.trimmingCharacters(in: .whitespaces).isEmpty {
                    Button("Use \(Money.format(suggested, currencyCode: data.currencyCode))") {
                        amountText = Money.formatMajorPlain(suggested, currencyCode: data.currencyCode)
                    }
                    .font(.system(size: 14))
                }

                TextField("Payee", text: $payee, prompt: Text("Coffee, rent, salary…"))
                    .textFieldStyle(LifeFieldStyle())

                if !suggestion.payees.isEmpty {
                    FlowRow(spacing: 8) {
                        ForEach(suggestion.payees.prefix(8), id: \.name) { p in
                            TagChip(label: p.name, selected: payee.caseInsensitiveCompare(p.name) == .orderedSame) {
                                payee = p.name
                            }
                        }
                    }
                }

                Text("Category")
                    .font(.system(size: 12))
                    .foregroundStyle(palette.onSurfaceVariant)
                    .padding(.top, 4)
                FlowRow(spacing: 8) {
                    ForEach(data.categories, id: \.self) { c in
                        TagChip(label: c, selected: category.caseInsensitiveCompare(c) == .orderedSame) {
                            category = c
                        }
                    }
                }

                TextField("Memo (optional)", text: $memo)
                    .textFieldStyle(LifeFieldStyle())

                Button {
                    guard let amt = amountMinor, amt > 0 else { return }
                    onSave(direction, amt, payee, category, memo)
                } label: {
                    Text(editing == nil ? "Save" : "Update")
                        .font(.system(size: 16, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .disabled(amountMinor == nil || (amountMinor ?? 0) <= 0)
                .padding(.top, 8)
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 28)
        }
        .background(palette.surface)
        .presentationDetents([.large])
        .onAppear {
            amountFocused = true
            if category.isEmpty, let suggested = suggestion.suggestedCategory, !suggested.isEmpty {
                category = suggested
            }
        }
        .onChange(of: suggestion.suggestedCategory) { _, new in
            if category.isEmpty, let new, !new.isEmpty {
                category = new
            }
        }
    }

    private func balanceLine(current: Int64, after: Int64, amountMinor: Int64?) -> String {
        let cur = Money.format(current, currencyCode: data.currencyCode)
        if editing == nil, let amountMinor, amountMinor > 0 {
            return "Balance \(cur)  →  \(Money.format(after, currencyCode: data.currencyCode))"
        }
        return "Balance \(cur)"
    }

    private func dirButton(_ label: String, selected: Bool, color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(selected ? palette.onPrimary : palette.onSurface)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(selected ? color : palette.surfaceVariant, in: RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
    }
}
