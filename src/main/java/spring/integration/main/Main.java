package spring.integration.main;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.*;
import org.springframework.integration.jms.JmsMessageDrivenEndpoint;
import org.springframework.integration.jms.dsl.*;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.messaging.*;
import org.springframework.messaging.support.*;

import javax.jms.ConnectionFactory;

@EnableIntegration
@SpringBootConfiguration
@EnableAutoConfiguration
public class Main extends SpringBootServletInitializer {

    private static final ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm:/localhost?broker.persistent=false");

    static final String OUT_QUEUE = "test.out";

    static final String IN_QUEUE = "test.in";

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setDefaultDestinationName(IN_QUEUE);
        Person persone = new Person("Mikle", "+79856421");
        template.convertAndSend(persone);
    }

    @Bean
    public ConnectionFactory connectionFactory() {
        connectionFactory.setTrustAllPackages(true);
        return connectionFactory;
    }

    @Bean
    public DefaultMessageListenerContainer container(ConnectionFactory connectionFactory) {
        DefaultMessageListenerContainer container = new DefaultMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setDestinationName(IN_QUEUE);
        container.setAutoStartup(true);
        return container;
    }

    @Bean
    public JmsMessageDrivenEndpoint adapter(DefaultMessageListenerContainer container) {
        return Jms.messageDrivenChannelAdapter(container)
                .outputChannel("msgProcess")
                .errorChannel("errorChannel")
                .autoStartup(true).get();
    }

    @Bean
    public IntegrationFlow integrationFlow() {
        return IntegrationFlows.from("msgProcess")
                .<Person, GenericMessage>transform(source -> {
                    source.setName("Mikle 2");
                    return new GenericMessage<>(source);
                })
                .handle(message -> {
                    Person p = (Person) message.getPayload();
                    if (!p.getName().equals("tre")) {
                        throw new IllegalArgumentException("Wrong name");
                    } else {
                        System.out.println("from chanel " + p.getName());
                    }
                })
                .get();
    }

    @Bean
    public IntegrationFlow errorChannelFlow() {
        return IntegrationFlows.from("errorChannel")
                .handle(m -> {
                    MessagingException e = (MessagingException) m.getPayload();

                    System.out.println(e.getFailedMessage().getPayload());
                })
                .get();
    }

    @Bean
    public JmsOutboundChannelAdapterSpec outboundChannelAdapter(ConnectionFactory connectionFactory) {
        return Jms.outboundAdapter(connectionFactory).destination(OUT_QUEUE);
    }

    @Bean
    public IntegrationFlow outProcess(JmsOutboundChannelAdapterSpec jmsOutboundChannelAdapter) {
        return IntegrationFlows
                .from((MessageSource<String>) () -> new GenericMessage<>("Out prepared msg"), p -> {
                    p.autoStartup(true);
                    p.poller(Pollers.fixedRate(500));
                })
                .handle(jmsOutboundChannelAdapter)
                .get();
    }

    @JmsListener(destination = OUT_QUEUE)
    public void handleOut(String msg) {
        System.out.println(msg);
    }
}
