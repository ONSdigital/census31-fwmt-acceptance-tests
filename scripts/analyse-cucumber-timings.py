#!/usr/bin/env python3
"""Summarise acceptance-test timings from a Cucumber JSON report.

Reports the step-vs-hook split, the slowest step definitions and features,
scenario duration percentiles, and the steps sitting near CommonUtils.TIMEOUT.
Every performance change documented in the Census 2027 acceptance-test investigation
plan is judged by re-running this against the run's cucumber.json.
"""

from __future__ import annotations

import argparse
import json
import statistics
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

DEFAULT_REPORT = Path("target/jsonReports/cucumber.json")

# CommonUtils.TIMEOUT is 10_000 ms; steps above this are effectively at the ceiling.
NEAR_TIMEOUT_SECONDS = 9.0


@dataclass
class Totals:
    count: int = 0
    seconds: float = 0.0

    def add(self, seconds: float) -> None:
        self.count += 1
        self.seconds += seconds

    @property
    def average(self) -> float:
        return self.seconds / self.count if self.count else 0.0


@dataclass
class Scenario:
    feature: str
    name: str
    step_seconds: float = 0.0
    hook_seconds: float = 0.0
    failed: bool = False

    @property
    def seconds(self) -> float:
        return self.step_seconds + self.hook_seconds


@dataclass
class NearTimeoutStep:
    feature: str
    scenario: str
    location: str
    status: str
    seconds: float


@dataclass
class Analysis:
    scenarios: list[Scenario] = field(default_factory=list)
    by_definition: dict[str, Totals] = field(default_factory=lambda: defaultdict(Totals))
    by_feature: dict[str, Totals] = field(default_factory=lambda: defaultdict(Totals))
    near_timeout: list[NearTimeoutStep] = field(default_factory=list)

    @property
    def step_seconds(self) -> float:
        return sum(s.step_seconds for s in self.scenarios)

    @property
    def hook_seconds(self) -> float:
        return sum(s.hook_seconds for s in self.scenarios)

    @property
    def total_seconds(self) -> float:
        return self.step_seconds + self.hook_seconds


def _duration_seconds(entry: dict) -> float:
    return entry.get("result", {}).get("duration", 0) / 1e9


def _location(entry: dict) -> str:
    return entry.get("match", {}).get("location", "<unmatched>")


def analyse(report: list[dict], near_timeout_seconds: float) -> Analysis:
    analysis = Analysis()

    for feature in report:
        feature_name = feature.get("name", "<unnamed feature>")

        for element in feature.get("elements", []):
            if element.get("type") != "scenario":
                continue

            scenario = Scenario(feature=feature_name, name=element.get("name", "<unnamed scenario>"))

            for phase in ("before", "after"):
                for hook in element.get(phase, []):
                    seconds = _duration_seconds(hook)
                    scenario.hook_seconds += seconds
                    analysis.by_definition[f"HOOK @{phase} {_location(hook)}"].add(seconds)

            for step in element.get("steps", []):
                seconds = _duration_seconds(step)
                status = step.get("result", {}).get("status", "unknown")
                scenario.step_seconds += seconds
                scenario.failed |= status == "failed"
                analysis.by_definition[_location(step)].add(seconds)

                if seconds >= near_timeout_seconds:
                    analysis.near_timeout.append(
                        NearTimeoutStep(feature_name, scenario.name, _location(step), status, seconds)
                    )

            analysis.by_feature[feature_name].add(scenario.seconds)
            analysis.scenarios.append(scenario)

    return analysis


def _minutes(seconds: float) -> str:
    return f"{seconds / 60:.2f} min"


def _percent(part: float, whole: float) -> str:
    return f"{part / whole * 100:.0f}%" if whole else "n/a"


def _heading(title: str) -> None:
    print(f"\n{title}")
    print("-" * len(title))


