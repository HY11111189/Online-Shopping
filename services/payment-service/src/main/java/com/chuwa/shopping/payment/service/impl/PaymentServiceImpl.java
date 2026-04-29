package com.chuwa.shopping.payment.service.impl;

import com.chuwa.shopping.client.OrderServiceClient;
import com.chuwa.shopping.dto.order.OrderDto;
import com.chuwa.shopping.dto.order.OrderPaymentSyncRequestDto;
import com.chuwa.shopping.dto.order.OrderStatus;
import com.chuwa.shopping.dto.payment.PaymentOperationType;
import com.chuwa.shopping.dto.payment.PaymentStatus;
import com.chuwa.shopping.exception.ShoppingResourceNotFoundException;
import com.chuwa.shopping.payment.dao.PaymentTransactionRepository;
import com.chuwa.shopping.payment.dto.PaymentDto;
import com.chuwa.shopping.payment.dto.PaymentRequestDto;
import com.chuwa.shopping.payment.dto.PaymentUpdateRequestDto;
import com.chuwa.shopping.payment.dto.RefundRequestDto;
import com.chuwa.shopping.payment.entity.PaymentTransaction;
import com.chuwa.shopping.payment.mapper.PaymentMapper;
import com.chuwa.shopping.payment.service.PaymentService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final long CANCEL_WINDOW_HOURS = 24;
    private static final long REFUND_WINDOW_DAYS = 7;

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentMapper paymentMapper;
    private final OrderServiceClient orderServiceClient;

    public PaymentServiceImpl(PaymentTransactionRepository paymentTransactionRepository,
                              PaymentMapper paymentMapper,
                              OrderServiceClient orderServiceClient) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentMapper = paymentMapper;
        this.orderServiceClient = orderServiceClient;
    }

    @Override
    public PaymentDto submitPayment(PaymentRequestDto requestDto) {
        return paymentTransactionRepository.findByIdempotencyKey(requestDto.getIdempotencyKey())
                .map(paymentMapper::toPaymentDto)
                .orElseGet(() -> createOrUpdatePayment(requestDto));
    }

    @Override
    public PaymentDto updatePayment(String paymentNumber, PaymentUpdateRequestDto requestDto) {
        PaymentTransaction payment = getPaymentEntity(paymentNumber);
        payment.setPaymentStatus(requestDto.getPaymentStatus());
        payment.setExternalReference(requestDto.getExternalReference());
        payment.setGatewayResponseCode(requestDto.getGatewayResponseCode());
        payment.setGatewayResponseMessage(requestDto.getGatewayResponseMessage());
        payment.setFailureReason(requestDto.getFailureReason());
        payment.setGatewayUpdatedAt(Instant.now());
        if (requestDto.getPaymentStatus() == PaymentStatus.CAPTURED || requestDto.getPaymentStatus() == PaymentStatus.AUTHORIZED) {
            payment.setProcessedAt(Instant.now());
        }
        PaymentDto savedPayment = paymentMapper.toPaymentDto(paymentTransactionRepository.save(payment));
        if (requestDto.getPaymentStatus() == PaymentStatus.CAPTURED) {
            syncOrderPayment(payment.getOrderId(), payment.getPaymentNumber(), PaymentStatus.CAPTURED, "Payment captured");
        }
        if (requestDto.getPaymentStatus() == PaymentStatus.FAILED) {
            syncOrderPayment(payment.getOrderId(), payment.getPaymentNumber(), PaymentStatus.FAILED, OrderStatus.FAILED, "Payment failed");
        }
        return savedPayment;
    }

    @Override
    public PaymentDto refundPayment(String paymentNumber, RefundRequestDto requestDto) {
        return paymentTransactionRepository.findByIdempotencyKey(requestDto.getIdempotencyKey())
                .map(paymentMapper::toPaymentDto)
                .orElseGet(() -> createReversal(paymentNumber, requestDto, OrderStatus.REFUNDED, "Refund processed"));
    }

    @Override
    public PaymentDto cancelPayment(String paymentNumber, RefundRequestDto requestDto) {
        return paymentTransactionRepository.findByIdempotencyKey(requestDto.getIdempotencyKey())
                .map(paymentMapper::toPaymentDto)
                .orElseGet(() -> createReversal(paymentNumber, requestDto, OrderStatus.CANCELLED, "Order canceled"));
    }

    @Override
    public PaymentDto getPayment(String paymentNumber) {
        return paymentMapper.toPaymentDto(getPaymentEntity(paymentNumber));
    }

    private PaymentDto createOrUpdatePayment(PaymentRequestDto requestDto) {
        OrderDto order = orderServiceClient.getOrder(requestDto.getOrderId());
        validatePaymentRequest(requestDto, order);

        PaymentTransaction payment = paymentTransactionRepository
                .findByOrderIdAndOperationType(order.getOrderNumber(), PaymentOperationType.SUBMIT)
                .orElseGet(() -> new PaymentTransaction());

        if (payment.getPaymentNumber() == null) {
            payment.setPaymentNumber("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (payment.getPaymentStatus() == PaymentStatus.AUTHORIZED
                || payment.getPaymentStatus() == PaymentStatus.CAPTURED
                || payment.getPaymentStatus() == PaymentStatus.REFUNDED
                || payment.getPaymentStatus() == PaymentStatus.CANCELLED) {
            return paymentMapper.toPaymentDto(payment);
        }

        payment.setOrderId(order.getOrderNumber());
        payment.setCustomerId(order.getCustomerId());
        payment.setPaymentMethod(requestDto.getPaymentMethod());
        payment.setOperationType(PaymentOperationType.SUBMIT);
        payment.setPaymentStatus(PaymentStatus.INITIATED);
        payment.setAmount(order.getTotalAmount());
        payment.setCurrencyCode(order.getCurrencyCode());
        payment.setIdempotencyKey(requestDto.getIdempotencyKey());
        payment.setExternalReference(requestDto.getExternalReference());
        payment.setGatewayUpdatedAt(Instant.now());
        return paymentMapper.toPaymentDto(paymentTransactionRepository.save(payment));
    }

    private PaymentDto createReversal(String paymentNumber,
                                      RefundRequestDto requestDto,
                                      OrderStatus targetOrderStatus,
                                      String defaultReason) {
        PaymentTransaction originalPayment = getPaymentEntity(paymentNumber);
        OrderDto order = orderServiceClient.getOrder(originalPayment.getOrderId());
        validateReversalWindow(order, targetOrderStatus);
        PaymentTransaction refund = new PaymentTransaction();
        refund.setPaymentNumber("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        refund.setOrderId(originalPayment.getOrderId());
        refund.setCustomerId(originalPayment.getCustomerId());
        refund.setPaymentMethod(originalPayment.getPaymentMethod());
        refund.setOperationType(PaymentOperationType.REFUND);
        refund.setPaymentStatus(PaymentStatus.REFUNDED);
        refund.setAmount(requestDto.getAmount());
        refund.setCurrencyCode(originalPayment.getCurrencyCode());
        refund.setIdempotencyKey(requestDto.getIdempotencyKey());
        refund.setExternalReference(requestDto.getExternalReference());
        refund.setRelatedPaymentNumber(originalPayment.getPaymentNumber());
        refund.setProcessedAt(Instant.now());
        refund.setReversedAt(Instant.now());
        refund.setGatewayUpdatedAt(Instant.now());
        PaymentDto savedRefund = paymentMapper.toPaymentDto(paymentTransactionRepository.save(refund));
        syncOrderPayment(originalPayment.getOrderId(),
                refund.getPaymentNumber(),
                PaymentStatus.REFUNDED,
                targetOrderStatus,
                defaultReason);
        return savedRefund;
    }

    private PaymentTransaction getPaymentEntity(String paymentNumber) {
        return paymentTransactionRepository.findByPaymentNumber(paymentNumber)
                .orElseThrow(() -> new ShoppingResourceNotFoundException("PaymentTransaction", "paymentNumber", paymentNumber));
    }

    private void validatePaymentRequest(PaymentRequestDto requestDto, OrderDto order) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Payment cannot be submitted for cancelled orders");
        }
        if (requestDto.getPaymentMethod() == null) {
            throw new IllegalArgumentException("Payment method is required");
        }
        if (!order.getCustomerId().equals(requestDto.getCustomerId())) {
            throw new IllegalArgumentException("Customer does not match order");
        }
        if (requestDto.getCurrencyCode() != null && !requestDto.getCurrencyCode().equals(order.getCurrencyCode())) {
            throw new IllegalArgumentException("Currency does not match order");
        }
        if (requestDto.getAmount() != null && order.getTotalAmount() != null
                && requestDto.getAmount().compareTo(order.getTotalAmount()) != 0) {
            throw new IllegalArgumentException("Payment amount must match order total");
        }
    }

    private void syncOrderPayment(String orderNumber,
                                  String paymentReference,
                                  PaymentStatus paymentStatus,
                                  OrderStatus orderStatus,
                                  String statusReason) {
        OrderPaymentSyncRequestDto syncRequest = new OrderPaymentSyncRequestDto();
        syncRequest.setPaymentReference(paymentReference);
        syncRequest.setPaymentStatus(paymentStatus);
        syncRequest.setOrderStatus(orderStatus);
        syncRequest.setStatusReason(statusReason);
        orderServiceClient.syncPayment(orderNumber, syncRequest);
    }

    private void syncOrderPayment(String orderNumber, String paymentReference, PaymentStatus paymentStatus, String statusReason) {
        syncOrderPayment(orderNumber, paymentReference, paymentStatus, null, statusReason);
    }

    private void validateReversalWindow(OrderDto order, OrderStatus targetOrderStatus) {
        if (order.getStatus() != OrderStatus.PAID) {
            throw new IllegalStateException("Only paid orders can be reversed");
        }
        Instant now = Instant.now();
        Instant cancelBase = order.getCreatedAt();
        Instant refundBase = order.getPaidAt() == null ? order.getCreatedAt() : order.getPaidAt();
        if (targetOrderStatus == OrderStatus.CANCELLED && (cancelBase == null || now.isAfter(cancelBase.plus(CANCEL_WINDOW_HOURS, ChronoUnit.HOURS)))) {
            throw new IllegalStateException("Paid orders cannot be canceled after 1 day");
        }
        if (targetOrderStatus == OrderStatus.REFUNDED && (refundBase == null || now.isAfter(refundBase.plus(REFUND_WINDOW_DAYS, ChronoUnit.DAYS)))) {
            throw new IllegalStateException("Refunds are only available for 7 days");
        }
    }
}
