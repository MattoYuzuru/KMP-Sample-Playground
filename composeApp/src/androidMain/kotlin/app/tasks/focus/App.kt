package app.tasks.focus

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
import androidx.compose.material3.Checkbox
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
import app.tasks.focus.model.TaskStore
import kotlinx.coroutines.delay

@Composable
@Preview
fun App() {
    MaterialTheme {
        val store = remember { TaskStore() }
        var tasks by remember { mutableStateOf(store.list()) }
        var activeTaskId by remember { mutableStateOf<String?>(null) }

        val activeTask = tasks.firstOrNull { it.id == activeTaskId }
        if (activeTask != null) {
            PomodoroTimerScreen(
                task = activeTask,
                onBack = { activeTaskId = null },
            )
        } else {
            TaskListScreen(
                tasks = tasks,
                onAddTask = { title ->
                    store.addTask(title = title)
                    tasks = store.list()
                },
                onToggleDone = { id, isDone ->
                    store.toggleDone(id = id, isDone = isDone)
                    tasks = store.list()
                },
                onDeleteTask = { id ->
                    store.deleteTask(id)
                    tasks = store.list()
                },
                onOpenTimer = { id -> activeTaskId = id },
            )
        }
    }
}

@Composable
private fun TaskListScreen(
    tasks: List<FocusTask>,
    onAddTask: (String) -> Unit,
    onToggleDone: (String, Boolean) -> Unit,
    onDeleteTask: (String) -> Unit,
    onOpenTimer: (String) -> Unit,
) {
    var titleInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .safeContentPadding()
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "FocusTasks",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = titleInput,
                onValueChange = { titleInput = it },
                modifier = Modifier.weight(1f),
                label = { Text("New task") },
            )
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = {
                val trimmed = titleInput.trim()
                if (trimmed.isNotEmpty()) {
                    onAddTask(trimmed)
                    titleInput = ""
                }
            }) {
                Text("Add")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (tasks.isEmpty()) {
            Text(
                text = "No tasks yet. Add your first focus task.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        onToggleDone = { onToggleDone(task.id, it) },
                        onDelete = { onDeleteTask(task.id) },
                        onOpenTimer = { onOpenTimer(task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: FocusTask,
    onToggleDone: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onOpenTimer: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = task.isDone,
                onCheckedChange = { onToggleDone(it) },
            )
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (task.isDone) "Done" else "Todo",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = onOpenTimer) {
                Text("Timer")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onDelete) {
                Text("Delete")
            }
        }
    }
}

private enum class TimerPhase {
    Work,
    Break,
}

@Composable
private fun PomodoroTimerScreen(
    task: FocusTask,
    onBack: () -> Unit,
) {
    val workSeconds = task.pomodoro.workMinutes * 60
    val breakSeconds = task.pomodoro.breakMinutes * 60
    var phase by remember { mutableStateOf(TimerPhase.Work) }
    var isRunning by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableStateOf(workSeconds) }

    LaunchedEffect(task.id) {
        phase = TimerPhase.Work
        isRunning = false
        remainingSeconds = workSeconds
    }

    LaunchedEffect(isRunning, phase) {
        if (!isRunning) return@LaunchedEffect
        while (isRunning) {
            delay(1000)
            if (!isRunning) break
            remainingSeconds -= 1
            if (remainingSeconds <= 0) {
                phase = if (phase == TimerPhase.Work) TimerPhase.Break else TimerPhase.Work
                remainingSeconds = if (phase == TimerPhase.Work) workSeconds else breakSeconds
            }
        }
    }

    Column(
        modifier = Modifier
            .safeContentPadding()
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onBack) {
                Text("Back")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (phase == TimerPhase.Work) "Work" else "Break",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Text(
            text = formatTime(remainingSeconds),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { isRunning = true }) {
                Text("Start")
            }
            Button(onClick = { isRunning = false }) {
                Text("Pause")
            }
            Button(onClick = {
                isRunning = false
                phase = TimerPhase.Work
                remainingSeconds = workSeconds
            }) {
                Text("Stop")
            }
        }
        Text(
            text = "Work ${task.pomodoro.workMinutes}m / Break ${task.pomodoro.breakMinutes}m",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
