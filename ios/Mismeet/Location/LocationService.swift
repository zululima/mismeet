import CoreLocation
import Foundation

/// One position report from Core Location, with the platform's unknown markers already removed.
struct LocationFix: Codable, Equatable, Sendable {
    var time: Date
    var latitude: Double
    var longitude: Double
    var horizontalAccuracy: Double
    var ellipsoidalAltitude: Double?
    var speed: Double?
    var course: Double?

    init?(_ location: CLLocation) {
        guard location.horizontalAccuracy >= 0 else { return nil }
        time = location.timestamp
        latitude = location.coordinate.latitude
        longitude = location.coordinate.longitude
        horizontalAccuracy = location.horizontalAccuracy
        ellipsoidalAltitude = location.verticalAccuracy >= 0 ? location.ellipsoidalAltitude : nil
        speed = location.speed >= 0 ? location.speed : nil
        course = location.course >= 0 ? location.course : nil
    }

    init(time: Date, latitude: Double, longitude: Double, horizontalAccuracy: Double,
         ellipsoidalAltitude: Double? = nil, speed: Double? = nil, course: Double? = nil) {
        self.time = time
        self.latitude = latitude
        self.longitude = longitude
        self.horizontalAccuracy = horizontalAccuracy
        self.ellipsoidalAltitude = ellipsoidalAltitude
        self.speed = speed
        self.course = course
    }

    func payload(battery: Double?) -> LocationPayload {
        LocationPayload(
            fixTime: UInt64(max(0, time.timeIntervalSince1970)),
            latitude: latitude,
            longitude: longitude,
            accuracy: Int(horizontalAccuracy.rounded()),
            altitude: ellipsoidalAltitude.map { Int($0.rounded()) },
            speed: speed,
            course: course.map { Int($0.rounded()) % 360 },
            battery: battery
        )
    }
}

@MainActor
final class LocationService: NSObject, CLLocationManagerDelegate {
    var onFix: ((LocationFix) -> Void)?
    var onAuthorizationChange: ((CLAuthorizationStatus) -> Void)?
    var onFailure: ((Error) -> Void)?
    var onRegionExit: (() -> Void)?
    var onRegionFailure: ((Error) -> Void)?

    private static let regionIdentifier = "last-fix"
    private let manager = CLLocationManager()
    private var monitoring = false

    var authorizationStatus: CLAuthorizationStatus {
        manager.authorizationStatus
    }

    var preciseLocation: Bool {
        manager.accuracyAuthorization == .fullAccuracy
    }

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        manager.pausesLocationUpdatesAutomatically = false
    }

    func requestAuthorization() {
        switch manager.authorizationStatus {
        case .notDetermined: manager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse: manager.requestAlwaysAuthorization()
        default: break
        }
    }

    func startMonitoringIfAuthorized() {
        let status = manager.authorizationStatus
        guard status == .authorizedAlways || status == .authorizedWhenInUse, !monitoring else { return }
        manager.allowsBackgroundLocationUpdates = status == .authorizedAlways
        manager.startMonitoringSignificantLocationChanges()
        monitoring = true
    }

    /// Spec section 8.1: leaving this region wakes the app, also when it is not running.
    func monitorRegion(around fix: LocationFix) {
        guard manager.authorizationStatus == .authorizedAlways,
              CLLocationManager.isMonitoringAvailable(for: CLCircularRegion.self) else { return }
        let region = CLCircularRegion(
            center: CLLocationCoordinate2D(latitude: fix.latitude, longitude: fix.longitude),
            radius: min(ProtocolConstants.regionRadiusMetres, manager.maximumRegionMonitoringDistance),
            identifier: Self.regionIdentifier
        )
        region.notifyOnEntry = false
        region.notifyOnExit = true
        manager.startMonitoring(for: region)
    }

    /// Also ends a registration left by an earlier process, which would otherwise keep relaunching the app.
    func stopMonitoring() {
        manager.stopMonitoringSignificantLocationChanges()
        for region in manager.monitoredRegions {
            manager.stopMonitoring(for: region)
        }
        manager.allowsBackgroundLocationUpdates = false
        monitoring = false
    }

    func requestFreshFix() {
        manager.requestLocation()
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let fix = locations.last.flatMap(LocationFix.init) else { return }
        Task { @MainActor in self.onFix?(fix) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in self.onFailure?(error) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        Task { @MainActor in self.onRegionExit?() }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, monitoringDidFailFor region: CLRegion?, withError error: Error) {
        Task { @MainActor in self.onRegionFailure?(error) }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in self.onAuthorizationChange?(status) }
    }
}
