#!/usr/bin/env python3
"""Assert the permissions — and the special accesses — of the MERGED release manifest against a reviewed list, exactly.

Why this exists
---------------
Call Halt Tech promises "no internet permission" (PROJET.md, § 1), and will ask for heavy accesses
(phone state, SMS, notifications, usage, overlay). A promise no build checks is an intention: this
script turns it into a build constraint. Precedent in the portfolio: Agenda Tech PUBLISHED an APK
carrying ACCESS_NETWORK_STATE, pulled transitively by androidx.work, unnoticed until a later audit.
Copied from Read Files Tech 3, with this app's package and list.

Two traps this script avoids
----------------------------
1. The source manifest is not evidence: libraries add permissions at merge time. Only the merged
   manifest — or the manifest of the APK itself — decides what the published APK asks for.
2. `<uses-permission>` can span several lines, a shape the merge produces. This parses the XML.

The list is EXACT, not only a ceiling
-------------------------------------
A permission outside the list fails (it must be a deliberate, reviewed act). A permission IN the list
but absent from the manifest fails too: the list, PRIVACY.md and the APK would otherwise describe three
different apps. Each component that brings a permission adds it here, in the same commit, with
PRIVACY.md (docs/ARCHITECTURE.md, D-007).

Special accesses are not permissions
------------------------------------
Notification access, accessibility, device administration, an input method: the user grants them in the
phone's settings, and the app asks for none of them with a `<uses-permission>`. It declares a COMPONENT the
system binds, guarded by an `android.permission.BIND_*` permission that only the system holds. Most of these
accesses are at least as wide as any permission, so the same rule holds: an exact list (ALLOWED_BINDINGS), each
one with its component and its line in PRIVACY.md, and a few that must never appear (FORBIDDEN_BINDINGS). The
few that grant nothing (a job service, if WorkManager ever comes) are listed all the same: reviewed, not unseen.

The permission checked is the one ANDROID applies, not only the attribute on the component: a component without
one takes the application's (`<application android:permission>`), an empty one cancels it, and an alias without
one takes its target's. A permission given by reference (`@string/…`) cannot be reviewed from the manifest: it
fails (independent review of 2026-11-02, which slipped an accessibility service past the first version).

Usage
-----
    python3 tools/check-manifest-permissions.py [manifest]

`manifest` defaults to the merged release manifest. It may also be the manifest of a built APK, as
printed by `apkanalyzer manifest print <apk>`.

Exit code 0 when the manifest matches the list exactly, 1 otherwise. Prints GitHub Actions `::error::`
annotations under CI, plain text otherwise.
"""

from __future__ import annotations

import os
import sys
import xml.etree.ElementTree as ET

# The Windows console is cp1252: a non-ASCII character in a message would otherwise crash the script,
# that is, report a FAILED check when the check succeeded.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ANDROID_NS = "http://schemas.android.com/apk/res/android"

DEFAULT_MANIFEST = os.path.join(
    "app", "build", "intermediates", "merged_manifest", "release",
    "processReleaseMainManifest", "AndroidManifest.xml",
)

# The applicationId of the release (app/build.gradle.kts, docs/ARCHITECTURE.md D-002).
PACKAGE = "com.filestech.callhalt"

# Must NEVER reach the APK, whatever the list says: the permissions that open a socket or read the
# network. Kept apart from the list so the failure message can say WHY. Same set as Read Files Tech.
FORBIDDEN = {
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.CHANGE_NETWORK_STATE",
    "android.permission.CHANGE_WIFI_STATE",
}

# Every permission the reviewed APK carries — no more, no less.
#
# The one androidx.core generates for its own non-exported receivers (signature level, named after the
# applicationId), then each permission with its component. The next ones, each with its own (docs/ARCHITECTURE.md,
# § 6): READ_CALL_LOG, RECEIVE_SMS, PACKAGE_USAGE_STATS, QUERY_ALL_PACKAGES.
ALLOWED = {
    f"{PACKAGE}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
    # When a call starts and ends (platform/telephony/CallStateReceiver). Not the number: that is READ_CALL_LOG.
    "android.permission.READ_PHONE_STATE",
    # The STOP screen started from the background, over the banking app (platform/stop, D-006). An activity, never
    # an overlay window.
    "android.permission.SYSTEM_ALERT_WINDOW",
    # "Hang up" on the STOP screen (platform/stop/CallControl).
    "android.permission.ANSWER_PHONE_CALLS",
    # The phone's dialog "Let app always run in background?" (platform/access/AccessSettings.background, D-023): put to
    # sleep, the protection is deaf. Reads nothing.
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
    # The STOP screen's fallback when the phone blocks it: a notification that opens it (platform/stop/StopNotifier,
    # D-024). The app posts no other.
    "android.permission.POST_NOTIFICATIONS",
    # ... opening the screen at once on a locked or sleeping phone (a full-screen notification).
    "android.permission.USE_FULL_SCREEN_INTENT",
    # A text to the relative the user chose (platform/guardian, D-034). Sends only: never READ_SMS nor RECEIVE_SMS.
    "android.permission.SEND_SMS",
    # Two short buzzes as a STOP opens (platform/stop/StopActivity, U4). Granted at install, reads nothing.
    "android.permission.VIBRATE",
}

