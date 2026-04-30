package com.chuwa.shopping.order.service.impl;

import com.chuwa.shopping.shared.client.ItemServiceClient;
import com.chuwa.shopping.order.dao.ShoppingOrderRepository;
import com.chuwa.shopping.exception.ShoppingResourceNotFoundException;
import com.chuwa.shopping.item.dto.ItemDto;
import com.chuwa.shopping.order.entity.OrderKey;
import com.chuwa.shopping.order.entity.OrderLineItem;
import com.chuwa.shopping.order.entity.OrderStatus;
import com.chuwa.shopping.order.entity.ShoppingOrder;
import com.chuwa.shopping.order.dto.OrderCancelRequestDto;
import com.chuwa.shopping.order.dto.OrderCreateRequestDto;
import com.chuwa.shopping.order.dto.OrderDto;
import com.chuwa.shopping.order.dto.OrderLineItemDto;
import com.chuwa.shopping.order.dto.OrderPaymentSyncRequestDto;
import com.chuwa.shopping.order.dto.OrderUpdateRequestDto;
import com.chuwa.shopping.order.service.OrderService;
import com.chuwa.shopping.payment.entity.PaymentStatus;
import com.chuwa.shopping.shared.mapper.ShoppingMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private final ShoppingOrderRepository shoppingOrderRepository;
    private final ShoppingMapper shoppingMapper;
    private final ItemServiceClient itemServiceClient;

    public OrderServiceImpl(ShoppingOrderRepository shoppingOrderRepository,
                            ShoppingMapper shoppingMapper,
                            ItemServiceClient itemServiceClient) {
        this.shoppingOrderRepository = shoppingOrderRepository;
        this.shoppingMapper = shoppingMapper;
        this.itemServiceClient = itemServiceClient;
    }

    @Override
    public OrderDto createOrder(OrderCreateRequestDto requestDto) {
        ShoppingOrder order = new ShoppingOrder();
        OrderKey key = new OrderKey();
        key.setCustomerId(requestDto.getCustomerId());
        key.setCreatedAt(Instant.now());
        key.setOrderId(UUID.randomUUID());
        order.setKey(key);
        order.setOrderNumber("ORD-" + key.getOrderId().toString().substring(0, 8).toUpperCase());
        order.setStatus(OrderStatus.CREATED);
        order.setCurrencyCode(requestDto.getCurrencyCode());
        order.setTaxAmount(defaultMoney(requestDto.getTaxAmount()));
        order.setShippingAmount(defaultMoney(requestDto.getShippingAmount()));
        order.setDiscountAmount(defaultMoney(requestDto.getDiscountAmount()));
        order.setShippingAddress(shoppingMapper.toAddressSnapshot(requestDto.getShippingAddress()));
        order.setBillingAddress(shoppingMapper.toAddressSnapshot(requestDto.getBillingAddress()));
        order.setItems(materializeLineItems(requestDto.getItems()));
        order.setCreateRequestId(requestDto.getCreateRequestId());
        order.setVersion(1);
        order.setUpdatedAt(Instant.now());
        recalculateTotals(order);
        return shoppingMapper.toOrderDto(shoppingOrderRepository.save(order));
    }

    @Override
    public OrderDto updateOrder(String orderNumber, OrderUpdateRequestDto requestDto) {
        ShoppingOrder order = getOrderEntity(orderNumber);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled orders cannot be updated");
        }
        order.setCurrencyCode(requestDto.getCurrencyCode());
        order.setTaxAmount(defaultMoney(requestDto.getTaxAmount()));
        order.setShippingAmount(defaultMoney(requestDto.getShippingAmount()));
        order.setDiscountAmount(defaultMoney(requestDto.getDiscountAmount()));
        order.setShippingAddress(shoppingMapper.toAddressSnapshot(requestDto.getShippingAddress()));
        order.setBillingAddress(shoppingMapper.toAddressSnapshot(requestDto.getBillingAddress()));
        order.setItems(materializeLineItems(requestDto.getItems()));
        order.setLastUpdateRequestId(requestDto.getUpdateRequestId());
        order.setStatusReason(requestDto.getStatusReason());
        order.setStatus(OrderStatus.CONFIRMED);
        order.setVersion(order.getVersion() + 1);
        order.setUpdatedAt(Instant.now());
        recalculateTotals(order);
        return shoppingMapper.toOrderDto(shoppingOrderRepository.save(order));
    }

    @Override
    public OrderDto cancelOrder(String orderNumber, OrderCancelRequestDto requestDto) {
        ShoppingOrder order = getOrderEntity(orderNumber);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelRequestId(requestDto.getCancelRequestId());
        order.setStatusReason(requestDto.getStatusReason());
        order.setCancelledAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        order.setVersion(order.getVersion() + 1);
        return shoppingMapper.toOrderDto(shoppingOrderRepository.save(order));
    }

    @Override
    public OrderDto getOrder(String orderNumber) {
        return shoppingMapper.toOrderDto(getOrderEntity(orderNumber));
    }

    @Override
    public List<OrderDto> getOrdersByCustomer(Long customerId) {
        return shoppingOrderRepository.findByKeyCustomerId(customerId)
                .stream()
                .map(shoppingMapper::toOrderDto)
                .collect(Collectors.toList());
    }

    @Override
    public OrderDto syncPaymentStatus(String orderNumber, OrderPaymentSyncRequestDto requestDto) {
        ShoppingOrder order = getOrderEntity(orderNumber);
        order.setPaymentReference(requestDto.getPaymentReference());
        order.setStatusReason(requestDto.getStatusReason());
        order.setUpdatedAt(Instant.now());
        order.setVersion(order.getVersion() + 1);

        if (requestDto.getPaymentStatus() == PaymentStatus.CAPTURED) {
            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(Instant.now());
        }

        if (requestDto.getPaymentStatus() == PaymentStatus.REFUNDED) {
            order.setStatusReason(requestDto.getStatusReason() == null ? "Payment refunded" : requestDto.getStatusReason());
        }

        return shoppingMapper.toOrderDto(shoppingOrderRepository.save(order));
    }

    private ShoppingOrder getOrderEntity(String orderNumber) {
        return shoppingOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ShoppingResourceNotFoundException("ShoppingOrder", "orderNumber", orderNumber));
    }

    private List<OrderLineItem> materializeLineItems(List<OrderLineItemDto> items) {
        List<OrderLineItem> lineItems = shoppingMapper.toOrderLineItems(items == null ? Collections.emptyList() : items);
        for (OrderLineItem item : lineItems) {
            ItemDto catalogItem = itemServiceClient.getItemBySku(item.getSku());
            item.setItemId(catalogItem.getId() != null ? catalogItem.getId() : item.getItemId());
            item.setSku(catalogItem.getSku());
            item.setItemName(catalogItem.getItemName());
            item.setUpc(catalogItem.getUpc());
            item.setUnitPrice(catalogItem.getUnitPrice());
            if (item.getLineTotal() == null && item.getUnitPrice() != null && item.getQuantity() != null) {
                item.setLineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }
        return lineItems;
    }

    private void recalculateTotals(ShoppingOrder order) {
        BigDecimal subtotal = order.getItems().stream()
                .map(OrderLineItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setSubtotalAmount(subtotal);
        order.setTotalAmount(subtotal
                .add(defaultMoney(order.getTaxAmount()))
                .add(defaultMoney(order.getShippingAmount()))
                .subtract(defaultMoney(order.getDiscountAmount())));
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
