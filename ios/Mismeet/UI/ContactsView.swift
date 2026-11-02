import CoreLocation
import PhotosUI
import SwiftUI

/// How far the panel at the bottom of the map is pulled up, as Find My's sheet: the header alone,
/// about a third of the screen, or most of it.
enum PanelHeight {
    case collapsed, medium, large

    /// The height of what the panel shows below its header, as a share of the screen.
    var fraction: CGFloat {
        switch self {
        case .collapsed: 0
        case .medium: 0.27
        case .large: 0.62
        }
    }

    var higher: PanelHeight { self == .collapsed ? .medium : .large }
    var lower: PanelHeight { self == .large ? .medium : .collapsed }
}

/// The contacts in a panel at the bottom of the map, like Find My's people: the header stays in
/// view, and pulling it up or down moves between the heights of `PanelHeight`.
struct ContactList: View {
    private struct AddRequest: Identifiable {
        let id = UUID()
        var invite = ""
    }

    @Environment(AppModel.self) private var model
    @State private var adding: AddRequest?
    @Binding var height: PanelHeight
    /// Shows a contact on the map.
    let focus: (String) -> Void

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 8) {
                Capsule()
                    .fill(.secondary.opacity(0.5))
                    .frame(width: 36, height: 5)
                HStack(spacing: 12) {
                    Text("Contacts").font(.title2.bold())
                    Spacer()
                    Button("Refresh", systemImage: "arrow.clockwise") {
                        Task { await model.refresh() }
                    }
                    .buttonStyle(.bordered)
                    .disabled(model.isRefreshing)
                    Button("Add", systemImage: "plus") { adding = AddRequest() }
                        .buttonStyle(.borderedProminent)
                }
                .labelStyle(.iconOnly)
                .buttonBorderShape(.circle)
                .controlSize(.large)
            }
            .padding([.horizontal, .top])
            .padding(.bottom, 12)
            .contentShape(Rectangle())
            .onTapGesture { withAnimation(.snappy) { height = height == .collapsed ? .medium : .collapsed } }
            .gesture(DragGesture(minimumDistance: 10).onEnded { drag in
                withAnimation(.snappy) { height = drag.translation.height < 0 ? height.higher : height.lower }
            })
            if height != .collapsed {
                List {
                    if model.state.contacts.isEmpty {
                        ContentUnavailableView(
                            "No contacts",
                            systemImage: "person.2",
                            description: Text("Add a contact from the invite they sent you.")
                        )
                        .listRowBackground(Color.clear)
                    }
                    ForEach(model.state.contacts) { contact in
                        ContactRow(contact: contact, focus: focus)
                            .listRowBackground(Color.clear)
                    }
                    .onDelete { offsets in
                        model.removeContacts(at: offsets)
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .containerRelativeFrame(.vertical) { length, _ in length * height.fraction }
                .refreshable { await model.refresh() }
            }
        }
        .sheet(item: $adding) { AddContactView(invite: $0.invite) }
        .onChange(of: model.openedInvite, initial: true) { _, link in
            guard let link else { return }
            adding = AddRequest(invite: link)
            model.openedInvite = nil
        }
    }
}

struct ContactRow: View {
    private struct PickedImage: Identifiable {
        let id = UUID()
        let image: UIImage
    }

    @Environment(AppModel.self) private var model
    @State private var pickedPhoto: PhotosPickerItem?
    @State private var cropping: PickedImage?
    let contact: Contact
    let focus: (String) -> Void

