package com.chuwa.shopping.order.messaging;

import com.chuwa.shopping.dto.order.OrderPlacedEvent;
import com.chuwa.shopping.dto.order.OrderDto;
import com.chuwa.shopping.dto.payment.PaymentMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class OrderPlacedEventPublisher {

    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    private final String topicName;

    public OrderPlacedEventPublisher(KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate,
                                     @Value("${shopping.kafka.topic.order-placed:shopping.order.placed.v1}") String topicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    public void publish(OrderDto order, PaymentMethod paymentMethod) {
        OrderPlacedEvent event = new OrderPlacedEvent();
        event.setEventId(order.getCreateRequestId() != null ? order.getCreateRequestId() : order.getOrderNumber() + ":PLACED");
        event.setOrderNumber(order.getOrderNumber());
        event.setCustomerId(order.getCustomerId());
        event.setTotalAmount(order.getTotalAmount());
        event.setCurrencyCode(order.getCurrencyCode());
        event.setPaymentMethod(paymentMethod);
        event.setOccurredAt(Instant.now());
        try {
            kafkaTemplate.send(topicName, order.getOrderNumber(), event).get(10, TimeUnit.SECONDS);
        } catch (Exception publishFailure) {
            throw new IllegalStateException("Failed to publish order event for " + order.getOrderNumber(), publishFailure);
        }
    }
}
