package uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.common;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import uk.gov.ons.census.fwmt.tests.acceptance.messaging.AcceptanceGatewayEventMonitor;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.PreFlightCheck;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.QueueClient;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.TMMockUtils;

@Component
public class CommonUtils {

    public final static long TIMEOUT = 10000L;

    @Autowired
    private TMMockUtils tmMockUtils;

    @Autowired
    private QueueClient queueClients;

    @Autowired
    private AcceptanceGatewayEventMonitor gatewayEventMonitor;
    
    @Autowired
    private PreFlightCheck preFlightCheck;

    @Value("${service.rabbit.url}")
    private String rabbitLocation;

    @Value("${service.rabbit.username}")
    private String rabbitUsername;

    @Value("${service.rabbit.password}")
    private String rabbitPassword;

    @Value("${service.rabbit.port:5672}")
    private Integer rabbitPort;

    public static Map<String, String> testBucket = new HashMap<>();

    public void setup() throws Exception {
      preFlightCheck.doCheck();
      tmMockUtils.enableRequestRecorder();
      tmMockUtils.resetMock();
      tmMockUtils.clearDownDatabase();
      queueClients.createQueue();
      queueClients.reset();
      gatewayEventMonitor.enableEventMonitor(rabbitLocation, rabbitUsername, rabbitPassword, rabbitPort);
      gatewayEventMonitor.reset();
    }

    public void clearDown() throws Exception {
      gatewayEventMonitor.tearDownGatewayEventMonitor();
      tmMockUtils.disableRequestRecorder();
    }





}