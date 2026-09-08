#!/usr/bin/env python3
"""Summarise queue-reset and hook-operation timings from a timings.ndjson file.

Every queue-reset performance change in the FMT-128 investigation is judged
against the run's timings.ndjson. This tool aggregates the hook-operation
records (queue-reset orchestration plus per-queue drains) and the
rm-message-wait records into per-operation and per-scenario statistics:

  - queue-reset total, its component operations (pause/drain/resume) and the
    per-queue drains (the critical path is the slowest drain, RM.Field)
  - percentile distribution per operation (p50/p90/p95/max), which surfaces
    the tail-latency variance the cloud builds keep reporting
  - summary across multiple runs so one pass compares builds directly

Parallel-drain caveat: ``queue-reset`` is the sum of its hook operations, not
the max drain, because the drains overlap (6 queues in parallel, 1 thread
each). The queue-reset hook duration therefore equals
pause + slowest_drain + resume + orchestration overhead. The per-queue drains
are reported individually so the critical path is always visible.
"""

from __future__ import annotations

import argparse
import json
import statistics
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

DEFAULT_TIMINGS = Path("target/performance-investigation/timings.ndjson")

# Operations whose timing makes up the queue-reset hook. Everything else under
# ScenarioHooks.setup (feature flags, mock resets, etc.) is reported as "other".
QUEUE_RESET_OPS = ("queue-reset",)
QUEUE_RESET_COMPONENT_PREFIXES = ("queue-reset-pause-", "queue-reset-drain-", "queue-reset-resume-")
QUEUE_RESET_DRAIN_PREFIX = "queue-reset-drain-"


@dataclass
class Stats:
    """Statistics for one operation over a run."""

    values: list[float] = field(default_factory=list)

    def add(self, value_ms: float) -> None:
        self.values.append(value_ms)

    @property
    def count(self) -> int:
        return len(self.values)

    @property
    def total(self) -> float:
        return sum(self.values)

    @property
    def mean(self) -> float:
        return self.total / self.count if self.count else 0.0

    def percentile(self, pct: float) -> float:
        if not self.values:
            return 0.0
        ordered = sorted(self.values)
        rank = pct / 100.0 * (len(ordered) - 1)
        lower = int(rank)
        upper = min(lower + 1, len(ordered) - 1)
        return ordered[lower] + (rank - lower) * (ordered[upper] - ordered[lower])

    def percentile_table_row(self) -> str:
        cells = [f"{self.percentile(pct):8.0f}" for pct in (50, 90, 95)]
        return f"mean={self.mean:7.0f}  min={min(self.values) if self.values else 0:6.0f}  max={max(self.values) if self.values else 0:6.0f}  p50={cells[0]}  p90={cells[1]}  p95={cells[2]}  n={self.count:3d}"


@dataclass
class RunAnalysis:
    """Aggregated hook-operation + rm-message-wait stats for one NDJSON file."""

    run_id: str
    file: Path
    hook_ops: dict[str, Stats] = field(default_factory=lambda: defaultdict(Stats))
    hook_errors: dict[str, int] = field(default_factory=lambda: defaultdict(int))
    queue_reset_ops: dict[str, Stats] = field(default_factory=lambda: defaultdict(Stats))
    rm_waits: dict[str, Stats] = field(default_factory=lambda: defaultdict(Stats))
    scenarios: int = 0
    failed_scenarios: int = 0
    malformed_lines: int = 0


def _group_key(operation_name: str) -> str:
    """Return the queue-reset component group for an operation, or None."""
    for prefix in QUEUE_RESET_COMPONENT_PREFIXES:
        if operation_name.startswith(prefix):
            return prefix.rstrip("-")
    if operation_name in QUEUE_RESET_OPS:
        return "queue-reset"
    return None


def analyse(path: Path, run_id: str | None = None) -> RunAnalysis:
    """Parse an NDJSON file of PerformanceTimingRecorder records."""
    analysis = RunAnalysis(run_id=run_id or "", file=path)

    with path.open(encoding="utf-8") as handle:
        for line_no, line in enumerate(handle, start=1):
            line = line.strip()
            if not line:
                continue
            try:
                record = json.loads(line)
            except json.JSONDecodeError:
                analysis.malformed_lines += 1
                continue

            record_type = record.get("type")
            if record_type == "scenario-start":
                analysis.scenarios += 1
            elif record_type == "scenario-finish":
                if record.get("failed"):
                    analysis.failed_scenarios += 1
            elif record_type == "hook-operation":
                operation = record.get("operationName", "<unknown>")
                duration_ms = record.get("durationMs")
                if duration_ms is None:
                    continue
                analysis.hook_ops[operation].add(float(duration_ms))
                if record.get("outcome") == "error":
                    analysis.hook_errors[operation] += 1
                group = _group_key(operation)
                if group is not None:
                    analysis.queue_reset_ops[group].add(float(duration_ms))
            elif record_type == "rm-message-wait":
                duration_ms = record.get("durationMs")
                if duration_ms is None:
                    continue
                analysis.rm_waits[record.get("logicalQueue", "<unknown>")].add(float(duration_ms))
            # Other record types (e.g. future instrumentation) are ignored.

    return analysis


def _heading(title: str) -> None:
    print(f"\n{title}")
    print("-" * len(title))


