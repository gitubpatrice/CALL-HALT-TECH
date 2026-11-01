#!/usr/bin/env python3
"""Fail when a unit-test suite ran ZERO tests, or any failed — read in the JUnit XML Gradle writes.

Why: under Gradle 9, a JUnit Platform launcher missing from the test runtime runs no test at all, and the
task stays GREEN (App Manager Tech #13). "BUILD SUCCESSFUL" says nothing about how many tests ran; the
reports do.

    python3 tools/check-test-results.py      (after :domain:test and :app:testDebugUnitTest)
"""

from __future__ import annotations

import glob
import os
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SUITES = {
    "domain": os.path.join("domain", "build", "test-results", "test"),
    "app": os.path.join("app", "build", "test-results", "testDebugUnitTest"),
}


def main() -> int:
    broken = False
    for module, folder in SUITES.items():
        reports = glob.glob(os.path.join(ROOT, folder, "*.xml"))
        tests = failures = skipped = 0
        for report in reports:
            suite = ET.parse(report).getroot()
            tests += int(suite.get("tests", 0))
            failures += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
            skipped += int(suite.get("skipped", 0))
        print(f"{module}: {len(reports)} report(s), {tests} test(s), {failures} failed, {skipped} skipped")
        if tests == 0:
            print(f"ERROR: {module} ran NO test - a launcher missing, or the reports were not written")
            broken = True
        if failures:
            print(f"ERROR: {module} has {failures} failed test(s)")
            broken = True
    return 1 if broken else 0


if __name__ == "__main__":
    sys.exit(main())
