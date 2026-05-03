package com.chuwa.shopping.payment.messaging;

import com.chuwa.shopping.dto.order.PaymentProcessedEvent;
import com.chuwa.shopping.payment.dto.PaymentDto;
import com.chuwa.shopping.dto.order.OrderStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Component
public class PaymentProcessedEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessedEventPublisher.class);

    private final KafkaTemplate<String, PaymentProcessedEvent> kafkaTemplate;
    private final String topicName;

    public PaymentProcessedEventPublisher(KafkaTemplate<String, PaymentProcessedEvent> kafkaTemplate,
                                          @Value("${shopping.kafka.topic.payment-processed:shopping.payment.processed.v1}") String topicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    public void publish(PaymentDto paymentDto, OrderStatus orderStatus, String statusReason) {
        PaymentProcessedEvent event = new PaymentProcessedEvent();
        event.setEventId(paymentDto.getIdempotencyKey() != null ? paymentDto.getIdempotencyKey() : paymentDto.getPaymentNumber());
        event.setOrderNumber(paymentDto.getOrderId());
        event.setPaymentNumber(paymentDto.getPaymentNumber());
        event.setCustomerId(paymentDto.getCustomerId());
        event.setPaymentStatus(paymentDto.getPaymentStatus());
        event.setOrderStatus(orderStatus);
        event.setStatusReason(statusReason);
        event.setAmount(paymentDto.getAmount());
        event.setCurrencyCode(paymentDto.getCurrencyCode());
        event.setOccurredAt(Instant.now());
        kafkaTemplate.send(topicName, paymentDto.getOrderId(), event)
                .addCallback(new ListenableFutureCallback<>() {
                    @Override
                    public void onSuccess(org.springframework.kafka.support.SendResult<String, PaymentProcessedEvent> result) {
                        // best effort; no-op
                    }

                    @Override
                    public void onFailure(Throwable ex) {
                        log.warn("Failed to publish payment event for {}", paymentDto.getPaymentNumber(), ex);
                    }
                });
    }
}