# Every component the system binds with a special access — (component, its BIND_* permission) — no more, no less.
ALLOWED_BINDINGS = {
    # Notification access: a banking app's request to approve an operation (platform/notification).
    (f"{PACKAGE}.platform.notification.NotificationListener", "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"),
}

# Must NEVER reach the APK, whatever the list says, each with the reason the failure message gives.
FORBIDDEN_BINDINGS = {
    "android.permission.BIND_ACCESSIBILITY_SERVICE":
        "banking apps detect accessibility services and refuse to run (docs/ARCHITECTURE.md, D-010)",
    "android.permission.BIND_VPN_SERVICE":
        "a VPN carries the phone's traffic: the network Call Halt Tech promises never to touch",
}

BIND_PREFIX = "android.permission.BIND_"
COMPONENTS = {"activity", "activity-alias", "service", "receiver", "provider"}

IN_CI = os.environ.get("GITHUB_ACTIONS") == "true"


def fail(lines: list[str]) -> None:
    prefix = "::error::" if IN_CI else "ERROR: "
    for line in lines:
        print(f"{prefix}{line}")


def parse(manifest_path: str) -> tuple[ET.Element, set[str]]:
    """The manifest root and every permission it requests, parsed as XML rather than grepped."""
    root = ET.parse(manifest_path).getroot()
    names = set()
    # Every spelling, matched by prefix: `<uses-permission-sdk-23>` grants the same capability on API 23+,
    # and the platform parser still accepts its pre-release alias `<uses-permission-sdk-m>` (aapt2 rejects
    # it, the merger keeps it — measured on App Manager Tech, 2026-10-08).
    for element in root.iter():
        if element.tag.startswith("uses-permission"):
            name = element.get(f"{{{ANDROID_NS}}}name")
            if name:
                names.add(name)
    return root, names


def bindings(root: ET.Element) -> tuple[set[tuple[str, str]], list[str]]:
    """Every (component, BIND_* permission) the system binds, with the permission Android applies to it; and every
    component whose permission is a reference, which the manifest alone cannot say."""
    application = root.find("application")
    inherited = permission_of(application, None)
    components = [element for element in application if element.tag in COMPONENTS]
    # An alias without a permission of its own takes its target's, which takes the application's.
    targets = {name_of(element): permission_of(element, inherited) for element in components if element.tag == "activity"}
    found, references = set(), []
    for element in components:
        default = inherited
        if element.tag == "activity-alias":
            default = targets.get(full_name(element.get(f"{{{ANDROID_NS}}}targetActivity") or ""), inherited)
        permission = permission_of(element, default)
        if permission and permission.startswith("@"):
            references.append(f"{name_of(element)} ({permission})")
        elif permission and permission.startswith(BIND_PREFIX):
            found.add((name_of(element), permission))
    return found, references


def permission_of(element: ET.Element, inherited: str | None) -> str | None:
    """The permission Android applies to [element]: its own; none when written empty; else [inherited]."""
    own = element.get(f"{{{ANDROID_NS}}}permission")
    if own is None:
        return inherited
    return own or None


def name_of(element: ET.Element) -> str:
    return full_name(element.get(f"{{{ANDROID_NS}}}name") or "")


def full_name(name: str) -> str:
    """A class name as Android resolves it: the merger writes it in full, a hand-written manifest may not."""
    if name.startswith("."):
        return PACKAGE + name
    return name if "." in name else f"{PACKAGE}.{name}"