def report(analysis: Analysis, top: int, near_timeout_seconds: float) -> None:
    total = analysis.total_seconds
    scenarios = analysis.scenarios

    if not scenarios:
        print("No scenarios found in report.")
        return

    _heading("Suite totals")
    print(f"scenarios          {len(scenarios)}")
    print(f"total              {_minutes(total)}")
    print(f"steps              {_minutes(analysis.step_seconds)} ({_percent(analysis.step_seconds, total)})")
    print(f"hooks              {_minutes(analysis.hook_seconds)} ({_percent(analysis.hook_seconds, total)})")

    durations = sorted(s.seconds for s in scenarios)
    _heading("Scenario duration percentiles")
    for label, value in (
        ("p50", statistics.median(durations)),
        ("p90", durations[int(0.90 * (len(durations) - 1))]),
        ("p95", durations[int(0.95 * (len(durations) - 1))]),
        ("max", durations[-1]),
    ):
        print(f"{label}                {value:7.2f}s")

    failed = [s for s in scenarios if s.failed]
    passed = [s for s in scenarios if not s.failed]
    _heading("Pass/fail time split")
    for label, group in (("failed", failed), ("passed", passed)):
        if not group:
            print(f"{label:8s} n=0")
            continue
        group_seconds = sum(s.seconds for s in group)
        print(
            f"{label:8s} n={len(group):4d}  {_minutes(group_seconds):>10s} "
            f"({_percent(group_seconds, total)})  avg={group_seconds / len(group):6.2f}s"
        )

    _heading(f"Top {top} step definitions and hooks by total time")
    ranked = sorted(analysis.by_definition.items(), key=lambda item: item[1].seconds, reverse=True)
    for location, totals in ranked[:top]:
        print(
            f"{_minutes(totals.seconds):>10s} ({_percent(totals.seconds, total):>4s})  "
            f"n={totals.count:5d}  avg={totals.average:6.2f}s  {location}"
        )

    _heading("Features by total time")
    for name, totals in sorted(analysis.by_feature.items(), key=lambda item: item[1].seconds, reverse=True):
        print(
            f"{_minutes(totals.seconds):>10s} ({_percent(totals.seconds, total):>4s})  "
            f"n={totals.count:4d}  avg={totals.average:6.2f}s  {name}"
        )

    _heading(f"Steps at or above {near_timeout_seconds:.0f}s")
    if not analysis.near_timeout:
        print("none")
        return

    near_by_definition: dict[tuple[str, str], Totals] = defaultdict(Totals)
    for step in analysis.near_timeout:
        near_by_definition[(step.location, step.status)].add(step.seconds)

    near_seconds = sum(s.seconds for s in analysis.near_timeout)
    print(
        f"{len(analysis.near_timeout)} steps, {_minutes(near_seconds)} "
        f"({_percent(near_seconds, total)} of suite)"
    )
    for (location, status), totals in sorted(near_by_definition.items(), key=lambda item: item[1].seconds, reverse=True):
        print(f"{_minutes(totals.seconds):>10s}  n={totals.count:5d}  {status:8s} {location}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument(
        "report",
        nargs="?",
        type=Path,
        default=DEFAULT_REPORT,
        help=f"Cucumber JSON report (default: {DEFAULT_REPORT})",
    )
    parser.add_argument("--top", type=int, default=20, help="number of step definitions to list (default: 20)")
    parser.add_argument(
        "--near-timeout-seconds",
        type=float,
        default=NEAR_TIMEOUT_SECONDS,
        help=f"threshold for near-timeout steps (default: {NEAR_TIMEOUT_SECONDS})",
    )
    parser.add_argument("--json", action="store_true", help="emit machine-readable summary instead of a text report")
    args = parser.parse_args(argv)

    if not args.report.is_file():
        parser.error(f"report not found: {args.report}\nRun the suite first, or pass an archived cucumber.json.")

    with args.report.open(encoding="utf-8") as handle:
        analysis = analyse(json.load(handle), args.near_timeout_seconds)

    if args.json:
        json.dump(
            {
                "report": str(args.report),
                "scenarios": len(analysis.scenarios),
                "totalSeconds": round(analysis.total_seconds, 3),
                "stepSeconds": round(analysis.step_seconds, 3),
                "hookSeconds": round(analysis.hook_seconds, 3),
                "failedScenarios": sum(1 for s in analysis.scenarios if s.failed),
                "nearTimeoutSteps": len(analysis.near_timeout),
                "byDefinition": {
                    location: {"count": totals.count, "seconds": round(totals.seconds, 3)}
                    for location, totals in sorted(
                        analysis.by_definition.items(), key=lambda item: item[1].seconds, reverse=True
                    )
                },
            },
            sys.stdout,
            indent=2,
        )
        print()
    else:
        print(f"Report: {args.report}")
        report(analysis, args.top, args.near_timeout_seconds)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
