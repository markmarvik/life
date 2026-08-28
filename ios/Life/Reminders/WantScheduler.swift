import Foundation
import UserNotifications

enum WantScheduler {
    static let eveHour = 20
    static let mornHour = 8
    static let prefix = "want:"

    static func requestPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    static func scheduleAll(_ data: AppData, now: Date = Date(), timeZone: TimeZone = .current) {
        let center = UNUserNotificationCenter.current()
        center.getPendingNotificationRequests { pending in
            let stale = pending.filter { $0.identifier.hasPrefix(prefix) }.map(\.identifier)
            center.removePendingNotificationRequests(withIdentifiers: stale)

            guard data.wantRemindersEnabled else { return }
            var cal = Calendar(identifier: .gregorian)
            cal.timeZone = timeZone

            for event in data.events where event.isOpenWant {
                guard let due = WantDomain.parseDue(event.dueDate),
                      let dueDate = cal.date(from: DateComponents(year: due.year, month: due.month, day: due.day))
                else { continue }

                if let eveDay = cal.date(byAdding: .day, value: -1, to: dueDate),
                   let eve = cal.date(bySettingHour: eveHour, minute: 0, second: 0, of: eveDay),
                   eve > now {
                    add(event: event, kind: "eve", date: eve, title: "Tomorrow", calendar: cal)
                }
                if let morn = cal.date(bySettingHour: mornHour, minute: 0, second: 0, of: dueDate),
                   morn > now {
                    add(event: event, kind: "morn", date: morn, title: "Today", calendar: cal)
                }
            }
        }
    }

    private static func add(event: LifeEvent, kind: String, date: Date, title: String, calendar: Calendar) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = event.body
        content.sound = .default
        let comps = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        let id = "\(prefix)\(event.id):\(kind)"
        let req = UNNotificationRequest(identifier: id, content: content, trigger: trigger)
        UNUserNotificationCenter.current().add(req)
    }
}
