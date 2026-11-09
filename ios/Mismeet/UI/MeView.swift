import SwiftUI

/// One screen without scrolling: the invite, the name, the switch for sharing and the way to the
/// setup checks, which hold everything else.
struct MeView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if let image = QRCode.image(for: model.inviteLink) {
                        Image(uiImage: image)
                            .resizable()
                            .interpolation(.none)
                            .scaledToFit()
                            .frame(width: 150, height: 150)
                            .frame(maxWidth: .infinity)
                    }
                    HStack {
                        if let link = URL(string: model.inviteLink) {
                            ShareLink(item: link, message: Text("Add me on Mismeet")) {
                                Label("Share invite", systemImage: "square.and.arrow.up")
                            }
                        }
                        Spacer()
                        Button("Copy link", systemImage: "doc.on.doc") {
                            UIPasteboard.general.string = model.inviteLink
                        }
                    }
                    .buttonStyle(.borderless)
                } header: {
                    Text("Invite")
                } footer: {
                    Text("Send the link, or let them scan the code.")
                }
                Section("Your name") {
                    TextField("Your name", text: Binding(get: { model.state.ownName }, set: { model.setOwnName($0) }))
                        .textContentType(.givenName)
                }
                Section {
                    Toggle("Share my location", isOn: Binding(
                        get: { model.state.sharingEnabled },
                        set: { model.setSharingEnabled($0) }
                    ))
                    if model.state.sharingEnabled {
                        LabeledContent("Permission", value: model.authorizationText)
                        if model.authorization != .authorizedAlways {
                            Button("Allow location") { model.requestLocationAuthorization() }
                        }
                    }
                } header: {
                    Text("Sharing from this phone")
                } footer: {
                    if !model.state.sharingEnabled {
                        Text("Off: this phone only watches its contacts and needs no location permission.")
                    }
                }
                Section {
                    NavigationLink("Check setup") { SetupChecksView() }
                }
            }
            .listSectionSpacing(.compact)
            .scrollBounceBehavior(.basedOnSize)
            .navigationTitle("Me")
        }
    }
}
