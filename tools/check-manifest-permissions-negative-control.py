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


def manifest(permissions: list[str], package: str = PACKAGE, application: bool = True) -> str:
    body = "".join(permission(name) for name in permissions)
    if application:
        body += '    <application android:label="x" />\n'
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    package="{package}">\n{body}</manifest>\n'
    )


HEALTHY = sorted(GUARD_MODULE.ALLOWED)

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
