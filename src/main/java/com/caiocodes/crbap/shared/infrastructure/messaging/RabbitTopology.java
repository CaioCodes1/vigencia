package com.caiocodes.crbap.shared.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A topologia do RabbitMQ, declarada em código.
 *
 * <p>Declarar aqui e não no painel de administração é o que faz um ambiente novo
 * subir igual ao anterior: fila criada na mão some no primeiro
 * {@code docker compose down -v} e volta com configuração levemente diferente.
 *
 * <p><b>Topic exchange e não direct:</b> a fila de notificações se liga a
 * {@code contract.*} e pega criado, ativado, renovado e cancelado com um binding
 * só. Com {@code direct} seria um binding por evento, e todo evento novo
 * exigiria mexer aqui — que é o acoplamento que o exchange existe para evitar.
 *
 * <p>Três coisas evitam perder mensagem, e faltando uma a garantia some: fila
 * <b>durable</b> (sobrevive ao restart do broker), mensagem <b>persistent</b>
 * (vai para disco, é o padrão do Spring AMQP) e <b>publisher confirms</b>
 * ligado no {@code application.yml} (o broker responde "recebi").
 */
@Configuration
public class RabbitTopology {

    public static final String EXCHANGE = "crbap.events";
    public static final String DLX = "crbap.dlx";
    public static final String DLQ = "crbap.dlq";
    public static final String QUEUE_CONTRACT = "notification.contract";
    public static final String QUEUE_BILLING = "notification.billing";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DLX, true, false);
    }

    @Bean
    public Queue notificationContractQueue() {
        return QueueBuilder.durable(QUEUE_CONTRACT)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey("dead.contract")
                .build();
    }

    @Bean
    public Queue notificationBillingQueue() {
        return QueueBuilder.durable(QUEUE_BILLING)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey("dead.billing")
                .build();
    }

    /**
     * Uma DLQ só, ligada a {@code dead.#}.
     *
     * <p>Separar por origem espalharia a atenção de quem monitora — e uma DLQ
     * que ninguém olha é um buraco onde eventos somem em silêncio. O alerta
     * (fase 8) dispara com {@code rabbitmq_queue_messages{queue="crbap.dlq"} > 0}.
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding bindContract() {
        return BindingBuilder.bind(notificationContractQueue())
                .to(eventsExchange()).with("contract.*");
    }

    @Bean
    public Binding bindBilling() {
        return BindingBuilder.bind(notificationBillingQueue())
                .to(eventsExchange()).with("billing.*");
    }

    @Bean
    public Binding bindDeadLetter() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with("dead.#");
    }

    /**
     * JSON no corpo, e não serialização Java.
     *
     * <p>O corpo da mensagem é o mesmo JSON que está na outbox — legível no
     * painel do broker, e independente de classe Java. Serialização nativa
     * amarraria o consumidor à mesma versão do pacote e da classe do produtor.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
