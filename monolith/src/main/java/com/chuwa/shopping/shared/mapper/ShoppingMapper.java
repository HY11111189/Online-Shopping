package com.chuwa.shopping.shared.mapper;

import com.chuwa.shopping.account.entity.CustomerAccount;
import com.chuwa.shopping.account.entity.CustomerAddress;
import com.chuwa.shopping.account.entity.StoredPaymentMethod;
import com.chuwa.shopping.item.entity.InventoryDocument;
import com.chuwa.shopping.item.entity.ItemDocument;
import com.chuwa.shopping.order.entity.AddressSnapshot;
import com.chuwa.shopping.order.entity.CartItem;
import com.chuwa.shopping.order.entity.OrderLineItem;
import com.chuwa.shopping.order.entity.ShoppingCart;
import com.chuwa.shopping.order.entity.ShoppingOrder;
import com.chuwa.shopping.payment.entity.PaymentTransaction;
import com.chuwa.shopping.account.dto.AccountDto;
import com.chuwa.shopping.account.dto.AddressDto;
import com.chuwa.shopping.account.dto.StoredPaymentMethodDto;
import com.chuwa.shopping.item.dto.InventoryDto;
import com.chuwa.shopping.item.dto.ItemDto;
import com.chuwa.shopping.order.dto.AddressSnapshotDto;
import com.chuwa.shopping.order.dto.CartDto;
import com.chuwa.shopping.order.dto.CartItemDto;
import com.chuwa.shopping.order.dto.OrderDto;
import com.chuwa.shopping.order.dto.OrderLineItemDto;
import com.chuwa.shopping.payment.dto.PaymentDto;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ShoppingMapper {

    public ItemDto toItemDto(ItemDocument document) {
        ItemDto dto = new ItemDto();
        dto.setId(document.getId());
        dto.setSku(document.getSku());
        dto.setUpc(document.getUpc());
        dto.setItemName(document.getItemName());
        dto.setBrand(document.getBrand());
        dto.setCategory(document.getCategory());
        dto.setDescription(document.getDescription());
        dto.setUnitPrice(document.getUnitPrice());
        dto.setListPrice(document.getListPrice());
        dto.setDiscountPercent(document.getDiscountPercent());
        dto.setCurrencyCode(document.getCurrencyCode());
        dto.setPictureUrls(document.getPictureUrls());
        dto.setStatus(document.getStatus());
        dto.setInventory(toInventoryDto(document.getInventory()));
        dto.setAttributes(document.getAttributes());
        dto.setCreatedAt(document.getCreatedAt());
        dto.setUpdatedAt(document.getUpdatedAt());
        return dto;
    }

    public InventoryDto toInventoryDto(InventoryDocument document) {
        if (document == null) {
            return null;
        }
        InventoryDto dto = new InventoryDto();
        dto.setTotalQuantity(document.getTotalQuantity());
        dto.setAvailableQuantity(document.getAvailableQuantity());
        dto.setReservedQuantity(document.getReservedQuantity());
        dto.setReorderLevel(document.getReorderLevel());
        dto.setWarehouseCode(document.getWarehouseCode());
        dto.setInStock(document.getInStock());
        dto.setLastRestockedAt(document.getLastRestockedAt());
        return dto;
    }

    public InventoryDocument toInventoryDocument(InventoryDto dto) {
        if (dto == null) {
            return null;
        }
        InventoryDocument document = new InventoryDocument();
        document.setTotalQuantity(dto.getTotalQuantity());
        document.setAvailableQuantity(dto.getAvailableQuantity());
        document.setReservedQuantity(dto.getReservedQuantity());
        document.setReorderLevel(dto.getReorderLevel());
        document.setWarehouseCode(dto.getWarehouseCode());
        document.setInStock(dto.getInStock());
        document.setLastRestockedAt(dto.getLastRestockedAt());
        return document;
    }

    public AccountDto toAccountDto(CustomerAccount account) {
        AccountDto dto = new AccountDto();
        dto.setId(account.getId());
        dto.setUsername(account.getUsername());
        dto.setFullName(account.getFullName());
        dto.setEmail(account.getEmail());
        dto.setPhoneNumber(account.getPhoneNumber());
        dto.setStatus(account.getStatus());
        dto.setMembershipLevel(account.getMembershipLevel());
        dto.setCreatedAt(account.getCreatedAt());
        dto.setUpdatedAt(account.getUpdatedAt());
        dto.setAddresses(account.getAddresses().stream().map(this::toAddressDto).collect(Collectors.toList()));
        dto.setPaymentMethods(account.getPaymentMethods().stream().map(this::toPaymentMethodDto).collect(Collectors.toList()));
        return dto;
    }

    public AddressDto toAddressDto(CustomerAddress address) {
        AddressDto dto = new AddressDto();
        dto.setId(address.getId());
        dto.setLabel(address.getLabel());
        dto.setRecipientName(address.getRecipientName());
        dto.setAddressLine1(address.getAddressLine1());
        dto.setAddressLine2(address.getAddressLine2());
        dto.setCity(address.getCity());
        dto.setState(address.getState());
        dto.setPostalCode(address.getPostalCode());
        dto.setCountry(address.getCountry());
        dto.setAddressType(address.getAddressType());
        dto.setDefaultAddress(address.isDefaultAddress());
        return dto;
    }

    public StoredPaymentMethodDto toPaymentMethodDto(StoredPaymentMethod method) {
        StoredPaymentMethodDto dto = new StoredPaymentMethodDto();
        dto.setId(method.getId());
        dto.setPaymentMethodType(method.getPaymentMethodType());
        dto.setProvider(method.getProvider());
        dto.setAccountToken(method.getAccountToken());
        dto.setMaskedNumber(method.getMaskedNumber());
        dto.setCardholderName(method.getCardholderName());
        dto.setExpiryMonth(method.getExpiryMonth());
        dto.setExpiryYear(method.getExpiryYear());
        dto.setDefaultMethod(method.isDefaultMethod());
        dto.setActive(method.isActive());
        return dto;
    }

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
        dto.setStatus(order.getStatus());
        dto.setStatusReason(order.getStatusReason());
        dto.setCurrencyCode(order.getCurrencyCode());
        dto.setSubtotalAmount(order.getSubtotalAmount());
        dto.setTaxAmount(order.getTaxAmount());
        dto.setShippingAmount(order.getShippingAmount());
        dto.setDiscountAmount(order.getDiscountAmount());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setShippingAddress(toAddressSnapshotDto(order.getShippingAddress()));
        dto.setBillingAddress(toAddressSnapshotDto(order.getBillingAddress()));
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

    public OrderLineItemDto toOrderLineItemDto(OrderLineItem item) {
        OrderLineItemDto dto = new OrderLineItemDto();
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

    public AddressSnapshotDto toAddressSnapshotDto(AddressSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        AddressSnapshotDto dto = new AddressSnapshotDto();
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
