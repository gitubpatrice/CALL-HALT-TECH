#!/usr/bin/env python3
"""NEGATIVE control of check-manifest-permissions.py — the proof that the guard can go red, for the right reason.

A guard that returned 0 whatever happened would hand out a false guarantee on every run. So each failure
it claims to catch is REPRODUCED here on a throwaway manifest, and two things are verified: the guard
returns 1, and its output names the RIGHT cause. A POSITIVE WITNESS opens the series — a healthy manifest,
built from the guard's own list, must return 0 — or a guard broken into failing on everything would pass.

Modelled on Read Files Tech's check-guards-negative-control.py, for this one guard. No dependencies, never
touches the repository.

    python3 tools/check-manifest-permissions-negative-control.py
"""

from __future__ import annotations

import importlib.util
import os
import subprocess
import sys
import tempfile

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

HERE = os.path.dirname(os.path.abspath(__file__))
GUARD = os.path.join(HERE, "check-manifest-permissions.py")


def guard_module():
    spec = importlib.util.spec_from_file_location("check_manifest_permissions", GUARD)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


GUARD_MODULE = guard_module()
PACKAGE = GUARD_MODULE.PACKAGE


def permission(name: str) -> str:
    return f'    <uses-permission android:name="{name}" />\n'


def bound(component: str, guard: str | None, tag: str = "service", extra: str = "") -> str:
    """A component the system binds, written across lines as the merger writes it; no permission when [guard] is None."""
    permission = "" if guard is None else f'\n            android:permission="{guard}"'
    return (
        f'        <{tag}\n            android:name="{component}"\n            android:exported="true"{extra}'
        f'{permission} />\n'
    )


HEALTHY = sorted(GUARD_MODULE.ALLOWED)
HEALTHY_BINDINGS = sorted(GUARD_MODULE.ALLOWED_BINDINGS)


def manifest(
    permissions: list[str],
    package: str = PACKAGE,
    application: bool = True,
    bindings: list[tuple[str, str]] = HEALTHY_BINDINGS,
) -> str:
    body = "".join(permission(name) for name in permissions)
    if application:
        components = "".join(bound(component, guard) for component, guard in bindings)
        body += f'    <application android:label="x">\n{components}    </application>\n'
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    package="{package}">\n{body}</manifest>\n'
    )


LISTENER, LISTENER_GUARD = HEALTHY_BINDINGS[0]
# The same listener under the relative name a hand-written manifest uses: the same access, not another one.
RELATIVE_LISTENER = [(LISTENER.removeprefix(PACKAGE), LISTENER_GUARD)]
ACCESSIBILITY = HEALTHY_BINDINGS + [(f"{PACKAGE}.Reader", "android.permission.BIND_ACCESSIBILITY_SERVICE")]
INPUT_METHOD = HEALTHY_BINDINGS + [(f"{PACKAGE}.Keyboard", "android.permission.BIND_INPUT_METHOD")]
# Device administration is a RECEIVER the system binds: every kind of component counts, not only services.
DEVICE_ADMIN = manifest(HEALTHY).replace(
    "    </application>",
    bound(f"{PACKAGE}.Admin", "android.permission.BIND_DEVICE_ADMIN", tag="receiver") + "    </application>",
)


def with_components(components: str, application_permission: str | None = None) -> str:
    """The healthy manifest plus [components], under an `<application android:permission>` when one is given."""
    healthy = manifest(HEALTHY)
    if application_permission is not None:
        healthy = healthy.replace('<application android:label="x">', f'<application android:label="x" android:permission="{application_permission}">')
    return healthy.replace("    </application>", components + "    </application>")


