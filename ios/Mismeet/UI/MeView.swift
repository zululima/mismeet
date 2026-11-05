import SwiftUI

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
                            .frame(maxWidth: 240)
                            .frame(maxWidth: .infinity)
                    }
                    if let link = URL(string: model.inviteLink) {
                        ShareLink(item: link, message: Text("Add me on Mismeet")) {
                            Label("Share invite", systemImage: "square.and.arrow.up")
                        }
                    }
                    Button("Copy invite link", systemImage: "doc.on.doc") {
                        UIPasteboard.general.string = model.inviteLink
                    }
                } header: {
                    Text("Invite")
                } footer: {
                    Text("Send the link, or let them scan the code. When they add you with sharing on, your contacts show a request to add them back.")
                }
                Section {
                    TextField("Your name", text: Binding(get: { model.state.ownName }, set: { model.setOwnName($0) }))
                        .textContentType(.givenName)
                } header: {
                    Text("Your name")
                } footer: {
                    Text("Sent to the people you add, so they know who asks to be added back.")
                }
                Section("Sharing from this phone") {
                    Toggle("Share my location", isOn: Binding(
                        get: { model.state.sharingEnabled },
                        set: { model.setSharingEnabled($0) }
                    ))
                    if model.state.sharingEnabled {
                        LabeledContent("Permission", value: model.authorizationText)
                        if model.authorization != .authorizedAlways {
                            Button("Allow location") { model.requestLocationAuthorization() }
                        }
                        Button("Share now") { model.publishNow() }
                            .disabled(model.isPublishing)
                        if let at = model.state.lastPublishedAt {
                            LabeledContent("Last shared with contacts", value: at.formatted(date: .abbreviated, time: .shortened))
                        }
                    } else {
                        Text("Off: this phone only watches its contacts, shares nothing and needs no location permission.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
                Section {
                    Toggle("Send problem reports", isOn: Binding(
                        get: { model.state.reportsEnabled },
                        set: { model.setReportsEnabled($0) }
                    ))
                } header: {
                    Text("Problem reports")
                } footer: {
                    Text("When a share fails or the app crashes, sends the developer an encrypted report, at most once a day, from a key made for that report alone. It holds the setup checks and the app's recent messages, never your location, contacts or keys. Check setup shows a report before you send one.")
                }
                Section {
                    NavigationLink("Check setup") { SetupChecksView() }
                }
            }
            .navigationTitle("Me")
        }
    }
}
