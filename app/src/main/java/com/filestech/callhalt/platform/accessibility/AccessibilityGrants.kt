package com.filestech.callhalt.platform.accessibility

import android.content.pm.PackageManager
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.SensitiveAction
import com.filestech.callhalt.domain.model.SensitiveActionKind

/**
 * The phone's own accessibility features, whose services the user — or a relative, by phone — turns on for himself: the
 * screen readers, the accessibility menu, the switch access, Samsung's voice assistant and camera gestures. A STOP there
 * would call a daughter guiding her mother a scammer (review of 2026-11-05: "press Continue" is what the screen says a
 * scammer says), and TalkBack turned on by its volume-keys shortcut would read the STOP aloud to someone who cannot
 * work it. Exempt only when PREINSTALLED as well (`FLAG_SYSTEM`, which no app installed afterwards can have), or signed by
 * its publisher ([SUITE_CERTIFICATES]): any app may take one of these ids where the real one is not installed. Not by
 * `isAccessibilityTool`, which an app declares itself. Declared in the manifest's `<queries>` too, to be seen;
 * `RemoteControlAppsTest` keeps both lists equal.
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
 * The signing certificates (SHA-256) of the phone's own features that a phone may not preinstall: Google's suites,
 * installed from Play on a Samsung, which preinstalls only its own (publication audit of 2026-11-07, M4, read on the S9).
 * Google signs each with two keys: the one of its copies on Play (APKMirror, several versions: TalkBack 15.0 to 16.2 by
 * "Google Eyes-Free Project", Voice Access 6.1 to 17.0 by "accessibility-voice-access"), and the one of the copies a
 * phone preinstalls (read with apksigner on the Android 16 emulator's; APKMirror lists both as valid). Samsung's features
 * have none here: every Samsung preinstalls them.
 */
internal val SUITE_CERTIFICATES: Map<String, Set<String>> = mapOf(
    "com.google.android.marvin.talkback" to setOf(
        "7b5ad55180a48a1f30f35377c0f9e5f911bf942fb4cf83eba255a0ebf580beef", // Play
        "93c9fc6d5af13c4e802dc1caf68f2e00688475b0b28f29487852cc9d550b6f27", // preinstalled
    ),
    "com.google.android.apps.accessibility.voiceaccess" to setOf(
        "7a425040828738d0fc0b93c16f3e4e4ac2aa49026100b2d01aebbc0b557d356e", // Play
        "d553b41c17aad365902adf609043a0f5af31634925e23cc6e750acb7ac056ca7", // preinstalled
    ),
)

/**
 * Whether [packageName] is one of the phone's own features: of the list, and [preinstalled] or signed with one of its
 * publisher's certificates ([signedWith], given a certificate's SHA-256). Under one of these ids without either, it is
 * an app like any other.
 */
internal fun isPhoneFeature(
    packageName: String,
    preinstalled: (String) -> Boolean,
    signedWith: (String, ByteArray) -> Boolean,
): Boolean = packageName in SYSTEM_ACCESSIBILITY_SUITES && (
    preinstalled(packageName) ||
        SUITE_CERTIFICATES[packageName].orEmpty().any { signedWith(packageName, it.hexToByteArray()) }
    )

/** Whether [packageName] is signed with the certificate whose SHA-256 is [sha256]: the sensor's question, and its test's. */
internal fun PackageManager.isSignedWith(packageName: String, sha256: ByteArray): Boolean =
    hasSigningCertificate(packageName, sha256, PackageManager.CERT_INPUT_SHA256)

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
