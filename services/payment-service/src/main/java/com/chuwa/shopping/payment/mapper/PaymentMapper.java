package com.chuwa.shopping.payment.mapper;

import com.chuwa.shopping.payment.dto.PaymentDto;
import com.chuwa.shopping.payment.entity.PaymentTransaction;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentDto toPaymentDto(PaymentTransaction payment) {
        PaymentDto dto = new PaymentDto();
        dto.setId(payment.getId());
        dto.setPaymentNumber(payment.getPaymentNumber());
        dto.setOrderId(payment.getOrderId());
        dto.setCustomerId(payment.getCustomerId());
        dto.setPaymentMethod(payment.getPaymentMethod());
        dto.setOperationType(payment.getOperationType());
        dto.setPaymentStatus(payment.getPaymentStatus());
        dto.setAmount(payment.getAmount());
        dto.setCurrencyCode(payment.getCurrencyCode());
        dto.setIdempotencyKey(payment.getIdempotencyKey());
        dto.setExternalReference(payment.getExternalReference());
        dto.setRelatedPaymentNumber(payment.getRelatedPaymentNumber());
        dto.setGatewayResponseCode(payment.getGatewayResponseCode());
        dto.setGatewayResponseMessage(payment.getGatewayResponseMessage());
        dto.setFailureReason(payment.getFailureReason());
        dto.setProcessedAt(payment.getProcessedAt());
        dto.setReversedAt(payment.getReversedAt());
        dto.setGatewayUpdatedAt(payment.getGatewayUpdatedAt());
        dto.setCreatedAt(payment.getCreatedAt());
        dto.setUpdatedAt(payment.getUpdatedAt());
        return dto;
    }
}
