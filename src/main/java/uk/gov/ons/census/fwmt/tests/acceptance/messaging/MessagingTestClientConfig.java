package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MessagingTestClientConfig {

  @Bean
  @Primary
  public MessagingTestClient messagingTestClient(
      ObjectProvider<PubSubEmulatorMessaging> emulatorClientProvider,
      ObjectProvider<GcpPubSubMessaging> gcpClientProvider,
      @Value("${fwmt.pubsub.mode:emulator}") String pubSubMode) {
    return new DelegatingMessagingTestClient(
        emulatorClientProvider.getIfAvailable(), gcpClientProvider.getIfAvailable(), pubSubMode);
  }
}

