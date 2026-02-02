import SwiftUI
import Shared

final class TaskStoreModel: ObservableObject {
    private let store = TaskStore()
    @Published var tasks: [FocusTask] = []

    init() {
        refresh()
    }

    func refresh() {
        tasks = toFocusTasks(store.list())
    }

    func addTask(title: String) {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        _ = store.addTask(
            title: trimmed,
            notes: "",
            pomodoro: PomodoroSettings(workMinutes: 25, breakMinutes: 5)
        )
        refresh()
    }

    func toggleDone(task: FocusTask) {
        _ = store.toggleDone(id: task.id, isDone: !task.isDone)
        refresh()
    }

    func deleteTasks(at offsets: IndexSet) {
        for index in offsets {
            let task = tasks[index]
            _ = store.deleteTask(id: task.id)
        }
        refresh()
    }
}

private func toFocusTasks(_ list: KotlinCollectionsList) -> [FocusTask] {
    var result: [FocusTask] = []
    let count = Int(list.size)
    result.reserveCapacity(count)
    for index in 0..<count {
        if let task = list.get(index: Int32(index)) as? FocusTask {
            result.append(task)
        }
    }
    return result
}

struct ContentView: View {
    @StateObject private var model = TaskStoreModel()
    @State private var newTitle = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                HStack {
                    TextField("New task", text: $newTitle)
                        .textFieldStyle(.roundedBorder)
                    Button("Add") {
                        model.addTask(title: newTitle)
                        newTitle = ""
                    }
                }
                .padding(.horizontal)

                if model.tasks.isEmpty {
                    Spacer()
                    Text("No tasks yet. Add your first focus task.")
                        .foregroundStyle(.secondary)
                    Spacer()
                } else {
                    List {
                        ForEach(model.tasks, id: \.id) { task in
                            HStack {
                                Button(action: { model.toggleDone(task: task) }) {
                                    Image(systemName: task.isDone ? "checkmark.circle.fill" : "circle")
                                }
                                .buttonStyle(.plain)
                                NavigationLink {
                                    PomodoroTimerView(task: task)
                                } label: {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(task.title)
                                            .font(.headline)
                                        Text(task.isDone ? "Done" : "Todo")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }
                        .onDelete(perform: model.deleteTasks)
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("FocusTasks")
        }
    }
}

private enum TimerPhase {
    case work
    case rest
}

struct PomodoroTimerView: View {
    let task: FocusTask
    @State private var phase: TimerPhase = .work
    @State private var isRunning = false
    @State private var remainingSeconds = 0
    @State private var timer: Timer?

    private var workSeconds: Int { Int(task.pomodoro.workMinutes) * 60 }
    private var breakSeconds: Int { Int(task.pomodoro.breakMinutes) * 60 }

    var body: some View {
        VStack(spacing: 16) {
            Text(task.title)
                .font(.title2)
                .fontWeight(.semibold)
            Text(phase == .work ? "Work" : "Break")
                .foregroundStyle(.secondary)
            Text(formatTime(remainingSeconds))
                .font(.system(size: 56, weight: .bold, design: .rounded))
            HStack(spacing: 12) {
                Button("Start") { startTimer() }
                Button("Pause") { pauseTimer() }
                Button("Stop") { resetTimer() }
            }
            Text("Work \(task.pomodoro.workMinutes)m / Break \(task.pomodoro.breakMinutes)m")
                .foregroundStyle(.secondary)
        }
        .padding()
        .onAppear { resetTimer() }
        .onDisappear { pauseTimer() }
    }

    private func startTimer() {
        guard timer == nil else { return }
        isRunning = true
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { _ in
            tick()
        }
    }

    private func pauseTimer() {
        isRunning = false
        timer?.invalidate()
        timer = nil
    }

    private func resetTimer() {
        pauseTimer()
        phase = .work
        remainingSeconds = workSeconds
    }

    private func tick() {
        guard isRunning else { return }
        if remainingSeconds > 0 {
            remainingSeconds -= 1
        }
        if remainingSeconds <= 0 {
            phase = phase == .work ? .rest : .work
            remainingSeconds = phase == .work ? workSeconds : breakSeconds
        }
    }
}

private func formatTime(_ totalSeconds: Int) -> String {
    let minutes = totalSeconds / 60
    let seconds = totalSeconds % 60
    return String(format: "%02d:%02d", minutes, seconds)
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