def main(argv: list[str]) -> int:
    args = argv[1:]
    if len(args) > 1 or any(a.startswith("--") for a in args):
        print(__doc__)
        return 2
    manifest_path = args[0] if args else DEFAULT_MANIFEST

    if not os.path.isfile(manifest_path):
        fail([
            f"merged manifest not found: {manifest_path}",
            "run first: ./gradlew :app:processReleaseMainManifest",
        ])
        return 1

    try:
        root, found = parse(manifest_path)
    except ET.ParseError as error:
        fail([f"merged manifest unreadable ({manifest_path}): {error}"])
        return 1

    if root.tag != "manifest" or root.find("application") is None:
        fail([
            f"{manifest_path} is not an application manifest -",
            "this is a failure of the check, not an absence of permissions.",
        ])
        return 1

    package = root.get("package")
    if package != PACKAGE:
        fail([
            f"{manifest_path} is not the release manifest of {PACKAGE} (package={package}) -",
            "this is a failure of the check, not a verdict on permissions.",
        ])
        return 1

    if not found:
        # An empty result is a broken check, never a clean bill of health: androidx.core alone declares
        # one permission in every build of this app.
        fail([
            f"no permission found in {manifest_path} -",
            "this is a failure of the check, not an absence of permissions.",
        ])
        return 1

    bound, references = bindings(root)

    print(f"Manifest: {manifest_path}")
    print(f"applicationId: {package}")
    print(f"Permissions ({len(found)}):")
    for name in sorted(found):
        mark = "ok " if name in ALLOWED else "!! "
        print(f"  {mark}{name}")
    print(f"Special accesses ({len(bound)}):")
    for component, permission in sorted(bound):
        mark = "ok " if (component, permission) in ALLOWED_BINDINGS else "!! "
        print(f"  {mark}{component} ({permission})")

    problems: list[str] = []

    forbidden_present = sorted(found & FORBIDDEN)
    if forbidden_present:
        problems.append("NETWORK permission in the release manifest:")
        problems += [f"  {name}" for name in forbidden_present]
        problems.append(
            "Call Halt Tech promises \"no internet permission\". Either the dependency that pulled it goes, "
            "or it is removed with tools:node=\"remove\" and the removal justified.",
        )

    unexpected = sorted(found - ALLOWED - FORBIDDEN)
    if unexpected:
        problems.append("Permission(s) outside the reviewed list:")
        problems += [f"  {name}" for name in unexpected]
        problems.append(
            "A permission must be a deliberate act: add it to ALLOWED in this script AND to PRIVACY.md, "
            "or drop what brought it.",
        )

    missing = sorted(ALLOWED - found)
    if missing:
        problems.append("Permission(s) in the reviewed list but absent from the manifest:")
        problems += [f"  {name}" for name in missing]
        problems.append(
            "The list, PRIVACY.md and the APK must describe the same app: remove them from ALLOWED and "
            "from PRIVACY.md if the removal is intended.",
        )

    problems += binding_problems(bound)

    if references:
        problems.append("Component permission given by reference, which the manifest alone cannot review:")
        problems += [f"  {reference}" for reference in references]
        problems.append("Write the permission's name in full, so that this check sees what Android will apply.")

    if problems:
        print()
        fail(problems)
        return 1

    print()
    print("OK: no network permission, and exactly the reviewed list of permissions and special accesses.")
    return 0


def binding_problems(bound: set[tuple[str, str]]) -> list[str]:
    """The special accesses, held to the same rule as the permissions: none forbidden, exactly the list."""
    problems: list[str] = []

    forbidden = sorted((component, permission) for component, permission in bound if permission in FORBIDDEN_BINDINGS)
    if forbidden:
        problems.append("Special access that must NEVER be in the app:")
        problems += [f"  {component} ({permission}): {FORBIDDEN_BINDINGS[permission]}" for component, permission in forbidden]

    unexpected = sorted(binding for binding in bound - ALLOWED_BINDINGS if binding[1] not in FORBIDDEN_BINDINGS)
    if unexpected:
        problems.append("Special access(es) outside the reviewed list:")
        problems += [f"  {component} ({permission})" for component, permission in unexpected]
        problems.append(
            "An access the user grants in the settings is a deliberate act too: add it to ALLOWED_BINDINGS in this "
            "script AND to PRIVACY.md, or drop the component.",
        )

    missing = sorted(ALLOWED_BINDINGS - bound)
    if missing:
        problems.append("Special access(es) in the reviewed list but absent from the manifest:")
        problems += [f"  {component} ({permission})" for component, permission in missing]
        problems.append(
            "The list, PRIVACY.md and the APK must describe the same app: remove them from ALLOWED_BINDINGS and "
            "from PRIVACY.md if the removal is intended.",
        )
    return problems


if __name__ == "__main__":
    sys.exit(main(sys.argv))
