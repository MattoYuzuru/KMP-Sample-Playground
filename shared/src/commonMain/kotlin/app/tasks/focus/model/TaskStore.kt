package app.tasks.focus.model

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.math.max
import kotlin.random.Random

data class PomodoroSession(
    val id: String,
    val taskId: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    val durationSeconds: Int,
)

data class TimerSnapshot(
    val taskId: String,
    val phase: TimerPhase,
    val isRunning: Boolean,
    val remainingSeconds: Int,
    val workSecondsAccrued: Int,
)

enum class TimerPhase {
    WORK,
    BREAK,
}

class TaskStore {
    private val tasks = mutableListOf<FocusTask>()
    private val sessions = mutableListOf<PomodoroSession>()
    private var activeTimer: ActiveTimerState? = null

    fun list(): List<FocusTask> = tasks.toList()

    fun listSessions(taskId: String): List<PomodoroSession> =
        sessions.filter { it.taskId == taskId }

    fun totalFocusedSeconds(taskId: String): Int =
        sessions.filter { it.taskId == taskId }.sumOf { it.durationSeconds }

    fun sessionCount(taskId: String): Int =
        sessions.count { it.taskId == taskId }

    fun addTask(
        title: String,
        description: String = "",
        tagsCsv: String = "",
        deadlineIso: String? = null,
        pomodoro: PomodoroSettings = PomodoroSettings(),
    ): FocusTask? {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) return null
        val now = nowMillis()
        val task = FocusTask(
            id = generateId(),
            title = trimmedTitle,
            description = description.trim(),
            tags = parseTags(tagsCsv),
            deadlineEpochMillis = parseDeadline(deadlineIso),
            status = TaskStatus.ACTIVE,
            pomodoro = pomodoro,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        tasks.add(0, task)
        return task
    }

