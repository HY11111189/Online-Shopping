package com.chuwa.shopping.order.dto;

import com.chuwa.shopping.payment.entity.PaymentStatus;

public class OrderPaymentSyncRequestDto {

    private String paymentReference;
    private PaymentStatus paymentStatus;
    private String statusReason;

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }
}