ACCESSIBILITY_GUARD = "android.permission.BIND_ACCESSIBILITY_SERVICE"
# The review of 2026-11-02 slipped an accessibility service past the first version: the component carries no
# permission, the application does, and Android applies it to every component that has none of its own.
INHERITED = with_components(bound(f"{PACKAGE}.Reader", None), application_permission=ACCESSIBILITY_GUARD)
# The other way round: a component's EMPTY permission cancels the application's, so nothing is bound with it.
CANCELLED = with_components(bound(f"{PACKAGE}.Reader", ""), application_permission=ACCESSIBILITY_GUARD)
# A reference resolves at install time, to whatever the string says: the manifest alone cannot be reviewed.
REFERENCE = with_components(bound(f"{PACKAGE}.Reader", "@string/reader_permission"))
# An alias without a permission of its own takes its target's: two components bound, not one.
ALIAS = with_components(
    bound(f"{PACKAGE}.Keyboard", "android.permission.BIND_INPUT_METHOD", tag="activity")
    + bound(f"{PACKAGE}.KeyboardAlias", None, tag="activity-alias", extra=f'\n            android:targetActivity="{PACKAGE}.Keyboard"'),
)

# The merger writes a permission across several lines, under its API-23 spelling too: a grep would miss it.
NETWORK_MULTILINE = manifest(HEALTHY).replace(
    "<application",
    '<uses-permission-sdk-23\n        android:name="android.permission.ACCESS_NETWORK_STATE"\n        />\n    <application',
)

CASES = [
    # (label, manifest content or None for "no file", expected code, text the output must contain)
    ("witness: the reviewed list, exactly", manifest(HEALTHY), 0, "OK: no network permission"),
    ("INTERNET added", manifest(HEALTHY + ["android.permission.INTERNET"]), 1, "NETWORK permission"),
    ("network permission across lines, API-23 spelling", NETWORK_MULTILINE, 1, "NETWORK permission"),
    ("a permission outside the list", manifest(HEALTHY + ["android.permission.READ_SMS"]), 1, "outside the reviewed list"),
    ("a listed permission gone", manifest(["android.permission.VIBRATE"]), 1, "absent from the manifest"),
    ("no permission at all", manifest([]), 1, "no permission found"),
    ("another app's manifest", manifest(HEALTHY, package="com.example.other"), 1, "is not the release manifest of"),
    ("not an application manifest", manifest(HEALTHY, application=False), 1, "is not an application manifest"),
    ("unreadable XML", "<manifest", 1, "unreadable"),
    ("no manifest built", None, 1, "merged manifest not found"),
    ("witness: the listener under its relative name", manifest(HEALTHY, bindings=RELATIVE_LISTENER), 0, "OK: no network"),
    ("the reviewed special access gone", manifest(HEALTHY, bindings=[]), 1, "Special access(es) in the reviewed list"),
    ("an accessibility service", manifest(HEALTHY, bindings=ACCESSIBILITY), 1, "must NEVER be in the app"),
    ("a special access outside the list", manifest(HEALTHY, bindings=INPUT_METHOD), 1, "Special access(es) outside"),
    ("a special access on a receiver", DEVICE_ADMIN, 1, "Special access(es) outside"),
    ("accessibility inherited from <application>", INHERITED, 1, "must NEVER be in the app"),
    ("witness: an empty permission cancels the inherited one", CANCELLED, 0, "OK: no network"),
    ("a component permission given by reference", REFERENCE, 1, "given by reference"),
    ("an alias inherits its target's special access", ALIAS, 1, f"{PACKAGE}.KeyboardAlias (android.permission.BIND_INPUT_METHOD)"),
]


def main() -> int:
    failures = 0
    with tempfile.TemporaryDirectory() as work:
        for index, (label, content, code, text) in enumerate(CASES):
            target = os.path.join(work, f"case{index}.xml")
            if content is not None:
                with open(target, "w", encoding="utf-8", newline="\n") as f:
                    f.write(content)
            result = subprocess.run(
                [sys.executable, GUARD, target], capture_output=True, text=True, encoding="utf-8", check=False,
            )
            output = result.stdout + result.stderr
            ok = result.returncode == code and text in output
            failures += 0 if ok else 1
            verdict = "detected, right cause" if code else "passes"
            print(f"  {'OK  ' if ok else 'FAIL'}  {label:<50} {verdict if ok else f'code={result.returncode}'}")
            if not ok:
                print(f"        expected code {code} and {text!r} in:\n{output}")

    print()
    if failures:
        print(f"NEGATIVE CONTROL: {failures} case(s) FAILED - the guard cannot be trusted as it is.")
        return 1
    print(f"NEGATIVE CONTROL: {len(CASES)}/{len(CASES)} -- every defect is detected, and for the right reason.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
