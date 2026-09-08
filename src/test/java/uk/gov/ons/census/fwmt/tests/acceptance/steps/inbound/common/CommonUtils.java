package uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.common;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import uk.gov.ons.census.fwmt.tests.acceptance.messaging.AcceptanceGatewayEventMonitor;
import uk.gov.ons.census.fwmt.tests.acceptance.timing.PerformanceTimingRecorder;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.JobServiceRefreshUtils;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.PreFlightCheck;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.QueueClient;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.TMMockUtils;

@Component
public class CommonUtils {

    public final static long TIMEOUT = 10000L;

  private static boolean preFlightCompleted;

  private static RuntimeException preFlightFailure;

    @Autowired
    private TMMockUtils tmMockUtils;

    @Autowired
    private QueueClient queueClients;

    @Autowired
    private AcceptanceGatewayEventMonitor gatewayEventMonitor;
    
    @Autowired
    private PreFlightCheck preFlightCheck;

    @Autowired
    private JobServiceRefreshUtils jobServiceRefreshUtils;

    @Autowired
    private PerformanceTimingRecorder performanceTimingRecorder;

    public static Map<String, String> testBucket = new HashMap<>();

    public synchronized void runPreFlightOnce() {
      if (preFlightCompleted) {
        return;
      }
      if (preFlightFailure != null) {
        throw preFlightFailure;
      }

      try {
        preFlightCheck.doCheck();
        preFlightCompleted = true;
      } catch (RuntimeException e) {
        preFlightFailure = e;
        throw e;
      }
    }

    public static synchronized void resetPreFlightStateForTests() {
      preFlightCompleted = false;
      preFlightFailure = null;
    }

    public void setup() throws Exception {
      record("job-service-feature-flags", jobServiceRefreshUtils::enableDefaultFeatureFlags);
      record("tm-mock-enable-request-recorder", tmMockUtils::enableRequestRecorder);
      record("tm-mock-reset", tmMockUtils::resetMock);
      record("tm-mock-clear-database", tmMockUtils::clearDownDatabase);
      record("queue-create", queueClients::createQueue);
      record("queue-reset", queueClients::reset);
      record("gateway-event-monitor-enable", gatewayEventMonitor::enableEventMonitor);
      record("gateway-event-monitor-reset", gatewayEventMonitor::reset);
    }

    private void record(String operationName, PerformanceTimingRecorder.HookOperation operation)
        throws Exception {
      performanceTimingRecorder.recordHookOperation("ScenarioHooks.setup", operationName, operation);
    }

    public void clearDown() throws Exception {
      gatewayEventMonitor.tearDownGatewayEventMonitor();
      tmMockUtils.disableRequestRecorder();
    }





}