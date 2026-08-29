import AVFoundation
import Foundation
import Speech

/// Apple on-device recognizer. No network. EN + SR when the locale pack is on the phone.
enum OnDeviceSpeech {
    static func authorize() async -> Bool {
        let mic = await withCheckedContinuation { cont in
            AVAudioApplication.requestRecordPermission { cont.resume(returning: $0) }
        }
        guard mic else { return false }
        let status = await withCheckedContinuation { (cont: CheckedContinuation<SFSpeechRecognizerAuthorizationStatus, Never>) in
            SFSpeechRecognizer.requestAuthorization { cont.resume(returning: $0) }
        }
        return status == .authorized
    }

    static func transcribe(url: URL, lang: String) async -> String? {
        let locale = Locale(identifier: lang == "sr" ? "sr-Latn" : "en-US")
        guard let recognizer = SFSpeechRecognizer(locale: locale), recognizer.isAvailable else { return nil }
        let req = SFSpeechURLRecognitionRequest(url: url)
        req.requiresOnDeviceRecognition = true
        req.shouldReportPartialResults = false
        return await withCheckedContinuation { cont in
            let lock = NSLock()
            var finished = false
            func finish(_ value: String?) {
                lock.lock()
                defer { lock.unlock() }
                guard !finished else { return }
                finished = true
                cont.resume(returning: value)
            }
            let task = recognizer.recognitionTask(with: req) { result, error in
                if error != nil {
                    finish(nil)
                    return
                }
                if result?.isFinal == true {
                    finish(result?.bestTranscription.formattedString)
                }
            }
            Task {
                try? await Task.sleep(nanoseconds: 12_000_000_000)
                task.cancel()
                finish(nil)
            }
        }
    }
}

final class ClipRecorder: NSObject {
    private var recorder: AVAudioRecorder?
    private(set) var url: URL = FileManager.default.temporaryDirectory.appendingPathComponent("life-mic.wav")

    var isRecording: Bool { recorder?.isRecording == true }

    func start() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playAndRecord, mode: .measurement, options: [.defaultToSpeaker])
        try session.setActive(true)
        let settings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatLinearPCM),
            AVSampleRateKey: 16_000,
            AVNumberOfChannelsKey: 1,
            AVLinearPCMBitDepthKey: 16,
            AVLinearPCMIsFloatKey: false
        ]
        url = FileManager.default.temporaryDirectory.appendingPathComponent("life-mic-\(UUID().uuidString).wav")
        recorder = try AVAudioRecorder(url: url, settings: settings)
        recorder?.record()
    }

    func stop() {
        recorder?.stop()
        recorder = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}
