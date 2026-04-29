package com.chuwa.shopping.payment.service.impl;

import com.chuwa.shopping.shared.client.OrderServiceClient;
import com.chuwa.shopping.payment.dao.PaymentTransactionRepository;
import com.chuwa.shopping.exception.ShoppingResourceNotFoundException;
import com.chuwa.shopping.order.dto.OrderDto;
import com.chuwa.shopping.order.dto.OrderPaymentSyncRequestDto;
import com.chuwa.shopping.order.entity.OrderStatus;
import com.chuwa.shopping.payment.entity.PaymentOperationType;
import com.chuwa.shopping.payment.entity.PaymentStatus;
import com.chuwa.shopping.payment.entity.PaymentTransaction;
import com.chuwa.shopping.payment.dto.PaymentDto;
import com.chuwa.shopping.payment.dto.PaymentRequestDto;
import com.chuwa.shopping.payment.dto.PaymentUpdateRequestDto;
import com.chuwa.shopping.payment.dto.RefundRequestDto;
import com.chuwa.shopping.payment.service.PaymentService;
import com.chuwa.shopping.shared.mapper.ShoppingMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ShoppingMapper shoppingMapper;
    private final OrderServiceClient orderServiceClient;

    public PaymentServiceImpl(PaymentTransactionRepository paymentTransactionRepository,
                              ShoppingMapper shoppingMapper,
                              OrderServiceClient orderServiceClient) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.shoppingMapper = shoppingMapper;
        this.orderServiceClient = orderServiceClient;
    }

    @Override
    public PaymentDto submitPayment(PaymentRequestDto requestDto) {
        return paymentTransactionRepository.findByIdempotencyKey(requestDto.getIdempotencyKey())
                .map(shoppingMapper::toPaymentDto)
                .orElseGet(() -> createPayment(requestDto));
    }

    @Override
    public PaymentDto updatePayment(String paymentNumber, PaymentUpdateRequestDto requestDto) {
        PaymentTransaction payment = getPaymentEntity(paymentNumber);
        payment.setOperationType(PaymentOperationType.UPDATE);
        payment.setPaymentStatus(requestDto.getPaymentStatus());
        payment.setExternalReference(requestDto.getExternalReference());
        payment.setGatewayResponseCode(requestDto.getGatewayResponseCode());
        payment.setGatewayResponseMessage(requestDto.getGatewayResponseMessage());
        payment.setFailureReason(requestDto.getFailureReason());
        payment.setGatewayUpdatedAt(Instant.now());
        if (requestDto.getPaymentStatus() == PaymentStatus.CAPTURED || requestDto.getPaymentStatus() == PaymentStatus.AUTHORIZED) {
            payment.setProcessedAt(Instant.now());
        }
        PaymentDto savedPayment = shoppingMapper.toPaymentDto(paymentTransactionRepository.save(payment));
        if (requestDto.getPaymentStatus() == PaymentStatus.CAPTURED) {
            syncOrderPayment(payment.getOrderId(), payment.getPaymentNumber(), PaymentStatus.CAPTURED, "Payment captured");
        }
        return savedPayment;
    }

    @Override
    public PaymentDto refundPayment(String paymentNumber, RefundRequestDto requestDto) {
        return paymentTransactionRepository.findByIdempotencyKey(requestDto.getIdempotencyKey())
                .map(shoppingMapper::toPaymentDto)
                .orElseGet(() -> createRefund(paymentNumber, requestDto));
    }

    @Override
    public PaymentDto getPayment(String paymentNumber) {
        return shoppingMapper.toPaymentDto(getPaymentEntity(paymentNumber));
    }

    private PaymentDto createPayment(PaymentRequestDto requestDto) {
        OrderDto order = orderServiceClient.getOrder(requestDto.getOrderId());
        validatePaymentRequest(requestDto, order);

        PaymentTransaction payment = new PaymentTransaction();
        payment.setPaymentNumber("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
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
        return shoppingMapper.toPaymentDto(paymentTransactionRepository.save(payment));
    }

    private PaymentDto createRefund(String paymentNumber, RefundRequestDto requestDto) {
        PaymentTransaction originalPayment = getPaymentEntity(paymentNumber);
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
        PaymentDto savedRefund = shoppingMapper.toPaymentDto(paymentTransactionRepository.save(refund));
        syncOrderPayment(originalPayment.getOrderId(), refund.getPaymentNumber(), PaymentStatus.REFUNDED, "Refund processed");
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

    private void syncOrderPayment(String orderNumber, String paymentReference, PaymentStatus paymentStatus, String statusReason) {
        OrderPaymentSyncRequestDto syncRequest = new OrderPaymentSyncRequestDto();
        syncRequest.setPaymentReference(paymentReference);
        syncRequest.setPaymentStatus(paymentStatus);
        syncRequest.setStatusReason(statusReason);
        orderServiceClient.syncPayment(orderNumber, syncRequest);
    }
}
