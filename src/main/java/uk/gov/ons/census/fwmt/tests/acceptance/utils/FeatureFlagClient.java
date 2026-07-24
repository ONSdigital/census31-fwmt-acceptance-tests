package uk.gov.ons.census.fwmt.tests.acceptance.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Acceptance-test helper that sets feature flags on the running services via
 * the dedicated test-support endpoints.
 *
 * <p>Both methods produce a single HTTP call to the service under test (when
 * the service is running with {@code APP_TESTING=true}), eliminating the many
 * individual {@code /env} writes that were previously needed.</p>
 *
 * <p>If the service does not expose the test-support endpoint (e.g. an older
 * build), the delegates fall back automatically to the legacy
 * {@code /env + /refresh} approach.</p>
 */
@Slf4j
@Component
public class FeatureFlagClient {

  private final JobServiceRefreshUtils jobServiceRefreshUtils;
  private final OutcomeServiceRefreshUtils outcomeServiceRefreshUtils;

  @Autowired
  public FeatureFlagClient(JobServiceRefreshUtils jobServiceRefreshUtils,
      OutcomeServiceRefreshUtils outcomeServiceRefreshUtils) {
    this.jobServiceRefreshUtils = jobServiceRefreshUtils;
    this.outcomeServiceRefreshUtils = outcomeServiceRefreshUtils;
  }

  /**
   * Resets all job-service feature flags to {@code enabled}.
   * Use {@code true} in scenario setup to enable all flags, or
   * {@code false} to disable all flags.
   */
  public void resetAllJobFlags(boolean enabled) {
    if (enabled) {
      jobServiceRefreshUtils.enableDefaultFeatureFlags();
    } else {
      // The reset endpoint also accepts false — delegate directly
      jobServiceRefreshUtils.setCreateFeatureFlagAndRefresh("HH", "create", false);
    }
  }

  /**
   * Sets a single job-service feature flag for the given survey and action.
   *
   * @param survey e.g. {@code "HH"}, {@code "CE"}, {@code "SPG"}, {@code "CCS"}, {@code "NC"}
   * @param action e.g. {@code "CREATE"}, {@code "UPDATE"}, {@code "CANCEL"}
   * @param enabled desired flag value
   */
  public void setJobFlag(String survey, String action, boolean enabled) {
    jobServiceRefreshUtils.setCreateFeatureFlagAndRefresh(survey, action, enabled);
  }

  /**
   * Resets all outcome-service feature flags to {@code enabled}.
   * Use {@code true} in scenario setup to enable all surveys.
   */
  public void resetAllOutcomeFlags(boolean enabled) {
    if (enabled) {
      outcomeServiceRefreshUtils.enableDefaultOutcomeFeatureFlags();
    } else {
      // Disable each known survey individually via the single-flag endpoint
      for (String survey : new String[]{"HH", "SPG", "CE", "CCS", "NC"}) {
        outcomeServiceRefreshUtils.setOutcomeFeatureFlagAndRefresh(survey, false);
      }
    }
  }

  /**
   * Sets a single outcome-service feature flag for the given survey.
   *
   * @param survey  e.g. {@code "HH"}, {@code "CE"}, {@code "CCS"}
   * @param enabled desired flag value
   */
  public void setOutcomeFlag(String survey, boolean enabled) {
    outcomeServiceRefreshUtils.setOutcomeFeatureFlagAndRefresh(survey, enabled);
  }
}

