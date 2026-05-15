package uk.gov.ons.census.fwmt.tests.acceptance.steps.outcomes;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@Configuration
public class OutcomeQueueConfig {
    @Value("${service.rabbit.username}")
    private String username;

    @Value("${service.rabbit.password}")
    private String password;

    @Value("${service.rabbit.url}")
    private String hostname;

    @Value("${service.rabbit.virtualhost}")
    private String virtualHost;

    @Value("${service.rabbit.port}")
    private int port;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Bean
    public Queue outcomePreprocessingQueue() {
        Queue queue = QueueBuilder.durable("rm.events").build();
        queue.setAdminsThatShouldDeclare(amqpAdmin);
        return queue;
    }

    @Bean
    public AmqpAdmin amqpAdmin() {
        return new RabbitAdmin(connectionFactory());
    }

    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory(hostname, port);

        cachingConnectionFactory.setVirtualHost(virtualHost);
        cachingConnectionFactory.setPassword(password);
        cachingConnectionFactory.setUsername(username);

        return cachingConnectionFactory;
    }
}