import SwiftUI

@main
struct LifeApp: App {
    @StateObject private var vm = LifeViewModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(vm)
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var vm: LifeViewModel

    var body: some View {
        let palette = LifePalette.from(backgroundId: vm.data.backgroundMode, accentId: vm.data.colorScheme)
        Group {
            if vm.showSettings {
                SettingsView()
            } else {
                TimelineView()
            }
        }
        .environment(\.lifePalette, palette)
        .preferredColorScheme(palette.isLight ? .light : .dark)
        .tint(palette.primary)
    }
}
