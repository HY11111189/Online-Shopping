package com.chuwa.shopping.order.entity;

import com.chuwa.shopping.dto.order.OrderStatus;
import org.springframework.data.cassandra.core.mapping.CassandraType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Table("shopping_orders")
public class ShoppingOrder {

    @PrimaryKey
    private OrderKey key;

    @Column("order_number")
    private String orderNumber;

    private OrderStatus status = OrderStatus.CREATED;

    @Column("status_reason")
    private String statusReason;

    @Column("currency_code")
    private String currencyCode = "USD";

    @Column("subtotal_amount")
    private BigDecimal subtotalAmount = BigDecimal.ZERO;

    @Column("tax_amount")
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column("shipping_amount")
    private BigDecimal shippingAmount = BigDecimal.ZERO;

    @Column("discount_amount")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column("total_amount")
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column("shipping_address")
    private AddressSnapshot shippingAddress;

    @Column("billing_address")
    private AddressSnapshot billingAddress;

    @CassandraType(type = CassandraType.Name.LIST, typeArguments = CassandraType.Name.UDT, userTypeName = "order_line_items")
    private List<OrderLineItem> items = new ArrayList<>();

    @Column("create_request_id")
    private String createRequestId;

    @Column("last_update_request_id")
    private String lastUpdateRequestId;

    @Column("cancel_request_id")
    private String cancelRequestId;

    @Column("payment_reference")
    private String paymentReference;

    @Column("completion_reference")
    private String completionReference;

    private Integer version = 1;

    @Column("updated_at")
    private Instant updatedAt;

    @Column("cancelled_at")
    private Instant cancelledAt;

    @Column("paid_at")
    private Instant paidAt;

    @Column("completed_at")
    private Instant completedAt;

    @Column("processed_payment_event_ids")
    @CassandraType(type = CassandraType.Name.LIST, typeArguments = CassandraType.Name.TEXT)
    private List<String> processedPaymentEventIds = new ArrayList<>();

    public OrderKey getKey() {
        return key;
    }

    public void setKey(OrderKey key) {
        this.key = key;
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

    public AddressSnapshot getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(AddressSnapshot shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public AddressSnapshot getBillingAddress() {
        return billingAddress;
    }

    public void setBillingAddress(AddressSnapshot billingAddress) {
        this.billingAddress = billingAddress;
    }

    public List<OrderLineItem> getItems() {
        return items;
    }

    public void setItems(List<OrderLineItem> items) {
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

    public List<String> getProcessedPaymentEventIds() {
        return processedPaymentEventIds;
    }

    public void setProcessedPaymentEventIds(List<String> processedPaymentEventIds) {
        this.processedPaymentEventIds = processedPaymentEventIds;
    }
}
