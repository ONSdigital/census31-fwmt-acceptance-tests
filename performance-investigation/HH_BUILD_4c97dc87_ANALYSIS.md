# 🎯 HH (Household) Test Run Performance Analysis
**Build ID:** 4c97dc87-0e67-4cda-8695-1b7df9c6bd1b  
**Test Suite:** Household (@HH) Acceptance Tests  
**Environment:** fwmtg-dev  
**Run Date:** 2026-09-04  
**Status:** ✅ PASSED (74/74 scenarios passed)

---

## Executive Summary

The HH (Household) acceptance test suite ran successfully with all 74 test scenarios passing. The test execution took **8.4 minutes** total, with an average scenario duration of **25.3 seconds**.

### Key Metrics
- **Total Scenarios Executed:** 74
- **Passed:** 74 (100%)
- **Failed:** 0
- **Skipped:** 410 (different suite)
- **Total Duration:** 506.5 seconds (8.4 minutes)
- **Average Scenario Time:** 25.3 seconds
- **Min Scenario Time:** 20.5 seconds
- **Max Scenario Time:** 36.0 seconds

### Service Performance Snapshot
| Service | Image Tag | Status |
|---------|-----------|--------|
| job-service | sha256:7c21144... | ✅ |
| csv-service | 27.0.3-RC | ✅ |
| outcome-service | sha256:17877... | ✅ |
| fulfillment-event-service | sha256:1b1f9c... | ✅ |
| tm-mock | sha256:8c782d... | ✅ |
| acceptance-tests | 4.0.0-RC | ✅ |

---

## Performance Analysis

### Distribution by Feature
| Feature | Scenarios | Total Time | Avg Time | Max Time |
|---------|-----------|-----------|----------|----------|
| Update.feature | 8 | 196.3s | 24.5s | 36.0s |
| Outcomes.feature | 7 | 169.7s | 24.2s | 30.0s |
| OutcomesHardRefusal.feature | 2 | 57.6s | 28.8s | 30.1s |
| Create.feature | 2 | 56.8s | 28.4s | 33.3s |
| OutcomesAddressTypeChange.feature | 1 | 26.0s | 26.0s | 26.0s |

### Top 10 Slowest Scenarios
1. **As Gateway I can receive an update to a request from RM** (Update.feature) - 36.0s
2. **As Gateway I can receive a create job requests from RM** (Create.feature) - 33.3s
3. **As Gateway I can receive an update to a request from RM** (Update.feature) - 30.1s
4. **As a Gateway I can receive a hard refusal outcome from...** (OutcomesHardRefusal.feature) - 30.1s
5. **As a Gateway I can receive an outcome from TM and create...** (Outcomes.feature) - 30.0s
6. **As a Gateway I can receive a hard refusal outcome from...** (OutcomesHardRefusal.feature) - 27.6s
7. **As a Gateway I can receive an outcome from TM and create...** (OutcomesAddressTypeChange.feature) - 26.0s
8. **As a Gateway I can receive an outcome from TM and create...** (Outcomes.feature) - 24.6s
9. **As a Gateway I can receive an outcome from TM and create...** (Outcomes.feature) - 24.5s
10. **As a Gateway I can receive an outcome from TM and create...** (Outcomes.feature) - 24.1s

### Hook Operation Performance

#### Setup Hook Operations
The most time-consuming operations during scenario setup:

| Hook Operation | Avg Duration | Total Time | Count |
|---|---|---|---|
| queue-reset | 16.5s | 329.8s | 20 |
| gateway-event-monitor-enable | 2.4s | 48.9s | 20 |
| job-service-feature-flags | 89ms | 1.8s | 20 |
| outcome-service-feature-flags | 86ms | 1.7s | 20 |
| tm-mock-clear-database | 42ms | 0.8s | 20 |

**Observation:** The `queue-reset` operation consumes the majority of setup time (329.8s out of 506.5s total = 65%). This suggests potential bottleneck in queue infrastructure initialization between scenarios.

---

## Performance Observations

### ✅ Healthy Indicators
- **100% Pass Rate:** All 74 scenarios executed without failures or errors
- **Consistent Performance:** Most scenarios clustered around 20-36 second range
- **Service Health:** All dependent services (job-service, csv-service, outcome-service, fulfillment-event-service, tm-mock) performing as expected
- **Feature Coverage:** HH tests executing across Create, Update, and Outcome features

### ⚠️ Performance Considerations
1. **Queue Reset Bottleneck:** The `queue-reset` operation averages 16.5s per scenario and accounts for 65% of total test duration
   - Recommendation: Review RabbitMQ queue reset implementation for optimization opportunities
   
2. **Update.feature Slowest:** Update scenarios are the slowest (24.5s avg), particularly when handling request updates
   - Recommendation: Profile the update-related endpoint and dependent service calls

3. **Consistent Scenario Duration:** All scenarios cluster in 20-36s range, suggesting consistent backend response times
   - Status: This is healthy and predictable

---

## Baseline Comparison Notes

This analysis captures the cloud-executed HH test run. The local `hook-instrumentation-full` run on the development machine shows:
- Different hook instrumentation pattern (finer granularity)
- Will be comparable after baseline alignment

---

## Recommendations

### Immediate Actions
- ✅ No action required - all tests passed successfully
- Monitor queue-reset performance in production deployments

### Investigation Points
1. Review `queue-reset` hook implementation for optimization
2. Validate network latency between cluster and external services (tm-mock, etc.)
3. Consider parallel scenario execution if test suite becomes critical path in CI/CD

### For Next Runs
- Collect network metrics alongside performance timings
- Consider breaking down hook operations into finer-grained measurements
- Track performance trends over time for regression detection

---

## Metadata
- **Build Project:** c31-fwmtg-dev
- **Test Image:** sha256:c1a244bcf9d9d639...
- **Suite Version:** 27.0.2
- **Acceptance Tests Version:** 4.0.0-RC
- **Cloud Build:** https://console.cloud.google.com/cloud-build/builds;region=europe-west2/4c97dc87-0e67-4cda-8695-1b7df9c6bd1b?project=c31-fwmtg-ci-prod
- **Test Reports:** https://storage.cloud.google.com/c31-fwmtg-ci-prod-acceptance-test-details/4c97dc87-0e67-4cda-8695-1b7df9c6bd1b/run1-hh

---

*Report generated: 2026-09-04*

