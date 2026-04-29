package com.chuwa.shopping.payment.messaging;

import com.chuwa.shopping.dto.order.OrderPlacedEvent;
import com.chuwa.shopping.dto.order.OrderPaymentSyncRequestDto;
import com.chuwa.shopping.dto.order.OrderStatus;
import com.chuwa.shopping.dto.payment.PaymentOperationType;
import com.chuwa.shopping.dto.payment.PaymentMethod;
import com.chuwa.shopping.dto.payment.PaymentStatus;
import com.chuwa.shopping.client.OrderServiceClient;
import com.chuwa.shopping.payment.dao.PaymentTransactionRepository;
import com.chuwa.shopping.payment.entity.PaymentTransaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class OrderPlacedEventConsumer {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderServiceClient orderServiceClient;
    private final int maxRetries;

    public OrderPlacedEventConsumer(PaymentTransactionRepository paymentTransactionRepository,
                                    OrderServiceClient orderServiceClient,
                                    @Value("${shopping.kafka.consumer.max-retries:5}") int maxRetries) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.orderServiceClient = orderServiceClient;
        this.maxRetries = maxRetries;
    }

    @KafkaListener(topics = "${shopping.kafka.topic.order-placed:shopping.order.placed.v1}",
            groupId = "${spring.kafka.consumer.group-id:payment-service}")
    public void consume(OrderPlacedEvent event, Acknowledgment acknowledgment) {
        // Reuse the same payment row for a given order submission so Kafka redelivery
        // does not create a second payment attempt.
        PaymentTransaction payment = paymentTransactionRepository
                .findByOrderIdAndOperationType(event.getOrderNumber(), PaymentOperationType.SUBMIT)
                .orElseGet(PaymentTransaction::new);

        // If the payment is already captured and the captured result was synced back to
        // order-service, this Kafka message was already fully handled. Acknowledge it
        // and stop so we do not process the same order twice.
        if (payment.getPaymentStatus() == PaymentStatus.CAPTURED
                && Boolean.TRUE.equals(payment.getOrderSyncCompleted())) {
            acknowledgment.acknowledge();
            return;
        }

        // Terminal business states are also safe to skip. If the order was already
        // finalized as refunded, cancelled, or failed, replaying the same event would
        // only duplicate work.
        if (payment.getPaymentStatus() == PaymentStatus.REFUNDED
                || payment.getPaymentStatus() == PaymentStatus.CANCELLED
                || payment.getPaymentStatus() == PaymentStatus.FAILED) {
            acknowledgment.acknowledge();
            return;
        }

        // Persist an INITIATED checkpoint before attempting capture. This is important
        // for crash recovery: if the service dies after this save but before the Kafka
        // offset is acknowledged, the event will be redelivered and we can resume from
        // the existing payment row instead of creating a new payment.
        payment.setPaymentNumber(payment.getPaymentNumber() == null
                ? "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
                : payment.getPaymentNumber());
        payment.setOrderId(event.getOrderNumber());
        payment.setCustomerId(event.getCustomerId());
        payment.setAmount(event.getTotalAmount());
        payment.setCurrencyCode(event.getCurrencyCode());
        payment.setPaymentMethod(event.getPaymentMethod() == null ? PaymentMethod.CREDIT_CARD : event.getPaymentMethod());
        payment.setOperationType(PaymentOperationType.SUBMIT);
        if (payment.getPaymentStatus() == null) {
            payment.setPaymentStatus(PaymentStatus.INITIATED);
        }
        payment.setGatewayUpdatedAt(Instant.now());
        if (payment.getOrderSyncCompleted() == null) {
            payment.setOrderSyncCompleted(Boolean.FALSE);
        }
        if (payment.getRetryCount() == null) {
            payment.setRetryCount(0);
        }
        if (payment.getIdempotencyKey() == null) {
            payment.setIdempotencyKey(event.getEventId());
        }
        paymentTransactionRepository.save(payment);

        try {
            // Capture only once. If capture already succeeded on a previous delivery but
            // syncing the result back to order-service failed, redelivery should retry
            // only the missing sync step rather than charging again.
            if (payment.getPaymentStatus() != PaymentStatus.CAPTURED) {
                payment.setPaymentStatus(PaymentStatus.CAPTURED);
                payment.setGatewayResponseCode("00");
                payment.setGatewayResponseMessage("Approved");
                payment.setProcessedAt(Instant.now());
                payment.setGatewayUpdatedAt(Instant.now());
                paymentTransactionRepository.save(payment);
            }

            // Sync the successful payment result to order-service. That service applies
            // the order state transition and the inventory deduction. Only after that
            // succeeds do we mark the sync as complete and acknowledge the Kafka record.
            OrderPaymentSyncRequestDto syncRequest = new OrderPaymentSyncRequestDto();
            syncRequest.setPaymentReference(payment.getPaymentNumber());
            syncRequest.setPaymentStatus(PaymentStatus.CAPTURED);
            syncRequest.setStatusReason("Payment captured");
            orderServiceClient.syncPayment(event.getOrderNumber(), syncRequest);
            payment.setOrderSyncCompleted(Boolean.TRUE);
            payment.setRetryCount(0);
            payment.setGatewayUpdatedAt(Instant.now());
            paymentTransactionRepository.save(payment);
        } catch (Exception captureFailure) {
            // Out-of-stock is treated as a final business failure, not a retryable
            // infrastructure error. Persist FAILED, attempt to sync FAILED to the order,
            // then acknowledge the Kafka record so it is not replayed forever.
            if (isInsufficientStockFailure(captureFailure)) {
                payment.setPaymentStatus(PaymentStatus.FAILED);
                payment.setGatewayResponseCode("OUT_OF_STOCK");
                payment.setGatewayResponseMessage("Inventory unavailable");
                payment.setFailureReason("Insufficient stock");
                payment.setGatewayUpdatedAt(Instant.now());
                payment.setOrderSyncCompleted(Boolean.FALSE);
                payment.setRetryCount(0);
                paymentTransactionRepository.save(payment);

                try {
                    OrderPaymentSyncRequestDto failedSync = new OrderPaymentSyncRequestDto();
                    failedSync.setPaymentReference(payment.getPaymentNumber());
                    failedSync.setPaymentStatus(PaymentStatus.FAILED);
                    failedSync.setOrderStatus(OrderStatus.FAILED);
                    failedSync.setStatusReason("Out of stock");
                    orderServiceClient.syncPayment(event.getOrderNumber(), failedSync);
                    acknowledgment.acknowledge();
                    return;
                } catch (Exception syncFailure) {
                    throw syncFailure;
                }
            }

            int nextRetryCount = payment.getRetryCount() == null ? 1 : payment.getRetryCount() + 1;
            payment.setRetryCount(nextRetryCount);
            // Everything else is treated as retryable. We keep the captured state if it
            // already happened, record the latest failure message, and rethrow without
            // acknowledging so Kafka redelivers the event later.
            payment.setFailureReason(captureFailure.getMessage());
            payment.setGatewayUpdatedAt(Instant.now());
            if (nextRetryCount >= maxRetries) {
                finalizeRetryExhausted(event, payment);
                acknowledgment.acknowledge();
                return;
            }
            paymentTransactionRepository.save(payment);
            throw captureFailure;
        }

        // Manual acknowledgment is the commit point for Kafka consumption. Reaching here
        // means payment capture and order sync both succeeded for this event delivery.
        acknowledgment.acknowledge();
    }

    private void finalizeRetryExhausted(OrderPlacedEvent event, PaymentTransaction payment) {
        payment.setGatewayUpdatedAt(Instant.now());
        if (payment.getPaymentStatus() == PaymentStatus.CAPTURED) {
            payment.setGatewayResponseCode("SYNC_RETRY_EXHAUSTED");
            payment.setGatewayResponseMessage("Payment captured; order sync requires manual reconciliation");
            payment.setFailureReason("Retry limit reached after payment capture");
        } else {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            payment.setGatewayResponseCode("PROCESSING_RETRY_EXHAUSTED");
            payment.setGatewayResponseMessage("Payment processing failed after retry limit");
            payment.setFailureReason("Retry limit reached before payment capture");
            try {
                OrderPaymentSyncRequestDto failedSync = new OrderPaymentSyncRequestDto();
                failedSync.setPaymentReference(payment.getPaymentNumber());
                failedSync.setPaymentStatus(PaymentStatus.FAILED);
                failedSync.setOrderStatus(OrderStatus.FAILED);
                failedSync.setStatusReason("Payment processing failed after retry limit");
                orderServiceClient.syncPayment(event.getOrderNumber(), failedSync);
                payment.setOrderSyncCompleted(Boolean.TRUE);
            } catch (Exception ignored) {
                payment.setOrderSyncCompleted(Boolean.FALSE);
            }
        }
        paymentTransactionRepository.save(payment);
    }

    private boolean isInsufficientStockFailure(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("insufficient_stock")
                || normalized.contains("inventory adjustment could not be applied")
                || normalized.contains("requested quantity exceeds available inventory");
    }
}