    var body: some View {
        HStack {
            PhotosPicker(selection: $pickedPhoto, matching: .images) {
                ContactAvatar(contact: contact)
            }
            .buttonStyle(.borderless)
            .contextMenu {
                if contact.photoSetAt != nil {
                    Button("Remove photo", systemImage: "trash") { model.removePhoto(contact.id) }
                }
            }
            Button {
                if contact.lastPayload != nil { focus(contact.id) }
            } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(contact.name).font(.headline)
                        Text(ContactRow.status(of: contact, place: model.placeNames[contact.id]))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    if let distance = ContactRow.distance(to: contact, in: model.state) {
                        Text(distance).font(.subheadline).foregroundStyle(.secondary)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if model.state.sharingEnabled {
                Toggle("Share my location", isOn: Binding(
                    get: { contact.share },
                    set: { model.setShare(contact.id, $0) }
                ))
                .labelsHidden()
            }
        }
        .onChange(of: pickedPhoto) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self), let image = model.loadPhoto(data: data) {
                    cropping = PickedImage(image: image)
                }
                pickedPhoto = nil
            }
        }
        .fullScreenCover(item: $cropping) { picked in
            PhotoCropView(image: picked.image) { square in
                model.setPhoto(contact.id, square: square)
            }
        }
    }

    /// Where and when a contact was last seen, such as "Kumpula, seen 2 minutes ago".
    static func status(of contact: Contact, place: String?) -> String {
        if let payload = contact.lastPayload {
            let fixed = Date(timeIntervalSince1970: TimeInterval(payload.fixTime))
            let age = RelativeDateTimeFormatter().localizedString(for: fixed, relativeTo: Date())
            let stale = contact.sharing == .notSharing ? ", no longer sharing" : ""
            return place.map { "\($0), seen \(age)\(stale)" } ?? "Seen \(age)\(stale)"
        }
        switch contact.sharing {
        case .notSharing: return "Not sharing with you"
        case .sharing, .unknown: return "No location yet"
        }
    }

    /// How far this phone's last fix is from a contact's, such as "850 m", "1.4 km" or "18 km", while
    /// sharing keeps that fix current.
    static func distance(to contact: Contact, in state: PersistedState) -> String? {
        guard state.sharingEnabled, let own = state.lastFix, let payload = contact.lastPayload else { return nil }
        let metres = CLLocation(latitude: own.latitude, longitude: own.longitude)
            .distance(from: CLLocation(latitude: payload.latitude, longitude: payload.longitude))
        let rounded = Int((metres / 10).rounded()) * 10
        if rounded < 1_000 { return "\(rounded) m" }
        let km = metres / 1_000
        return km < 10 ? "\(km.formatted(.number.precision(.fractionLength(1)))) km" : "\(Int(km.rounded())) km"
    }
}

struct AddContactView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var invite: String
    @State private var name = ""
    @State private var error: String?
    @State private var scanning = false

    init(invite: String = "") {
        _invite = State(initialValue: invite)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Invite") {
                    TextField("Invite link", text: $invite, axis: .vertical)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .lineLimit(3...6)
                    Button("Paste") {
                        invite = UIPasteboard.general.string ?? ""
                    }
                    if QRScannerView.isSupported {
                        Button("Scan QR code") { scanning = true }
                    }
                }
                Section("Name") {
                    TextField("How you call them", text: $name)
                }
                if let error {
                    Section { Text(error).foregroundStyle(.red) }
                }
            }
            .navigationTitle("Add contact")
            .sheet(isPresented: $scanning) {
                NavigationStack {
                    QRScannerView { text in
                        invite = text
                        scanning = false
                    }
                    .ignoresSafeArea()
                    .safeAreaInset(edge: .bottom) {
                        Button("Cancel") { scanning = false }
                            .buttonStyle(.borderedProminent)
                            .controlSize(.large)
                            .padding()
                    }
                    .navigationTitle("Scan the invite")
                    .navigationBarTitleDisplayMode(.inline)
                }
            }
            .safeAreaInset(edge: .bottom) {
                HStack(spacing: 12) {
                    Button { dismiss() } label: {
                        Text("Cancel").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    Button { add() } label: {
                        Text("Add").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(invite.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                        name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
                .controlSize(.large)
                .padding()
            }
        }
    }

    private func add() {
        do {
            try model.addContact(invite: invite, name: name)
            dismiss()
        } catch {
            self.error = error.localizedDescription
        }
    }
}
