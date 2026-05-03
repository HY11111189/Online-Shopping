package com.chuwa.shopping.order.mapper;

import com.chuwa.shopping.order.dto.CartDto;
import com.chuwa.shopping.order.dto.CartItemDto;
import com.chuwa.shopping.dto.order.OrderLineItemDto;
import com.chuwa.shopping.order.entity.AddressSnapshot;
import com.chuwa.shopping.order.entity.CartItem;
import com.chuwa.shopping.order.entity.OrderLineItem;
import com.chuwa.shopping.order.entity.ShoppingCart;
import com.chuwa.shopping.order.entity.ShoppingOrder;
import com.chuwa.shopping.dto.order.AddressSnapshotDto;
import com.chuwa.shopping.dto.order.OrderDto;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class OrderMapper {

    public CartDto toCartDto(ShoppingCart cart) {
        CartDto dto = new CartDto();
        dto.setCustomerId(cart.getCustomerId());
        dto.setCartId(cart.getCartId());
        dto.setStatus(cart.getStatus());
        dto.setCreatedAt(cart.getCreatedAt());
        dto.setUpdatedAt(cart.getUpdatedAt());
        dto.setCheckedOutAt(cart.getCheckedOutAt());
        dto.setItems((cart.getItems() == null ? Collections.<CartItem>emptyList() : cart.getItems())
                .stream()
                .map(this::toCartItemDto)
                .collect(Collectors.toList()));
        return dto;
    }

    public CartItemDto toCartItemDto(CartItem item) {
        CartItemDto dto = new CartItemDto();
        dto.setItemId(item.getItemId());
        dto.setSku(item.getSku());
        dto.setItemName(item.getItemName());
        dto.setUpc(item.getUpc());
        dto.setQuantity(item.getQuantity());
        dto.setUnitPrice(item.getUnitPrice());
        return dto;
    }

    public CartItem toCartItem(CartItemDto dto) {
        CartItem item = new CartItem();
        item.setItemId(dto.getItemId());
        item.setSku(dto.getSku());
        item.setItemName(dto.getItemName());
        item.setUpc(dto.getUpc());
        item.setQuantity(dto.getQuantity());
        item.setUnitPrice(dto.getUnitPrice());
        return item;
    }

    public OrderDto toOrderDto(ShoppingOrder order) {
        OrderDto dto = new OrderDto();
        dto.setCustomerId(order.getKey().getCustomerId());
        dto.setCreatedAt(order.getKey().getCreatedAt());
        dto.setOrderId(order.getKey().getOrderId());
        dto.setOrderNumber(order.getOrderNumber());
        dto.setStatus(order.getStatus() == null ? null : com.chuwa.shopping.dto.order.OrderStatus.valueOf(order.getStatus().name()));
        dto.setStatusReason(order.getStatusReason());
        dto.setCurrencyCode(order.getCurrencyCode());
        dto.setSubtotalAmount(order.getSubtotalAmount());
        dto.setTaxAmount(order.getTaxAmount());
        dto.setShippingAmount(order.getShippingAmount());
        dto.setDiscountAmount(order.getDiscountAmount());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setShippingAddress(toAddressSnapshotDto(order.getShippingAddress()));
        dto.setBillingAddress(toAddressSnapshotDto(order.getBillingAddress()));
        dto.setPaymentMethod(order.getPaymentMethod());
        dto.setItems(order.getItems().stream().map(this::toOrderLineItemDto).collect(Collectors.toList()));
        dto.setCreateRequestId(order.getCreateRequestId());
        dto.setLastUpdateRequestId(order.getLastUpdateRequestId());
        dto.setCancelRequestId(order.getCancelRequestId());
        dto.setPaymentReference(order.getPaymentReference());
        dto.setCompletionReference(order.getCompletionReference());
        dto.setVersion(order.getVersion());
        dto.setUpdatedAt(order.getUpdatedAt());
        dto.setCancelledAt(order.getCancelledAt());
        dto.setPaidAt(order.getPaidAt());
        dto.setCompletedAt(order.getCompletedAt());
        return dto;
    }

    public com.chuwa.shopping.dto.order.OrderLineItemDto toOrderLineItemDto(OrderLineItem item) {
        com.chuwa.shopping.dto.order.OrderLineItemDto dto = new com.chuwa.shopping.dto.order.OrderLineItemDto();
        dto.setItemId(item.getItemId());
        dto.setSku(item.getSku());
        dto.setItemName(item.getItemName());
        dto.setUpc(item.getUpc());
        dto.setQuantity(item.getQuantity());
        dto.setUnitPrice(item.getUnitPrice());
        dto.setLineTotal(item.getLineTotal());
        return dto;
    }

    public OrderLineItem toOrderLineItem(OrderLineItemDto dto) {
        OrderLineItem item = new OrderLineItem();
        item.setItemId(dto.getItemId());
        item.setSku(dto.getSku());
        item.setItemName(dto.getItemName());
        item.setUpc(dto.getUpc());
        item.setQuantity(dto.getQuantity());
        item.setUnitPrice(dto.getUnitPrice());
        item.setLineTotal(dto.getLineTotal());
        return item;
    }

    public com.chuwa.shopping.dto.order.AddressSnapshotDto toAddressSnapshotDto(AddressSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        com.chuwa.shopping.dto.order.AddressSnapshotDto dto = new com.chuwa.shopping.dto.order.AddressSnapshotDto();
        dto.setRecipientName(snapshot.getRecipientName());
        dto.setAddressLine1(snapshot.getAddressLine1());
        dto.setAddressLine2(snapshot.getAddressLine2());
        dto.setCity(snapshot.getCity());
        dto.setState(snapshot.getState());
        dto.setPostalCode(snapshot.getPostalCode());
        dto.setCountry(snapshot.getCountry());
        dto.setPhoneNumber(snapshot.getPhoneNumber());
        return dto;
    }

    public AddressSnapshot toAddressSnapshot(AddressSnapshotDto dto) {
        if (dto == null) {
            return null;
        }
        AddressSnapshot snapshot = new AddressSnapshot();
        snapshot.setRecipientName(dto.getRecipientName());
        snapshot.setAddressLine1(dto.getAddressLine1());
        snapshot.setAddressLine2(dto.getAddressLine2());
        snapshot.setCity(dto.getCity());
        snapshot.setState(dto.getState());
        snapshot.setPostalCode(dto.getPostalCode());
        snapshot.setCountry(dto.getCountry());
        snapshot.setPhoneNumber(dto.getPhoneNumber());
        return snapshot;
    }

    public List<OrderLineItem> toOrderLineItems(List<OrderLineItemDto> dtos) {
        return dtos.stream().map(this::toOrderLineItem).collect(Collectors.toList());
    }
}
