import SwiftUI
import Shared

final class TaskStoreModel: ObservableObject {
    let store = TaskStore()
    @Published var tasks: [FocusTask] = []

    init() {
        refresh()
    }

    func refresh() {
        tasks = toFocusTasks(store.list())
    }

    func createTask(title: String, description: String, tags: String, deadline: String?) -> FocusTask? {
        let task = store.addTask(
            title: title,
            description: description,
            tagsCsv: tags,
            deadlineIso: deadline
        )
        refresh()
        return task
    }

    func updateTask(id: String, title: String, description: String, tags: String, deadline: String?) {
        _ = store.updateTaskMeta(
            id: id,
            title: title,
            description: description,
            tagsCsv: tags,
            deadlineIso: deadline
        )
        refresh()
    }

    func markDone(id: String, done: Bool) {
        _ = store.markDone(id: id, done: done)
        refresh()
    }

    func deleteTask(id: String) {
        _ = store.deleteTask(id: id)
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
    @State private var searchText = ""
    @State private var path: [String] = []
    @State private var showCreate = false
    @State private var bannerMessage: String?

    var body: some View {
        NavigationStack(path: $path) {
            HomeView(
                tasks: filteredTasks,
                store: model.store,
                searchText: $searchText,
                onCreate: { showCreate = true },
                onOpen: { task in
                    if model.store.isTimerRunningForOtherTask(taskId: task.id) {
                        bannerMessage = "Таймер уже запущен для другой задачи"
                    } else {
                        path.append(task.id)
                    }
                }
            )
            .navigationDestination(for: String.self) { taskId in
                TaskDetailView(taskId: taskId, model: model)
            }
            .sheet(isPresented: $showCreate) {
                CreateTaskView(model: model, onClose: { showCreate = false }, onOpenTask: { task in
                    showCreate = false
                    path.append(task.id)
                })
            }
            .overlay(alignment: .top) {
                if let bannerMessage {
                    BannerMessage(text: bannerMessage)
                        .padding()
                }
            }
            .onChange(of: bannerMessage) { newValue in
                guard newValue != nil else { return }
                DispatchQueue.main.asyncAfter(deadline: .now() + 2.2) {
                    bannerMessage = nil
                }
            }
        }
    }

    private var filteredTasks: [FocusTask] {
        let trimmed = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return model.tasks }
        return model.tasks.filter { task in
            let text = (task.title + " " + task.description).lowercased()
            return text.contains(trimmed.lowercased())
        }
    }
}

struct HomeView: View {
    let tasks: [FocusTask]
    let store: TaskStore
    @Binding var searchText: String
    let onCreate: () -> Void
    let onOpen: (FocusTask) -> Void

    var body: some View {
        let now = Date().timeIntervalSince1970 * 1000
        let newTasks = tasks.filter { $0.status != TaskStatus.deleted && !store.isOverdue(task: $0) }
            .filter { now - Double($0.createdAtEpochMillis) <= 24 * 60 * 60 * 1000 }
            .sorted { $0.createdAtEpochMillis > $1.createdAtEpochMillis }
        let olderTasks = tasks.filter { $0.status != TaskStatus.deleted && !store.isOverdue(task: $0) }
            .filter { now - Double($0.createdAtEpochMillis) > 24 * 60 * 60 * 1000 }
            .sorted { $0.createdAtEpochMillis > $1.createdAtEpochMillis }
        let overdueTasks = tasks.filter { store.isOverdue(task: $0) && $0.status != TaskStatus.deleted }
            .sorted { $0.updatedAtEpochMillis > $1.updatedAtEpochMillis }
        let deletedTasks = tasks.filter { $0.status == TaskStatus.deleted }
            .sorted { $0.updatedAtEpochMillis > $1.updatedAtEpochMillis }

        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("FocusTasks")
                    .font(.largeTitle).bold()
                TextField("Поиск задач", text: $searchText)
                    .textFieldStyle(.roundedBorder)
                Button("Новая задача") {
                    onCreate()
                }
                TimerBanner(store: store, onOpen: onOpen)
                TaskSection(title: "Новые задачи", tasks: newTasks, store: store, onOpen: onOpen)
                TaskSection(title: "Более старые", tasks: olderTasks, store: store, onOpen: onOpen)
                TaskSection(title: "Просроченные", tasks: overdueTasks, store: store, onOpen: onOpen)
                TaskSection(title: "Удаленные", tasks: deletedTasks, store: store, onOpen: onOpen)
            }
            .padding()
        }
    }
}

