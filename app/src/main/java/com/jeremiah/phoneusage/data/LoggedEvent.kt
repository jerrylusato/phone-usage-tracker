package com.jeremiah.phoneusage.data

/**
 * One raw usage event as persisted by this app.
 *
 * [type] uses the numeric `UsageEvents.Event` values. They are declared here as plain
 * constants so the log format stays stable and so the code does not reference platform
 * fields that were added in later API levels.
 */
data class LoggedEvent(
    val timestamp: Long,
    val packageName: String,
    val type: Int,
    val className: String? = null,
)

object EventTypes {
    const val ACTIVITY_RESUMED = 1
    const val ACTIVITY_PAUSED = 2
    const val USER_INTERACTION = 7
    const val SCREEN_INTERACTIVE = 15
    const val SCREEN_NON_INTERACTIVE = 16
    const val KEYGUARD_SHOWN = 17
    const val KEYGUARD_HIDDEN = 18
    const val FOREGROUND_SERVICE_START = 19
    const val FOREGROUND_SERVICE_STOP = 20
    const val ACTIVITY_STOPPED = 23
    const val DEVICE_SHUTDOWN = 26
    const val DEVICE_STARTUP = 27

    /** Everything worth keeping that the platform will hand a normal app. */
    val TRACKED = setOf(
        ACTIVITY_RESUMED,
        ACTIVITY_PAUSED,
        USER_INTERACTION,
        SCREEN_INTERACTIVE,
        SCREEN_NON_INTERACTIVE,
        KEYGUARD_SHOWN,
        KEYGUARD_HIDDEN,
        FOREGROUND_SERVICE_START,
        FOREGROUND_SERVICE_STOP,
        ACTIVITY_STOPPED,
        DEVICE_SHUTDOWN,
        DEVICE_STARTUP,
    )

    fun label(type: Int): String = when (type) {
        ACTIVITY_RESUMED -> "app opened"
        ACTIVITY_PAUSED -> "app paused"
        USER_INTERACTION -> "interaction"
        SCREEN_INTERACTIVE -> "screen on"
        SCREEN_NON_INTERACTIVE -> "screen off"
        KEYGUARD_SHOWN -> "locked"
        KEYGUARD_HIDDEN -> "unlocked"
        FOREGROUND_SERVICE_START -> "service start"
        FOREGROUND_SERVICE_STOP -> "service stop"
        ACTIVITY_STOPPED -> "app stopped"
        DEVICE_SHUTDOWN -> "shutdown"
        DEVICE_STARTUP -> "boot"
        else -> "type $type"
    }
}
