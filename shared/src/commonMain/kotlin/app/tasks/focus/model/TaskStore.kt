package app.tasks.focus.model

import kotlin.random.Random

class TaskStore {
    private val tasks = mutableListOf<FocusTask>()

    fun list(): List<FocusTask> = tasks.toList()

    fun addTask(
        title: String,
        notes: String = "",
        pomodoro: PomodoroSettings = PomodoroSettings(),
    ): FocusTask? {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) return null
        val task = FocusTask(
            id = generateId(),
            title = trimmedTitle,
            notes = notes.trim(),
            isDone = false,
            pomodoro = pomodoro,
        )
        tasks.add(0, task)
        return task
    }

    fun toggleDone(id: String, isDone: Boolean): Boolean {
        val index = tasks.indexOfFirst { it.id == id }
        if (index == -1) return false
        val current = tasks[index]
        tasks[index] = current.copy(isDone = isDone)
        return true
    }

    fun deleteTask(id: String): Boolean {
        val index = tasks.indexOfFirst { it.id == id }
        if (index == -1) return false
        tasks.removeAt(index)
        return true
    }

    private fun generateId(): String {
        return "${Random.nextLong()}-${Random.nextInt()}"
    }
}