struct TaskSection: View {
    let title: String
    let tasks: [FocusTask]
    let store: TaskStore
    let onOpen: (FocusTask) -> Void

    var body: some View {
        if tasks.isEmpty { return AnyView(EmptyView()) }
        return AnyView(
            VStack(alignment: .leading, spacing: 8) {
                Text(title).font(.headline)
                ForEach(tasks, id: \.id) { task in
                    TaskRow(task: task, isOverdue: store.isOverdue(task: task))
                        .onTapGesture { onOpen(task) }
                }
            }
        )
    }
}

struct TaskRow: View {
    let task: FocusTask
    let isOverdue: Bool

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 6) {
                Text(task.title).font(.headline)
                Text(statusLabel()).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            if task.status == TaskStatus.active {
                Text("За работу")
                    .font(.caption)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color(.systemBlue).opacity(0.1))
                    .clipShape(Capsule())
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func statusLabel() -> String {
        if task.status == TaskStatus.deleted { return "Удалено" }
        if task.status == TaskStatus.done { return "Готово" }
        if isOverdue { return "Просрочено" }
        return "Активно"
    }
}

struct CreateTaskView: View {
    @ObservedObject var model: TaskStoreModel
    let onClose: () -> Void
    let onOpenTask: (FocusTask) -> Void

    @State private var title = ""
    @State private var description = ""
    @State private var tags = ""
    @State private var hasDeadline = false
    @State private var deadlineDate = Date()

    var body: some View {
        NavigationStack {
            Form {
                Section(header: Text("Метаданные")) {
                    TextField("Название", text: $title)
                    TextField("Описание", text: $description)
                    TextField("Теги (до 3, через запятую)", text: $tags)
                    Toggle("Есть дедлайн", isOn: $hasDeadline)
                    if hasDeadline {
                        DatePicker(
                            "Дедлайн",
                            selection: $deadlineDate,
                            in: Date()...,
                            displayedComponents: [.date, .hourAndMinute]
                        )
                    }
                }
                Section {
                    Button("Создать и открыть") {
                        if let task = model.createTask(
                            title: title,
                            description: description,
                            tags: tags,
                            deadline: hasDeadline ? dateToLocalIso(deadlineDate) : nil
                        ) {
                            onOpenTask(task)
                        }
                    }
                    Button("Создать и назад") {
                        _ = model.createTask(
                            title: title,
                            description: description,
                            tags: tags,
                            deadline: hasDeadline ? dateToLocalIso(deadlineDate) : nil
                        )
                        onClose()
                    }
                }
            }
            .navigationTitle("Новая задача")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Закрыть") { onClose() }
                }
            }
        }
    }
}

struct TaskDetailView: View {
    let taskId: String
    @ObservedObject var model: TaskStoreModel

    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var description = ""
    @State private var tags = ""
    @State private var hasDeadline = false
    @State private var deadlineDate = Date()
    @State private var timerSnapshot: TimerSnapshot?

