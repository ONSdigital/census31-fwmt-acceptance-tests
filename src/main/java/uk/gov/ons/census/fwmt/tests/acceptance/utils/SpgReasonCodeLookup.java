package uk.gov.ons.census.fwmt.tests.acceptance.utils;

import java.util.HashMap;
import java.util.Map;

public class SpgReasonCodeLookup {

  private final Map<String, String> spgReasonCodeMap = new HashMap<>();

  public String getLookup(String outcomeCode) {
    return spgReasonCodeMap.get(outcomeCode);
  }

  public void add(String productCode, String processorNames) {
    spgReasonCodeMap.put(productCode, processorNames);
  }

}
