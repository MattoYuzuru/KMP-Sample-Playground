package app.tasks.focus

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform