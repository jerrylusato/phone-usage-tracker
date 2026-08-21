package com.jeremiah.phoneusage.util

import android.content.Context
import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves package names to human-readable names. Only launchable apps are visible to this
 * process (see the `<queries>` block in the manifest), so anything else falls back to the
 * raw package name rather than requesting broader package visibility.
 */
object AppLabels {

    private val cache = ConcurrentHashMap<String, String>()

    fun label(context: Context, packageName: String): String {
        if (packageName.isEmpty()) return "Unknown"
        cache[packageName]?.let { return it }
        val pm = context.packageManager
        val resolved = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrElse { prettifyPackage(packageName) }
        cache[packageName] = resolved
        return resolved
    }

    fun isSystemApp(context: Context, packageName: String): Boolean = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        info.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or
            android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }.getOrDefault(false)

    /** "com.samsung.android.dialer" -> "Dialer" when the label cannot be resolved. */
    private fun prettifyPackage(packageName: String): String =
        packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }

    @Suppress("unused")
    fun clear() = cache.clear()

    fun launcherPackages(context: Context): Set<String> = runCatching {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }.getOrDefault(emptySet())
}
