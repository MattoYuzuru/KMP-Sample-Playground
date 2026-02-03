package app.tasks.focus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tasks.focus.model.FocusTask
import app.tasks.focus.model.TaskStatus
import app.tasks.focus.model.TaskStore
import app.tasks.focus.model.TimerPhase
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

private sealed class Screen {
    data object Home : Screen()
    data object CreateTask : Screen()
    data class TaskDetail(val taskId: String) : Screen()
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        val store = remember { TaskStore() }
        var tasks by remember { mutableStateOf(store.list()) }
        var screen by remember { mutableStateOf<Screen>(Screen.Home) }
        var toast by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(toast) {
            if (toast != null) {
                delay(2200)
                toast = null
            }
        }

        when (val current = screen) {
            Screen.Home -> HomeScreen(
                store = store,
                tasks = tasks,
                onCreateTask = { screen = Screen.CreateTask },
                onOpenTask = { id ->
                    if (store.isTimerRunningForOtherTask(id)) {
                        toast = "Таймер уже запущен для другой задачи"
                    } else {
                        screen = Screen.TaskDetail(id)
                    }
                },
            )
            Screen.CreateTask -> CreateTaskScreen(
                onBack = { screen = Screen.Home },
                onCreate = { title, description, tags, deadline ->
                    val task = store.addTask(
                        title = title,
                        description = description,
                        tagsCsv = tags,
                        deadlineIso = deadline,
                    )
                    tasks = store.list()
                    if (task != null) {
                        screen = Screen.TaskDetail(task.id)
                    }
                },
                onCreateAndBack = { title, description, tags, deadline ->
                    store.addTask(
                        title = title,
                        description = description,
                        tagsCsv = tags,
                        deadlineIso = deadline,
                    )
                    tasks = store.list()
                    screen = Screen.Home
                },
            )
            is Screen.TaskDetail -> TaskDetailScreen(
                store = store,
                taskId = current.taskId,
                onBack = {
                    tasks = store.list()
                    screen = Screen.Home
                },
                onDelete = {
                    store.deleteTask(current.taskId)
                    tasks = store.list()
                    screen = Screen.Home
                },
                onDone = { done ->
                    store.markDone(current.taskId, done)
                    tasks = store.list()
                },
                onSaveMeta = {
                    tasks = store.list()
                },
            )
        }

        if (toast != null) {
            ToastMessage(text = toast!!)
        }
    }
}

@Composable
private fun HomeScreen(
    store: TaskStore,
    tasks: List<FocusTask>,
    onCreateTask: () -> Unit,
    onOpenTask: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = tasks.filter {
        val text = "${it.title} ${it.description}".lowercase()
        text.contains(query.trim().lowercase())
    }
    val now = Clock.System.now().toEpochMilliseconds()
    val newTasks = filtered.filter { it.status != TaskStatus.DELETED && !store.isOverdue(it) }
        .filter { now - it.createdAtEpochMillis <= 24 * 60 * 60 * 1000 }
        .sortedByDescending { it.createdAtEpochMillis }
    val olderTasks = filtered.filter { it.status != TaskStatus.DELETED && !store.isOverdue(it) }
        .filter { now - it.createdAtEpochMillis > 24 * 60 * 60 * 1000 }
        .sortedByDescending { it.createdAtEpochMillis }
    val overdueTasks = filtered.filter { store.isOverdue(it) && it.status != TaskStatus.DELETED }
        .sortedByDescending { it.deadlineEpochMillis ?: 0 }
    val deletedTasks = filtered.filter { it.status == TaskStatus.DELETED }
        .sortedByDescending { it.updatedAtEpochMillis }

    Column(
        modifier = Modifier
            .safeContentPadding()
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "FocusTasks",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Поиск задач") },
        )
        Button(onClick = onCreateTask) {
            Text("Новая задача")
        }
        TimerBanner(store = store, onOpenTask = onOpenTask)
        TaskSection(
            title = "Новые задачи",
            tasks = newTasks,
            onOpenTask = onOpenTask,
            isOverdue = { store.isOverdue(it) },
        )
        TaskSection(
            title = "Более старые",
            tasks = olderTasks,
            onOpenTask = onOpenTask,
            isOverdue = { store.isOverdue(it) },
        )
        TaskSection(
            title = "Просроченные",
            tasks = overdueTasks,
            onOpenTask = onOpenTask,
            isOverdue = { store.isOverdue(it) },
        )
        TaskSection(
            title = "Удаленные",
            tasks = deletedTasks,
            onOpenTask = onOpenTask,
            isOverdue = { store.isOverdue(it) },
        )
    }
}

@Composable
private fun TaskSection(
    title: String,
    tasks: List<FocusTask>,
    onOpenTask: (String) -> Unit,
    isOverdue: (FocusTask) -> Boolean,
) {
    if (tasks.isEmpty()) return
    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tasks, key = { it.id }) { task ->
            TaskRow(
                task = task,
                isOverdue = isOverdue(task),
                onOpenTask = { onOpenTask(task.id) },
            )
        }
    }
}

