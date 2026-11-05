package com.filestech.callhalt.platform.accessibility

import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.SensitiveAction
import com.filestech.callhalt.domain.model.SensitiveActionKind

/**
 * The phone's own accessibility features, whose services the user — or a relative, by phone — turns on for himself: the
 * screen readers, the accessibility menu, the switch access, Samsung's voice assistant and camera gestures. A STOP there
 * would call a daughter guiding her mother a scammer (review of 2026-11-05: "press Continue" is what the screen says a
 * scammer says), and TalkBack turned on by its volume-keys shortcut would read the STOP aloud to someone who cannot
 * work it. Exempt only when PREINSTALLED as well (`FLAG_SYSTEM`, which no app installed afterwards can have): any app may
 * take one of these ids where the real one is not installed. Not by `isAccessibilityTool`, which an app declares itself.
 * Declared in the manifest's `<queries>` too, to be seen; `RemoteControlAppsTest` keeps both lists equal.
 *
 * Read on the S9 (`com.samsung.accessibility`, `com.samsung.android.bixby.agent`, `com.sec.android.app.camera`: SYSTEM)
 * and the Android 15 and 16 emulators (the accessibility menu, TalkBack).
 */
internal val SYSTEM_ACCESSIBILITY_SUITES: Set<String> = setOf(
    "com.google.android.marvin.talkback",
    "com.google.android.apps.accessibility.voiceaccess",
    "com.android.systemui.accessibility.accessibilitymenu",
    "com.samsung.accessibility",
    "com.samsung.android.accessibility.talkback",
    "com.samsung.android.bixby.agent",
    "com.sec.android.app.camera",
)

/**
 * The accessibility services turned on, as Android keeps them: one setting, `pkg/class` entries separated by colons
 * (`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`). An empty or missing setting is none. A class may be written short,
 * `pkg/.Cls`, or long, `pkg/pkg.Cls`, and Android may rewrite one form as the other: each is read in the long form, so
 * that a service already on never reads as new (review of 2026-11-05, 1).
 */
internal fun enabledServices(setting: String?): Set<String> =
    setting.orEmpty().split(':').map(String::trim).filter(String::isNotEmpty).map(::longForm).toSet()

private fun longForm(entry: String): String {
    val packageName = entry.substringBefore('/', missingDelimiterValue = "")
    val className = entry.substringAfter('/', missingDelimiterValue = "")
    return if (packageName.isNotEmpty() && className.startsWith('.')) "$packageName/$packageName$className" else entry
}

/**
 * What a change of that setting is to the engine (docs/ARCHITECTURE.md, D-033): an app just given the accessibility
 * access, when a service appears in [now] that was not in [before], of another app than this one and not one of the
 * phone's own features ([isSystemSuite]). A service turned off, or one listed again, is nothing. The package goes to the
 * journal, which app it was; of the setting, nothing else.
 */
internal fun accessibilityGranted(
    before: Set<String>,
    now: Set<String>,
    ownPackage: String,
    at: Moment,
    isSystemSuite: (String) -> Boolean = { false },
): SensitiveAction? {
    val granted = (now - before).map { it.substringBefore('/') }
        .firstOrNull { it.isNotEmpty() && it != ownPackage && !isSystemSuite(it) }
    return granted?.let { SensitiveAction(at, SensitiveActionKind.ACCESSIBILITY_ENABLED, source = it) }
}
