#!/usr/bin/env python3
"""Summarize JVM JaCoCo XML without mistaking Android instrumentation for JVM coverage."""

from pathlib import Path
import sys
import xml.etree.ElementTree as ET

# These classes contain deterministic, Android-independent logic.
# Other code is NOT "tested" by being absent from this focused metric.
INCLUDED = (
    "com/example/shoppinglist/model/",
    "com/example/shoppinglist/share/",
    "com/example/shoppinglist/ui/FormLayoutMeasurements",
)


def line_counts(classes):
    covered = missed = 0
    for item in classes:
        counter = item.find("counter[@type='LINE']")
        if counter is not None:
            covered += int(counter.attrib["covered"])
            missed += int(counter.attrib["missed"])
    return covered, missed


def report(path):
    root = ET.parse(path).getroot()
    classes = []
    for package in root.findall("package"):
        classes.extend(package.findall("class"))
    total_covered, total_missed = line_counts(classes)
    focused = [
        item for item in classes
        if any(item.attrib["name"].startswith(prefix) for prefix in INCLUDED)
    ]
    covered, missed = line_counts(focused)
    if not focused or covered + missed == 0:
        raise ValueError("No eligible classes in JaCoCo XML; verify report and class names")

    def summary(label, hit, miss):
        total = hit + miss
        return f"{label}: {hit}/{total} lines ({hit * 100 / total:.1f}%)" if total else f"{label}: n/a"

    print(summary("All compiled app classes (JVM tests only)", total_covered, total_missed))
    print(summary("Focused pure-Java logic (JVM tests only)", covered, missed))
    print("Included: model/*, share/*, ui/FormLayoutMeasurements*")
    print("Excluded from focused metric: Activity, Adapter, ViewModel, Repository, "
          "ShoppingDatabase, application and Android view/controller classes.")
    print("These excluded classes still need other checks: SQLite Android instrumentation, "
          "integration scenarios, Lint, SpotBugs and manual UI QA.")
    print("This script does not modify the original JaCoCo report or impose a coverage threshold.")


if __name__ == "__main__":
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
        "app/build/reports/coverage/test/debug/report.xml"
    )
    if not source.is_file():
        sys.exit(f"Missing JaCoCo XML: {source}. Check the AGP report path.")
    try:
        report(source)
    except (ET.ParseError, ValueError, KeyError) as error:
        sys.exit(f"Invalid/incomplete JaCoCo report: {error}")
