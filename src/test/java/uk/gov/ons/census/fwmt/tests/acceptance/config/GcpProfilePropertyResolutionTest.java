package uk.gov.ons.census.fwmt.tests.acceptance.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class GcpProfilePropertyResolutionTest {

  private static final String ENV_PROPERTY = "FWMT_PUBSUB_PROJECT";

  @AfterEach
  void clearOverrides() {
    System.clearProperty(ENV_PROPERTY);
  }

  @Test
  void shouldResolvePubSubProjectFromOverrideInGcpInclusterProfile() throws IOException {
    assertThat(resolvePubSubProject("application-gcp-incluster.properties", "c31-fwmtg-test"))
        .isEqualTo("c31-fwmtg-test");
  }

  @Test
  void shouldKeepDevDefaultWhenOverrideIsAbsentInGcpInclusterProfile() throws IOException {
    assertThat(resolvePubSubProject("application-gcp-incluster.properties", null))
        .isEqualTo("c31-fwmtg-dev");
  }

  private String resolvePubSubProject(String resourcePath, String overrideValue) throws IOException {
    if (overrideValue == null) {
      System.clearProperty(ENV_PROPERTY);
    } else {
      System.setProperty(ENV_PROPERTY, overrideValue);
    }

    Properties properties =
        PropertiesLoaderUtils.loadProperties(new ClassPathResource(resourcePath));

    ConfigurableEnvironment environment = new StandardEnvironment();
    if (overrideValue != null) {
      environment
        .getPropertySources()
        .addFirst(new MapPropertySource("testOverrides", Map.of(ENV_PROPERTY, overrideValue)));
    }

    return environment.resolveRequiredPlaceholders(properties.getProperty("fwmt.pubsub.project"));
  }
}