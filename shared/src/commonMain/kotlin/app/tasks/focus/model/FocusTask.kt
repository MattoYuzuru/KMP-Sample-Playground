package app.tasks.focus.model

data class FocusTask(
    val id: String,
    val title: String,
    val notes: String,
    val isDone: Boolean,
    val pomodoro: PomodoroSettings,
)