    fun updateTaskMeta(
        id: String,
        title: String,
        description: String,
        tagsCsv: String,
        deadlineIso: String?,
    ): Boolean {
        val index = tasks.indexOfFirst { it.id == id }
        if (index == -1) return false
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) return false
        val current = tasks[index]
        tasks[index] = current.copy(
            title = trimmedTitle,
            description = description.trim(),
            tags = parseTags(tagsCsv),
            deadlineEpochMillis = parseDeadline(deadlineIso),
            updatedAtEpochMillis = nowMillis(),
        )
        return true
    }

    fun markDone(id: String, done: Boolean): Boolean {
        val index = tasks.indexOfFirst { it.id == id }
        if (index == -1) return false
        val current = tasks[index]
        val nextStatus = if (done) TaskStatus.DONE else TaskStatus.ACTIVE
        tasks[index] = current.copy(
            status = nextStatus,
            updatedAtEpochMillis = nowMillis(),
        )
        return true
    }

    fun deleteTask(id: String): Boolean {
        val index = tasks.indexOfFirst { it.id == id }
        if (index == -1) return false
        val current = tasks[index]
        tasks[index] = current.copy(
            status = TaskStatus.DELETED,
            updatedAtEpochMillis = nowMillis(),
        )
        if (activeTimer?.taskId == id) {
            activeTimer = null
        }
        return true
    }

    fun isOverdue(task: FocusTask): Boolean {
        if (task.status != TaskStatus.ACTIVE) return false
        val deadline = task.deadlineEpochMillis ?: return false
        return nowMillis() > deadline
    }

    fun canOpenTask(taskId: String): Boolean {
        val timer = activeTimer ?: return true
        if (!timer.isRunning) return true
        return timer.taskId == taskId
    }

    fun activeTimerTaskId(): String? = activeTimer?.taskId

    fun startTimer(taskId: String): Boolean {
        val now = nowMillis()
        val existing = activeTimer
        if (existing != null && existing.taskId != taskId && existing.isRunning) return false
        if (existing == null || existing.taskId != taskId) {
            val task = tasks.firstOrNull { it.id == taskId } ?: return false
            if (task.status == TaskStatus.DELETED) return false
            activeTimer = ActiveTimerState(
                taskId = taskId,
                phase = TimerPhase.WORK,
                isRunning = true,
                remainingSeconds = max(task.pomodoro.workMinutes, 1) * 60,
                workSecondsAccrued = 0,
                lastUpdatedEpochMillis = now,
                sessionStartedEpochMillis = now,
                workDurationSeconds = max(task.pomodoro.workMinutes, 1) * 60,
                breakDurationSeconds = max(task.pomodoro.breakMinutes, 1) * 60,
            )
            return true
        }
        activeTimer = existing.copy(isRunning = true, lastUpdatedEpochMillis = now)
        return true
    }

    fun pauseTimer(): Boolean {
        val timer = activeTimer ?: return false
        val now = nowMillis()
        val updated = applyElapsed(timer, now)
        activeTimer = updated.copy(isRunning = false, lastUpdatedEpochMillis = now)
        return true
    }

    fun finishSession(): PomodoroSession? {
        val timer = activeTimer ?: return null
        val now = nowMillis()
        val updated = applyElapsed(timer, now)
        val sessionSeconds = updated.workSecondsAccrued
        val session = if (sessionSeconds > 0) {
            PomodoroSession(
                id = generateId(),
                taskId = updated.taskId,
                startedAtEpochMillis = updated.sessionStartedEpochMillis,
                endedAtEpochMillis = now,
                durationSeconds = sessionSeconds,
            ).also { sessions.add(0, it) }
        } else {
            null
        }
        activeTimer = null
        return session
    }

    fun timerSnapshot(taskId: String): TimerSnapshot? {
        val timer = activeTimer ?: return null
        if (timer.taskId != taskId) return null
        val now = nowMillis()
        val updated = if (timer.isRunning) applyElapsed(timer, now) else timer
        activeTimer = updated.copy(lastUpdatedEpochMillis = now)
        return TimerSnapshot(
            taskId = updated.taskId,
            phase = updated.phase,
            isRunning = updated.isRunning,
            remainingSeconds = updated.remainingSeconds,
            workSecondsAccrued = updated.workSecondsAccrued,
        )
    }

    fun deadlineIsoForTask(taskId: String): String? {
        val task = tasks.firstOrNull { it.id == taskId } ?: return null
        val deadline = task.deadlineEpochMillis ?: return null
        val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(deadline)
        return instant.toLocalDateTime(TimeZone.currentSystemDefault()).toString()
    }

    fun isTimerRunningForOtherTask(taskId: String): Boolean {
        val timer = activeTimer ?: return false
        return timer.isRunning && timer.taskId != taskId
    }

    private fun applyElapsed(timer: ActiveTimerState, nowMillis: Long): ActiveTimerState {
        if (!timer.isRunning) return timer
        var remainingSeconds = timer.remainingSeconds
        var phase = timer.phase
        var workSecondsAccrued = timer.workSecondsAccrued
        var lastUpdatedMillis = timer.lastUpdatedEpochMillis
        var deltaSeconds = ((nowMillis - lastUpdatedMillis) / 1000).toInt()
        if (deltaSeconds <= 0) return timer.copy(lastUpdatedEpochMillis = nowMillis)

        while (deltaSeconds > 0) {
            if (remainingSeconds > deltaSeconds) {
                remainingSeconds -= deltaSeconds
                if (phase == TimerPhase.WORK) {
                    workSecondsAccrued += deltaSeconds
                }
                deltaSeconds = 0
            } else {
                if (phase == TimerPhase.WORK) {
                    workSecondsAccrued += remainingSeconds
                }
                deltaSeconds -= remainingSeconds
                phase = if (phase == TimerPhase.WORK) TimerPhase.BREAK else TimerPhase.WORK
                remainingSeconds = if (phase == TimerPhase.WORK) {
                    timer.workDurationSeconds
                } else {
                    timer.breakDurationSeconds
                }
            }
        }
        return timer.copy(
            remainingSeconds = remainingSeconds,
            phase = phase,
            workSecondsAccrued = workSecondsAccrued,
            lastUpdatedEpochMillis = nowMillis,
        )
    }

    private fun parseDeadline(iso: String?): Long? {
        val trimmed = iso?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val local = LocalDateTime.parse(trimmed)
            local.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        }.getOrNull()
    }

    private fun parseTags(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.take(20) }
            .distinct()
            .take(3)
    }

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    private fun generateId(): String = "${Random.nextLong()}-${Random.nextInt()}"
}

private data class ActiveTimerState(
    val taskId: String,
    val phase: TimerPhase,
    val isRunning: Boolean,
    val remainingSeconds: Int,
    val workSecondsAccrued: Int,
    val lastUpdatedEpochMillis: Long,
    val sessionStartedEpochMillis: Long,
    val workDurationSeconds: Int,
    val breakDurationSeconds: Int,
)