    var body: some View {
        let task = model.tasks.first { $0.id == taskId }
        if let task {
            let isActive = task.status == TaskStatus.active
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Профиль задачи").font(.title2).bold()
                    if isActive {
                        TextField("Название", text: $title)
                            .textFieldStyle(.roundedBorder)
                        TextField("Описание", text: $description)
                            .textFieldStyle(.roundedBorder)
                        TextField("Теги (до 3, через запятую)", text: $tags)
                            .textFieldStyle(.roundedBorder)
                        Toggle("Есть дедлайн", isOn: $hasDeadline)
                        if hasDeadline {
                            DatePicker(
                                "Дедлайн",
                                selection: $deadlineDate,
                                in: Date()...,
                                displayedComponents: [.date, .hourAndMinute]
                            )
                        }
                        Button("Сохранить") {
                            model.updateTask(
                                id: taskId,
                                title: title,
                                description: description,
                                tags: tags,
                                deadline: hasDeadline ? dateToLocalIso(deadlineDate) : nil
                            )
                        }
                    } else {
                        Text("Название: \(task.title)")
                        Text("Описание: \(task.description.isEmpty ? "—" : task.description)")
                        Text("Теги: \(task.tags.isEmpty ? "—" : task.tags.joined(separator: ", "))")
                        Text("Дедлайн: \(model.store.deadlineIsoForTask(taskId: taskId) ?? "—")")
                    }
                    Divider()
                    Text("Статистика").font(.headline)
                    Text("Сессии: \(model.store.sessionCount(taskId: taskId))")
                    Text("Время фокуса: \(formatDuration(model.store.totalFocusedSeconds(taskId: taskId)))")
                    if isActive {
                        Divider()
                        Text("Помодоро").font(.headline)
                        Text(timerSnapshot?.phase == TimerPhase.break_ ? "Перерыв" : "Работа")
                            .foregroundStyle(.secondary)
                        Text(formatClock(timerSnapshot?.remainingSeconds ?? Int(task.pomodoro.workMinutes) * 60))
                            .font(.system(size: 54, weight: .bold, design: .rounded))
                        let running = timerSnapshot?.isRunning == true
                        let hasStarted = timerSnapshot != nil
                        let startLabel = !hasStarted ? "Старт" : (running ? "Пауза" : "Продолжить")
                        HStack(spacing: 12) {
                            Button(startLabel) {
                                if running {
                                    _ = model.store.pauseTimer()
                                } else {
                                    _ = model.store.startTimer(taskId: taskId)
                                }
                            }
                            Button("Закончить сессию") {
                                _ = model.store.finishSession()
                                dismiss()
                            }
                        }
                        HStack(spacing: 12) {
                            Button("Готово") {
                                model.markDone(id: taskId, done: true)
                                dismiss()
                            }
                            Button("Удалить") {
                                model.deleteTask(id: taskId)
                                dismiss()
                            }
                        }
                    }
                }
                .padding()
            }
            .onAppear {
                title = task.title
                description = task.description
                tags = task.tags.joined(separator: ", ")
                if let millis = task.deadlineEpochMillis, millis > 0 {
                    deadlineDate = dateFromEpochMillis(millis)
                    hasDeadline = true
                } else {
                    hasDeadline = false
                }
                updateTimer()
            }
            .onReceive(timerPublisher) { _ in
                updateTimer()
            }
        } else {
            Text("Задача не найдена")
        }
    }

    private var timerPublisher: Timer.TimerPublisher {
        Timer.publish(every: 1, on: .main, in: .common).autoconnect()
    }

    private func updateTimer() {
        timerSnapshot = model.store.timerSnapshot(taskId: taskId)
    }
}

struct TimerBanner: View {
    let store: TaskStore
    let onOpen: (FocusTask) -> Void

    var body: some View {
        if let taskId = store.activeTimerTaskId() {
            let tasks = toFocusTasks(store.list())
            if let task = tasks.first(where: { $0.id == taskId }) {
                HStack {
                    Text("Таймер запущен")
                    Spacer()
                    Button("Открыть") { onOpen(task) }
                }
                .padding()
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
    }
}

struct BannerMessage: View {
    let text: String

    var body: some View {
        Text(text)
            .padding(12)
            .frame(maxWidth: .infinity)
            .background(Color(.systemGray6))
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

private func formatClock(_ totalSeconds: Int) -> String {
    let minutes = totalSeconds / 60
    let seconds = totalSeconds % 60
    return String(format: "%02d:%02d", minutes, seconds)
}

private func formatDuration(_ totalSeconds: Int) -> String {
    let hours = totalSeconds / 3600
    let minutes = (totalSeconds % 3600) / 60
    if hours > 0 { return "\(hours)ч \(minutes)м" }
    return "\(minutes)м"
}

private func dateToLocalIso(_ date: Date) -> String {
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.dateFormat = "yyyy-MM-dd'T'HH:mm"
    return formatter.string(from: date)
}

private func dateFromEpochMillis(_ millis: Int64) -> Date {
    Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
}
