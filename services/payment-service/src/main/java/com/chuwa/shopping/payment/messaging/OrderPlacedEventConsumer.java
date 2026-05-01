package com.chuwa.shopping.payment.messaging;

import com.chuwa.shopping.dto.order.OrderPlacedEvent;
import com.chuwa.shopping.dto.order.OrderPaymentSyncRequestDto;
import com.chuwa.shopping.dto.order.OrderStatus;
import com.chuwa.shopping.dto.order.OrderDto;
import com.chuwa.shopping.dto.order.OrderLineItemDto;
import com.chuwa.shopping.dto.item.InventoryAdjustmentRequestDto;
import com.chuwa.shopping.dto.item.InventoryAdjustmentType;
import com.chuwa.shopping.dto.payment.PaymentOperationType;
import com.chuwa.shopping.dto.payment.PaymentMethod;
import com.chuwa.shopping.dto.payment.PaymentStatus;
import com.chuwa.shopping.client.OrderServiceClient;
import com.chuwa.shopping.client.ItemServiceClient;
import com.chuwa.shopping.payment.dao.PaymentTransactionRepository;
import com.chuwa.shopping.payment.entity.PaymentTransaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OrderPlacedEventConsumer {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderServiceClient orderServiceClient;
    private final ItemServiceClient itemServiceClient;
    private final int maxRetries;

    public OrderPlacedEventConsumer(PaymentTransactionRepository paymentTransactionRepository,
                                    OrderServiceClient orderServiceClient,
                                    ItemServiceClient itemServiceClient,
                                    @Value("${shopping.kafka.consumer.max-retries:5}") int maxRetries) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.orderServiceClient = orderServiceClient;
        this.itemServiceClient = itemServiceClient;
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

        // If the payment is already captured and the order update already succeeded,
        // this message was fully handled. Acknowledge and stop.
        if (payment.getPaymentStatus() == PaymentStatus.CAPTURED
                && Boolean.TRUE.equals(payment.getOrderSyncCompleted())) {
            acknowledgment.acknowledge();
            return;
        }

        // Terminal states do not need more work. Redelivering the same event would only
        // duplicate work.
        if (payment.getPaymentStatus() == PaymentStatus.REFUNDED
                || payment.getPaymentStatus() == PaymentStatus.CANCELLED
                || payment.getPaymentStatus() == PaymentStatus.FAILED) {
            acknowledgment.acknowledge();
            return;
        }

        // Persist an INITIATED checkpoint before doing any remote work. If the service
        // crashes, Kafka can redeliver the event and we resume from the same payment row.
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
        if (payment.getInventoryAdjusted() == null) {
            payment.setInventoryAdjusted(Boolean.FALSE);
        }
        if (payment.getRetryCount() == null) {
            payment.setRetryCount(0);
        }
        if (payment.getIdempotencyKey() == null) {
            payment.setIdempotencyKey(event.getEventId());
        }
        paymentTransactionRepository.save(payment);

        OrderDto order = orderServiceClient.getOrder(event.getOrderNumber());
        try {
            // Deduct stock first. If this event is redelivered later, item-service will
            // ignore the same operation id instead of subtracting inventory twice.
            if (!Boolean.TRUE.equals(payment.getInventoryAdjusted())) {
                adjustInventory(order, payment.getPaymentNumber(), InventoryAdjustmentType.PURCHASE);
                payment.setInventoryAdjusted(Boolean.TRUE);
                paymentTransactionRepository.save(payment);
            }

            // Capture only once. If capture already succeeded but the order sync failed,
            // a retry should continue from the saved payment row instead of charging again.
            if (payment.getPaymentStatus() != PaymentStatus.CAPTURED) {
                payment.setPaymentStatus(PaymentStatus.CAPTURED);
                payment.setGatewayResponseCode("00");
                payment.setGatewayResponseMessage("Approved");
                payment.setProcessedAt(Instant.now());
                payment.setGatewayUpdatedAt(Instant.now());
                paymentTransactionRepository.save(payment);
            }

            // Sync the successful payment result to order-service. Only after the order
            // update succeeds do we mark the message complete and acknowledge Kafka.
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
                    // Stock could not be reserved, so fail the order immediately.
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
            // Any other failure is treated as retryable. If stock was already deducted,
            // we keep that state and let Kafka redeliver the event later.
            payment.setFailureReason(captureFailure.getMessage());
            payment.setGatewayUpdatedAt(Instant.now());
            if (nextRetryCount >= maxRetries) {
                // Retries are exhausted. If stock was already deducted, undo it now and
                // mark the order as failed.
                if (Boolean.TRUE.equals(payment.getInventoryAdjusted())) {
                    adjustInventory(order, payment.getPaymentNumber(), InventoryAdjustmentType.RESTOCK);
                    payment.setInventoryAdjusted(Boolean.FALSE);
                }
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
                paymentTransactionRepository.save(payment);
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

    private void adjustInventory(OrderDto order, String operationPrefix, InventoryAdjustmentType adjustmentType) {
        List<InventoryLine> changedItems = new ArrayList<>();
        try {
            for (InventoryLine item : groupBySku(order.getItems())) {
                InventoryAdjustmentRequestDto requestDto = new InventoryAdjustmentRequestDto();
                requestDto.setAdjustmentType(adjustmentType);
                requestDto.setQuantity(item.quantity);
                requestDto.setReference(order.getOrderNumber());
                requestDto.setOperationId(operationPrefix + ":" + adjustmentType.name().toLowerCase() + ":" + item.sku);
                itemServiceClient.adjustInventory(item.sku, requestDto);
                changedItems.add(item);
            }
        } catch (RuntimeException inventoryFailure) {
            // If one item adjustment fails after earlier items already changed, undo the
            // earlier successful changes so the inventory stays consistent.
            if (adjustmentType == InventoryAdjustmentType.PURCHASE && !changedItems.isEmpty()) {
                rollbackChangedItems(order, operationPrefix, changedItems);
            }
            throw inventoryFailure;
        }
    }

    private void rollbackChangedItems(OrderDto order, String operationPrefix, List<InventoryLine> changedItems) {
        for (InventoryLine item : changedItems) {
            InventoryAdjustmentRequestDto requestDto = new InventoryAdjustmentRequestDto();
            requestDto.setAdjustmentType(InventoryAdjustmentType.RESTOCK);
            requestDto.setQuantity(item.quantity);
            requestDto.setReference(order.getOrderNumber());
            requestDto.setOperationId(operationPrefix + ":rollback:" + item.sku);
            itemServiceClient.adjustInventory(item.sku, requestDto);
        }
    }

    private List<InventoryLine> groupBySku(List<OrderLineItemDto> items) {
        Map<String, InventoryLine> grouped = new LinkedHashMap<>();
        if (items == null) {
            return new ArrayList<>();
        }
        for (OrderLineItemDto item : items) {
            if (item == null || item.getSku() == null || item.getQuantity() == null) {
                continue;
            }
            InventoryLine existing = grouped.get(item.getSku());
            if (existing == null) {
                grouped.put(item.getSku(), new InventoryLine(item.getSku(), item.getQuantity()));
            } else {
                existing.quantity += item.getQuantity();
            }
        }
        return new ArrayList<>(grouped.values());
    }

    private static final class InventoryLine {
        private final String sku;
        private int quantity;

        private InventoryLine(String sku, int quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }
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
