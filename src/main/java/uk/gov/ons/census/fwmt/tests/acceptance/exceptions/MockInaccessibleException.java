package uk.gov.ons.census.fwmt.tests.acceptance.exceptions;

public class MockInaccessibleException extends RuntimeException {
  public MockInaccessibleException(String reason) {
    super(reason);
  }
}
