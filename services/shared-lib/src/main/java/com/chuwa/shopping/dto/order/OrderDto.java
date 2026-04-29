package com.chuwa.shopping.dto.order;

import com.chuwa.shopping.dto.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrderDto {

    private Long customerId;
    private Instant createdAt;
    private UUID orderId;
    private String orderNumber;
    private OrderStatus status;
    private String statusReason;
    private String currencyCode;
    private BigDecimal subtotalAmount;
    private BigDecimal taxAmount;
    private BigDecimal shippingAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private AddressSnapshotDto shippingAddress;
    private AddressSnapshotDto billingAddress;
    private List<OrderLineItemDto> items = new ArrayList<>();
    private String createRequestId;
    private String lastUpdateRequestId;
    private String cancelRequestId;
    private String paymentReference;
    private String completionReference;
    private Integer version;
    private Instant updatedAt;
    private Instant cancelledAt;
    private Instant paidAt;
    private Instant completedAt;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public BigDecimal getSubtotalAmount() {
        return subtotalAmount;
    }

    public void setSubtotalAmount(BigDecimal subtotalAmount) {
        this.subtotalAmount = subtotalAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getShippingAmount() {
        return shippingAmount;
    }

    public void setShippingAmount(BigDecimal shippingAmount) {
        this.shippingAmount = shippingAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public AddressSnapshotDto getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(AddressSnapshotDto shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public AddressSnapshotDto getBillingAddress() {
        return billingAddress;
    }

    public void setBillingAddress(AddressSnapshotDto billingAddress) {
        this.billingAddress = billingAddress;
    }

    public List<OrderLineItemDto> getItems() {
        return items;
    }

    public void setItems(List<OrderLineItemDto> items) {
        this.items = items;
    }

    public String getCreateRequestId() {
        return createRequestId;
    }

    public void setCreateRequestId(String createRequestId) {
        this.createRequestId = createRequestId;
    }

    public String getLastUpdateRequestId() {
        return lastUpdateRequestId;
    }

    public void setLastUpdateRequestId(String lastUpdateRequestId) {
        this.lastUpdateRequestId = lastUpdateRequestId;
    }

    public String getCancelRequestId() {
        return cancelRequestId;
    }

    public void setCancelRequestId(String cancelRequestId) {
        this.cancelRequestId = cancelRequestId;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public String getCompletionReference() {
        return completionReference;
    }

    public void setCompletionReference(String completionReference) {
        this.completionReference = completionReference;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
