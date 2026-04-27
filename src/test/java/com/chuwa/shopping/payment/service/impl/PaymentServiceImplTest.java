package com.chuwa.shopping.payment.service.impl;

import com.chuwa.shopping.shared.client.OrderServiceClient;
import com.chuwa.shopping.order.dto.OrderDto;
import com.chuwa.shopping.payment.dao.PaymentTransactionRepository;
import com.chuwa.shopping.payment.dto.PaymentDto;
import com.chuwa.shopping.payment.dto.PaymentRequestDto;
import com.chuwa.shopping.payment.dto.PaymentUpdateRequestDto;
import com.chuwa.shopping.payment.dto.RefundRequestDto;
import com.chuwa.shopping.payment.entity.PaymentMethod;
import com.chuwa.shopping.payment.entity.PaymentOperationType;
import com.chuwa.shopping.payment.entity.PaymentStatus;
import com.chuwa.shopping.payment.entity.PaymentTransaction;
import com.chuwa.shopping.shared.mapper.ShoppingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private OrderServiceClient orderServiceClient;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(paymentTransactionRepository, new ShoppingMapper(), orderServiceClient);
    }

    @Test
    void submitPaymentShouldReturnExistingTransactionForSameIdempotencyKey() {
        PaymentTransaction existing = new PaymentTransaction();
        existing.setId(1L);
        existing.setPaymentNumber("PAY-EXISTING");
        existing.setIdempotencyKey("idem-1");
        existing.setPaymentStatus(PaymentStatus.INITIATED);

        when(paymentTransactionRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existing));

        PaymentRequestDto request = new PaymentRequestDto();
        request.setIdempotencyKey("idem-1");

        PaymentDto result = paymentService.submitPayment(request);

        assertEquals("PAY-EXISTING", result.getPaymentNumber());
        verify(paymentTransactionRepository, never()).save(any(PaymentTransaction.class));
    }

    @Test
    void submitPaymentShouldValidateAgainstOrderAndUseOrderTotals() {
        when(paymentTransactionRepository.findByIdempotencyKey("idem-new")).thenReturn(Optional.empty());
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderServiceClient.getOrder("ORD-1")).thenReturn(orderDto());

        PaymentRequestDto request = new PaymentRequestDto();
        request.setOrderId("ORD-1");
        request.setCustomerId(1L);
        request.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        request.setAmount(new BigDecimal("12.99"));
        request.setCurrencyCode("USD");
        request.setIdempotencyKey("idem-new");

        PaymentDto result = paymentService.submitPayment(request);

        assertEquals("ORD-1", result.getOrderId());
        assertEquals(new BigDecimal("12.99"), result.getAmount());
        assertEquals("USD", result.getCurrencyCode());
    }

    @Test
    void refundPaymentShouldCreateRefundLinkedToOriginalPayment() {
        PaymentTransaction original = new PaymentTransaction();
        original.setPaymentNumber("PAY-ORIGINAL");
        original.setOrderId("ORD-1");
        original.setCustomerId(1L);
        original.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        original.setCurrencyCode("USD");

        when(paymentTransactionRepository.findByIdempotencyKey("refund-1")).thenReturn(Optional.empty());
        when(paymentTransactionRepository.findByPaymentNumber("PAY-ORIGINAL")).thenReturn(Optional.of(original));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderServiceClient.syncPayment(eq("ORD-1"), any())).thenReturn(orderDto());

        RefundRequestDto refund = new RefundRequestDto();
        refund.setIdempotencyKey("refund-1");
        refund.setAmount(new BigDecimal("12.99"));
        refund.setExternalReference("ext-refund-1");

        PaymentDto result = paymentService.refundPayment("PAY-ORIGINAL", refund);

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(captor.capture());
        PaymentTransaction saved = captor.getValue();
        assertEquals(PaymentOperationType.REFUND, saved.getOperationType());
        assertEquals(PaymentStatus.REFUNDED, saved.getPaymentStatus());
        assertEquals("PAY-ORIGINAL", saved.getRelatedPaymentNumber());
        assertNotNull(saved.getProcessedAt());
        assertNotNull(saved.getReversedAt());
        assertEquals("PAY-ORIGINAL", result.getRelatedPaymentNumber());
    }

    @Test
    void updatePaymentShouldMarkProcessedAtForCapturedPayment() {
        PaymentTransaction payment = new PaymentTransaction();
        payment.setPaymentNumber("PAY-1");
        payment.setOrderId("ORD-1");
        when(paymentTransactionRepository.findByPaymentNumber("PAY-1")).thenReturn(Optional.of(payment));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderServiceClient.syncPayment(eq("ORD-1"), any())).thenReturn(orderDto());

        PaymentUpdateRequestDto update = new PaymentUpdateRequestDto();
        update.setPaymentStatus(PaymentStatus.CAPTURED);
        update.setGatewayResponseCode("200");
        update.setGatewayResponseMessage("captured");

        PaymentDto result = paymentService.updatePayment("PAY-1", update);

        assertEquals(PaymentStatus.CAPTURED, result.getPaymentStatus());
        assertEquals(PaymentOperationType.UPDATE, payment.getOperationType());
        assertNotNull(payment.getProcessedAt());
        assertNotNull(payment.getGatewayUpdatedAt());
    }

    private OrderDto orderDto() {
        OrderDto order = new OrderDto();
        order.setOrderNumber("ORD-1");
        order.setCustomerId(1L);
        order.setCurrencyCode("USD");
        order.setTotalAmount(new BigDecimal("12.99"));
        return order;
    }
}