@Composable
private fun TaskRow(
    task: FocusTask,
    isOverdue: Boolean,
    onOpenTask: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenTask() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                val statusLabel = when {
                    task.status == TaskStatus.DELETED -> "Удалено"
                    task.status == TaskStatus.DONE -> "Готово"
                    isOverdue -> "Просрочено"
                    else -> "Активно"
                }
                Text(text = statusLabel, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onOpenTask) { Text("За работу") }
        }
    }
}

@Composable
private fun CreateTaskScreen(
    onBack: () -> Unit,
    onCreate: (String, String, String, String?) -> Unit,
    onCreateAndBack: (String, String, String, String?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .safeContentPadding()
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Назад") }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Новая задача", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Название") })
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Описание") },
        )
        OutlinedTextField(
            value = tags,
            onValueChange = { tags = it },
            label = { Text("Теги (до 3, через запятую)") },
        )
        OutlinedTextField(
            value = deadline,
            onValueChange = { deadline = it },
            label = { Text("Дедлайн ISO, например 2026-02-02T18:30") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onCreate(title, description, tags, deadline.ifBlank { null }) }) {
                Text("Создать и открыть")
            }
            Button(onClick = { onCreateAndBack(title, description, tags, deadline.ifBlank { null }) }) {
                Text("Создать и назад")
            }
        }
    }
}

@Composable
private fun TaskDetailScreen(
    store: TaskStore,
    taskId: String,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onDone: (Boolean) -> Unit,
    onSaveMeta: () -> Unit,
) {
    val task = store.list().firstOrNull { it.id == taskId }
    if (task == null) {
        Column(
            modifier = Modifier
                .safeContentPadding()
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text("Задача не найдена")
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onBack) { Text("Назад") }
        }
        return
    }
    var title by remember(taskId) { mutableStateOf(task.title) }
    var description by remember(taskId) { mutableStateOf(task.description) }
    var tags by remember(taskId) { mutableStateOf(task.tags.joinToString(", ")) }
    var deadline by remember(taskId) { mutableStateOf(task.deadlineEpochMillis?.let { epochToIso(it) } ?: "") }
    var timerSnapshot by remember { mutableStateOf(store.timerSnapshot(taskId)) }

    LaunchedEffect(taskId) {
        while (true) {
            timerSnapshot = store.timerSnapshot(taskId)
            delay(1000)
        }
    }

    val totalSeconds = store.totalFocusedSeconds(taskId)
    val sessionCount = store.sessionCount(taskId)

    Column(
        modifier = Modifier
            .safeContentPadding()
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Назад") }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Профиль задачи", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Название") })
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Описание") },
        )
        OutlinedTextField(
            value = tags,
            onValueChange = { tags = it },
            label = { Text("Теги (до 3, через запятую)") },
        )
        OutlinedTextField(
            value = deadline,
            onValueChange = { deadline = it },
            label = { Text("Дедлайн ISO, например 2026-02-02T18:30") },
        )
        Button(onClick = {
            store.updateTaskMeta(taskId, title, description, tags, deadline.ifBlank { null })
            onSaveMeta()
        }) {
            Text("Сохранить")
        }
        Divider()
        Text(text = "Статистика", style = MaterialTheme.typography.titleMedium)
        Text(text = "Сессии: $sessionCount")
        Text(text = "Время фокуса: ${formatDuration(totalSeconds)}")
        Divider()
        Text(text = "Помодоро", style = MaterialTheme.typography.titleMedium)
        val phaseLabel = if (timerSnapshot?.phase == TimerPhase.BREAK) "Перерыв" else "Работа"
        val remaining = timerSnapshot?.remainingSeconds ?: (task.pomodoro.workMinutes * 60)
        Text(text = phaseLabel)
        Text(text = formatClock(remaining), style = MaterialTheme.typography.displaySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { store.startTimer(taskId) }) { Text("Старт") }
            Button(onClick = { store.pauseTimer() }) { Text("Пауза") }
            Button(onClick = { store.finishSession() }) { Text("Закончить сессию") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onDone(true) }) { Text("Готово") }
            Button(onClick = onDelete) { Text("Удалить") }
        }
    }
}

@Composable
private fun TimerBanner(
    store: TaskStore,
    onOpenTask: (String) -> Unit,
) {
    val activeId = store.activeTimerTaskId() ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Таймер запущен")
            Button(onClick = { onOpenTask(activeId) }) { Text("Открыть") }
        }
    }
}

@Composable
private fun ToastMessage(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun formatClock(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}ч ${minutes}м" else "${minutes}м"
}

private fun epochToIso(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis)
    return instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).toString()
}