def _report_run(analysis: RunAnalysis) -> None:
    print(f"Run: {analysis.run_id or analysis.file}  ({analysis.file})")

    _heading("Suite totals")
    print(f"scenarios          {analysis.scenarios}")
    print(f"failed scenarios   {analysis.failed_scenarios}")
    if analysis.malformed_lines:
        print(f"malformed lines    {analysis.malformed_lines}")

    reset_total = analysis.queue_reset_ops["queue-reset"]
    if reset_total.count:
        _heading("Queue-reset hook")
        stats = reset_total
        print(f"count              {stats.count}")
        print(f"mean               {stats.mean:7.0f} ms")
        print(f"p50                {stats.percentile(50):7.0f} ms")
        print(f"p90                {stats.percentile(90):7.0f} ms")
        print(f"p95                {stats.percentile(95):7.0f} ms")
        print(f"max                {max(stats.values):7.0f} ms")
        print(f"sum                {stats.total:7.0f} ms")

        _heading("Queue-reset components")
        for group in sorted(analysis.queue_reset_ops, key=lambda g: g):
            component = analysis.queue_reset_ops[group]
            if group == "queue-reset":
                continue
            print(f"{group:28s} {component.percentile_table_row()}")
    else:
        _heading("Queue-reset hook")
        print("no queue-reset records found")

    if analysis.hook_ops:
        _heading("Setup-hook operations (top 15 by total time)")
        ranked = sorted(analysis.hook_ops.items(), key=lambda item: item[1].total, reverse=True)[:15]
        for operation, op_stats in ranked:
            print(f"{operation:40s} {op_stats.percentile_table_row()}")

    if analysis.hook_errors:
        _heading("Hook errors by operation")
        for operation, count in sorted(analysis.hook_errors.items(), key=lambda item: item[1], reverse=True):
            print(f"{operation:40s} errors={count}")

    if analysis.rm_waits:
        _heading("RM message waits")
        for queue in sorted(analysis.rm_waits, key=lambda q: analysis.rm_waits[q].total, reverse=True):
            waits = analysis.rm_waits[queue]
            print(f"{queue:30s} {waits.percentile_table_row()}")

    _heading("Critical path (slowest drain)")
    drains = {
        op: op_stats
        for op, op_stats in analysis.hook_ops.items()
        if op.startswith(QUEUE_RESET_DRAIN_PREFIX)
    }
    if drains:
        slowest_op, slowest_stats = max(drains.items(), key=lambda item: item[1].mean)
        print(
            f"{slowest_op}: mean={slowest_stats.mean:.0f}ms, max={max(slowest_stats.values):.0f}ms"
            f" (parallel overhead bound)"
        )
    else:
        print("no per-queue drains found")


def _report_summary(analyses: list[RunAnalysis]) -> None:
    """Print a compact side-by-side of queue-reset means for multiple runs."""
    _heading("Cross-run summary")
    header = f"{'run':<42s} {'mean':>7s} {'p50':>7s} {'p95':>7s} {'max':>7s} {'n':>4s}"
    print(header)
    print("-" * len(header))
    for analysis in analyses:
        reset = analysis.queue_reset_ops["queue-reset"]
        if not reset.count:
            print(f"{analysis.run_id or analysis.file.name:<42s} no queue-reset records")
            continue
        print(
            f"{analysis.run_id or analysis.file.name:<42s} "
            f"{reset.mean:7.0f} {reset.percentile(50):7.0f} {reset.percentile(95):7.0f} "
            f"{max(reset.values):7.0f} {reset.count:4d}"
        )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument(
        "timings",
        nargs="+",
        type=Path,
        help="one or more timings.ndjson files (default: target/performance-investigation/timings.ndjson)",
    )
    parser.add_argument("--json", action="store_true", help="emit machine-readable summary instead of text")
    args = parser.parse_args(argv)

    analyses: list[RunAnalysis] = []
    for path in args.timings:
        if not path.is_file():
            parser.error(f"timings file not found: {path}")
        run_id = path.parent.name if "run" in path.parent.name.lower() else None
        analyses.append(analyse(path, run_id))

    if args.json:
        output = {}
        for analysis in analyses:
            reset = analysis.queue_reset_ops["queue-reset"]
            per_run = {
                "file": str(analysis.file),
                "scenarios": analysis.scenarios,
                "failedScenarios": analysis.failed_scenarios,
                "queueReset": {
                    "count": reset.count,
                    "meanMs": round(reset.mean, 1),
                    "p50Ms": round(reset.percentile(50), 1),
                    "p90Ms": round(reset.percentile(90), 1),
                    "p95Ms": round(reset.percentile(95), 1),
                    "maxMs": round(max(reset.values) if reset.values else 0, 1),
                }
                | {
                    "components": {
                        group: {"meanMs": round(s.mean, 1), "maxMs": round(max(s.values), 1)}
                        for group, s in analysis.queue_reset_ops.items()
                        if group != "queue-reset"
                    }
                },
            }
            output[analysis.run_id or str(analysis.file)] = per_run
        json.dump(output, sys.stdout, indent=2)
        print()
        return 0

    for index, analysis in enumerate(analyses):
        if index:
            print()
        _report_run(analysis)
    if len(analyses) > 1:
        _report_summary(analyses)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())