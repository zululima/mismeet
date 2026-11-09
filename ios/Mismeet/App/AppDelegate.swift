import BackgroundTasks
import UIKit

final class AppDelegate: NSObject, UIApplicationDelegate {
    // A launch for a significant location change, a region exit or a background refresh happens
    // in the background, before any view exists. Touching the model starts location monitoring so
    // the pending event is delivered; the refresh handler has to be registered before this returns.
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: AppModel.refreshTaskIdentifier, using: .main) { task in
            MainActor.assumeIsolated {
                guard let task = task as? BGAppRefreshTask else { return task.setTaskCompleted(success: false) }
                AppModel.shared.runRefresh(task)
            }
        }
        _ = AppModel.shared
        return true
    }
}
