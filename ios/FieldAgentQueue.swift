import Foundation

/// Same on-disk, bounded, remove-by-id outbox as the Android side, and for the
/// same reason: a queue that dies with the process is a queue that lies.
final class FieldAgentQueue {
  struct Entry {
    let clientId: String
    let payload: String
  }

  private let url: URL
  private let maxSize: Int
  private let lock = NSLock()
  private var entries: [Entry] = []
  private var loaded = false

  init(url: URL, maxSize: Int) {
    self.url = url
    self.maxSize = maxSize
  }

  static func defaultURL() -> URL {
    let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
      ?? URL(fileURLWithPath: NSTemporaryDirectory())
    let directory = base.appendingPathComponent("field-agent", isDirectory: true)
    try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    return directory.appendingPathComponent("outbox.tsv")
  }

  @discardableResult
  func add(_ entry: Entry) -> Int {
    lock.lock()
    defer { lock.unlock() }
    load()
    entries.append(entry)
    var dropped = 0
    while entries.count > maxSize {
      entries.removeFirst()
      dropped += 1
    }
    persist()
    return dropped
  }

  func peek(_ limit: Int) -> [Entry] {
    lock.lock()
    defer { lock.unlock() }
    load()
    guard limit > 0 else { return [] }
    return Array(entries.prefix(limit))
  }

  @discardableResult
  func remove(ids: [String]) -> Int {
    guard !ids.isEmpty else { return 0 }
    lock.lock()
    defer { lock.unlock() }
    load()
    let wanted = Set(ids)
    let before = entries.count
    entries.removeAll { wanted.contains($0.clientId) }
    let removed = before - entries.count
    if removed > 0 { persist() }
    return removed
  }

  func count() -> Int {
    lock.lock()
    defer { lock.unlock() }
    load()
    return entries.count
  }

  private func load() {
    guard !loaded else { return }
    loaded = true
    guard let text = try? String(contentsOf: url, encoding: .utf8) else { return }
    // Only a line terminated by a newline was fully written.
    for line in text.components(separatedBy: "\n").dropLast() {
      guard let separator = line.firstIndex(of: "\t") else { continue }
      let clientId = String(line[line.startIndex..<separator])
      let payload = String(line[line.index(after: separator)...])
      if !clientId.isEmpty && !payload.isEmpty {
        entries.append(Entry(clientId: clientId, payload: payload))
      }
    }
    while entries.count > maxSize { entries.removeFirst() }
  }

  private func persist() {
    let text = entries.map { "\($0.clientId)\t\($0.payload)\n" }.joined()
    // Atomic write: a crash mid-save leaves the previous queue, not half of it.
    try? text.write(to: url, atomically: true, encoding: .utf8)
  }
}
