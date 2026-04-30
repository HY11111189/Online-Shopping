package com.chuwa.shopping.order.dto;

public class OrderCancelRequestDto {

    private String cancelRequestId;
    private String statusReason;

    public String getCancelRequestId() {
        return cancelRequestId;
    }

    public void setCancelRequestId(String cancelRequestId) {
        this.cancelRequestId = cancelRequestId;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }
}
