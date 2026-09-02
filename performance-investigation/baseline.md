# Acceptance Test Performance Baseline

## Run

| Measure | Value |
| --- | ---: |
| Maven command | `mvn clean verify` |
| Build finished | 2026-09-02 06:58:20 BST |
| Full Maven build time | 24m 52s |
| Test execution time | 24m 36.457s |
| Total tests | 233 |
| Average time per test | 6.34s |

The full Maven build time includes approximately 15.543s outside Surefire test execution, such as clean, report generation, and Maven lifecycle overhead.

## Test Suite Breakdown

| Test suite / runner | Tests | Total time | Average time per test |
| --- | ---: | ---: | ---: |
| `DelegatingMessagingTestClientTest` | 3 | 0.433s | 0.14s |
| `GcpPubSubMessagingTest` | 5 | 0.024s | 0.00s |
| `RunCucumberTest` | 225 | 24m 36s | 6.56s |
| **Total** | **233** | **24m 36.457s** | **6.34s** |

`RunCucumberTest` is the acceptance-test Cucumber runner and accounts for 225 of 233 tests (96.6%) and effectively all of the test execution time. The other entries are Surefire test classes rather than Cucumber runners.

## Evidence

All measurements were taken from `performance-investigation/output.log`:

- Surefire class summaries provide test counts and elapsed time for each row.
- Maven reports `Total time: 24:52 min` and finish time `2026-09-02T06:58:20+01:00`.
- The Cucumber runner completed with 18 test failures; timings above describe the completed run, not a passing baseline.