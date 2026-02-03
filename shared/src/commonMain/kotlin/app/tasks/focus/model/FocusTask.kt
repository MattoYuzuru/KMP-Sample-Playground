package app.tasks.focus.model

data class FocusTask(
    val id: String,
    val title: String,
    val description: String,
    val tags: List<String>,
    val deadlineEpochMillis: Long?,
    val status: TaskStatus,
    val pomodoro: PomodoroSettings,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

enum class TaskStatus {
    ACTIVE,
    DONE,
    DELETED,
}
