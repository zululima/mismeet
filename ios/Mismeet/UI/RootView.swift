import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.scenePhase) private var scenePhase
    @State private var screen: Screen = Screen.testTab == "me" ? .me : .map
    @State private var focused: String?
    @State private var panel: PanelHeight = Screen.testTab == "contacts" ? .large : .medium

    enum Screen: Hashable {
        case map, me

        // Test hook: MISMEET_TEST_TAB=contacts or me in the launch environment of a debug build;
        // contacts opens the map with the contact list pulled up.
        static var testTab: String? {
            #if DEBUG
            ProcessInfo.processInfo.environment["MISMEET_TEST_TAB"]
            #else
            nil
            #endif
        }
    }

    var body: some View {
        TabView(selection: $screen) {
            Tab("Map", systemImage: "map", value: .map) {
                ContactsMapView(focused: $focused, panel: $panel)
                    // The panel runs on under the tab bar, as Find My's sheet does.
                    .toolbarBackground(.hidden, for: .tabBar)
            }
            Tab("Me", systemImage: "person.crop.circle", value: .me) {
                MeView()
            }
        }
        .alert(model.notice ?? "", isPresented: Binding(
            get: { model.notice != nil },
            set: { if !$0 { model.notice = nil } }
        )) {}
        .onOpenURL { url in
            model.openedInvite = url.absoluteString
            focused = nil
            screen = .map
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { model.becameActive() }
        }
        .task(id: model.state.contacts.map { "\($0.id):\($0.lastEventId ?? "")" }) {
            while !Task.isCancelled {
                await model.updatePlaceNames()
                try? await Task.sleep(for: .seconds(ProtocolConstants.foregroundFetchIntervalSeconds))
            }
        }
        .task {
            while !Task.isCancelled {
                await model.refresh()
                try? await Task.sleep(for: .seconds(ProtocolConstants.foregroundFetchIntervalSeconds))
            }
        }
    }
}
