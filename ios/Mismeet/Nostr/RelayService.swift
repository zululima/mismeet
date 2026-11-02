import Foundation
import NostrSDK

struct PublishResult: Sendable {
    var succeeded: [String]
    var failed: [String: String]

    var summary: String {
        failed.map { "\($0.key): \($0.value)" }.sorted().joined(separator: "; ")
    }
}

/// One relay as the setup checks found it: `problem` is nil when it answered, and `ownLocationAt` is
/// the `created_at` of this phone's location event it holds.
struct RelayCheck: Sendable, Identifiable {
    let url: String
    var problem: String?
    var ownLocationAt: UInt64?

    var id: String { url }
}

/// One short-lived client per operation: connect, do the work, shut down. Spec section 7.2 and 7.3.
actor RelayService {
    func publish(event: Event, relayUrls: [String]) async -> PublishResult {
        guard let client = await connectedClient(to: relayUrls) else {
            return PublishResult(succeeded: [], failed: ["relays": "none reachable"])
        }
        defer { Task { await client.shutdown() } }
        do {
            let output = try await client.sendEvent(
                event: event,
                target: SendEventTarget.broadcast(),
                ackPolicy: AckPolicy.all(),
                okTimeout: ProtocolConstants.okTimeoutSeconds,
                authenticationTimeout: nil
            )
            return PublishResult(
                succeeded: output.success.map(\.description),
                failed: Dictionary(output.failed.map { ($0.key.description, $0.value) }, uniquingKeysWith: { first, _ in first })
            )
        } catch {
            return PublishResult(succeeded: [], failed: ["send": "\(error)"])
        }
    }

    func fetch(targets: [RelayUrl: [Filter]]) async -> [Event] {
        guard let client = await connectedClient(to: targets.keys.map(\.description)) else { return [] }
        defer { Task { await client.shutdown() } }
        do {
            return try await client.fetchEvents(
                target: ReqTarget.manual(targets: targets),
                timeout: ProtocolConstants.okTimeoutSeconds,
                policy: .exitOnEose,
                maxEvents: nil
            )
        } catch {
            return []
        }
    }

    /// Connects to each relay on its own and asks it for `author`'s location event, all at once.
    func check(relayUrls: [String], author: PublicKey) async -> [RelayCheck] {
        await withTaskGroup(of: (Int, RelayCheck).self) { group in
            for (index, url) in relayUrls.enumerated() {
                group.addTask { (index, await Self.check(url, author: author)) }
            }
            var checks: [(Int, RelayCheck)] = []
            for await check in group { checks.append(check) }
            return checks.sorted { $0.0 < $1.0 }.map(\.1)
        }
    }

    private static func check(_ text: String, author: PublicKey) async -> RelayCheck {
        guard let url = try? RelayUrl.parse(url: text) else { return RelayCheck(url: text, problem: "Not a relay address") }
        let client = ClientBuilder().connectTimeout(timeout: ProtocolConstants.connectTimeoutSeconds).build()
        defer { Task { await client.shutdown() } }
        guard (try? await client.addRelay(url: url)) != nil else { return RelayCheck(url: text, problem: "Not a relay address") }
        let output = await client.tryConnect(timeout: ProtocolConstants.connectTimeoutSeconds)
        guard !output.success.isEmpty else {
            return RelayCheck(url: text, problem: output.failed.values.first ?? "No answer within \(Int(ProtocolConstants.connectTimeoutSeconds)) seconds")
        }
        let filter = Filter()
            .kinds(kinds: [Kind(kind: ProtocolConstants.locationKind)])
            .authors(authors: [author])
            .identifier(identifier: ProtocolConstants.identifier)
        do {
            let events = try await client.fetchEvents(
                target: ReqTarget.manual(targets: [url: [filter]]),
                timeout: ProtocolConstants.okTimeoutSeconds,
                policy: .exitOnEose,
                maxEvents: nil
            )
            return RelayCheck(url: text, ownLocationAt: events.map { $0.createdAt().asSecs() }.max())
        } catch {
            return RelayCheck(url: text, problem: "\(error)")
        }
    }

    private func connectedClient(to relayUrls: [String]) async -> Client? {
        let client = ClientBuilder().connectTimeout(timeout: ProtocolConstants.connectTimeoutSeconds).build()
        var added = 0
        for text in relayUrls {
            guard let url = try? RelayUrl.parse(url: text) else { continue }
            if (try? await client.addRelay(url: url)) != nil { added += 1 }
        }
        guard added > 0 else { return nil }
        _ = await client.tryConnect(timeout: ProtocolConstants.connectTimeoutSeconds)
        return client
    }
}
