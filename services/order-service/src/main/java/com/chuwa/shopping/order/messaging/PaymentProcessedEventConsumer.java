package com.chuwa.shopping.order.messaging;

import com.chuwa.shopping.dto.order.OrderPaymentSyncRequestDto;
import com.chuwa.shopping.dto.order.OrderStatus;
import com.chuwa.shopping.dto.order.PaymentProcessedEvent;
import com.chuwa.shopping.dto.payment.PaymentStatus;
import com.chuwa.shopping.order.service.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class PaymentProcessedEventConsumer {

    private final OrderService orderService;

    public PaymentProcessedEventConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = "${shopping.kafka.topic.payment-processed:shopping.payment.processed.v1}",
            groupId = "${spring.kafka.consumer.group-id:order-service}")
    public void consume(PaymentProcessedEvent event, Acknowledgment acknowledgment) {
        OrderPaymentSyncRequestDto requestDto = new OrderPaymentSyncRequestDto();
        requestDto.setPaymentReference(event.getPaymentNumber());
        requestDto.setPaymentStatus(event.getPaymentStatus());
        requestDto.setOrderStatus(event.getOrderStatus() == null
                ? defaultOrderStatus(event.getPaymentStatus())
                : event.getOrderStatus());
        requestDto.setStatusReason(event.getStatusReason());
        orderService.syncPaymentStatus(event.getOrderNumber(), requestDto);
        acknowledgment.acknowledge();
    }

    private OrderStatus defaultOrderStatus(PaymentStatus paymentStatus) {
        if (paymentStatus == PaymentStatus.CAPTURED) {
            return OrderStatus.PAID;
        }
        if (paymentStatus == PaymentStatus.REFUNDED) {
            return OrderStatus.REFUNDED;
        }
        if (paymentStatus == PaymentStatus.CANCELLED) {
            return OrderStatus.CANCELLED;
        }
        return OrderStatus.FAILED;
    }
}
