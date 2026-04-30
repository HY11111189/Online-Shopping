package com.chuwa.shopping.order.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class OrderCreateRequestDto {

    private Long customerId;
    private String currencyCode;
    private BigDecimal taxAmount;
    private BigDecimal shippingAmount;
    private BigDecimal discountAmount;
    private AddressSnapshotDto shippingAddress;
    private AddressSnapshotDto billingAddress;
    private List<OrderLineItemDto> items = new ArrayList<>();
    private String createRequestId;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
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
}
