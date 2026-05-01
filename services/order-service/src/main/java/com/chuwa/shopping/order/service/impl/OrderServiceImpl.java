package com.chuwa.shopping.order.service.impl;

import com.chuwa.shopping.exception.ShoppingResourceNotFoundException;
import com.chuwa.shopping.order.dao.OrderNumberLookupRepository;
import com.chuwa.shopping.client.ItemServiceClient;
import com.chuwa.shopping.dto.item.InventoryAdjustmentRequestDto;
import com.chuwa.shopping.dto.item.InventoryAdjustmentType;
import com.chuwa.shopping.dto.item.ItemDto;
import com.chuwa.shopping.dto.order.OrderDto;
import com.chuwa.shopping.dto.order.OrderLineItemDto;
import com.chuwa.shopping.dto.order.OrderPaymentSyncRequestDto;
import com.chuwa.shopping.dto.order.OrderStatus;
import com.chuwa.shopping.dto.payment.PaymentStatus;
import com.chuwa.shopping.order.dao.ShoppingOrderRepository;
import com.chuwa.shopping.order.dto.OrderCancelRequestDto;
import com.chuwa.shopping.order.dto.OrderCreateRequestDto;
import com.chuwa.shopping.order.dto.OrderUpdateRequestDto;
import com.chuwa.shopping.order.messaging.OrderPlacedEventPublisher;
import com.chuwa.shopping.order.entity.OrderKey;
import com.chuwa.shopping.order.entity.OrderLineItem;
import com.chuwa.shopping.order.entity.OrderNumberLookup;
import com.chuwa.shopping.order.entity.ShoppingOrder;
import com.chuwa.shopping.order.mapper.OrderMapper;
import com.chuwa.shopping.order.service.OrderService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private static final long CANCEL_WINDOW_HOURS = 24;

    private final ShoppingOrderRepository shoppingOrderRepository;
    private final OrderNumberLookupRepository orderNumberLookupRepository;
    private final OrderMapper orderMapper;
    private final ItemServiceClient itemServiceClient;
    private final OrderPlacedEventPublisher orderPlacedEventPublisher;

    public OrderServiceImpl(ShoppingOrderRepository shoppingOrderRepository,
                            OrderNumberLookupRepository orderNumberLookupRepository,
                            OrderMapper orderMapper,
                            ItemServiceClient itemServiceClient,
                            OrderPlacedEventPublisher orderPlacedEventPublisher) {
        this.shoppingOrderRepository = shoppingOrderRepository;
        this.orderNumberLookupRepository = orderNumberLookupRepository;
        this.orderMapper = orderMapper;
        this.itemServiceClient = itemServiceClient;
        this.orderPlacedEventPublisher = orderPlacedEventPublisher;
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
        order.setShippingAddress(orderMapper.toAddressSnapshot(requestDto.getShippingAddress()));
        order.setBillingAddress(orderMapper.toAddressSnapshot(requestDto.getBillingAddress()));
        order.setItems(materializeLineItems(requestDto.getItems()));
        order.setCreateRequestId(requestDto.getCreateRequestId());
        order.setVersion(1);
        order.setUpdatedAt(Instant.now());
        recalculateTotals(order);
        ShoppingOrder saved = shoppingOrderRepository.save(order);
        orderNumberLookupRepository.save(toOrderNumberLookup(saved));
        OrderDto savedOrder = orderMapper.toOrderDto(saved);
        orderPlacedEventPublisher.publish(savedOrder, requestDto.getPaymentMethod());
        return savedOrder;
    }

    @Override
    public OrderDto updateOrder(String orderNumber, OrderUpdateRequestDto requestDto) {
        ShoppingOrder order = getOrderEntity(orderNumber);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled orders cannot be updated");
        }
        List<OrderLineItem> updatedItems = materializeLineItems(requestDto.getItems());
        order.setCurrencyCode(requestDto.getCurrencyCode());
        order.setTaxAmount(defaultMoney(requestDto.getTaxAmount()));
        order.setShippingAmount(defaultMoney(requestDto.getShippingAmount()));
        order.setDiscountAmount(defaultMoney(requestDto.getDiscountAmount()));
        order.setShippingAddress(orderMapper.toAddressSnapshot(requestDto.getShippingAddress()));
        order.setBillingAddress(orderMapper.toAddressSnapshot(requestDto.getBillingAddress()));
        order.setItems(updatedItems);
        order.setLastUpdateRequestId(requestDto.getUpdateRequestId());
        order.setStatusReason(requestDto.getStatusReason());
        order.setStatus(OrderStatus.CREATED);
        order.setVersion(order.getVersion() + 1);
        order.setUpdatedAt(Instant.now());
        recalculateTotals(order);
        return orderMapper.toOrderDto(shoppingOrderRepository.save(order));
    }

    @Override
    public OrderDto cancelOrder(String orderNumber, OrderCancelRequestDto requestDto) {
        ShoppingOrder order = getOrderEntity(orderNumber);
        Instant cancelDeadline = order.getKey().getCreatedAt().plus(CANCEL_WINDOW_HOURS, ChronoUnit.HOURS);
        if (Instant.now().isAfter(cancelDeadline)) {
            throw new IllegalStateException("Orders cannot be canceled after 1 day");
        }
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new IllegalStateException("Only newly created unpaid orders can be canceled directly");
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelRequestId(requestDto.getCancelRequestId());
        order.setStatusReason(requestDto.getStatusReason());
        order.setCancelledAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        order.setVersion(order.getVersion() + 1);
        return orderMapper.toOrderDto(shoppingOrderRepository.save(order));
    }

    @Override
    public OrderDto getOrder(String orderNumber) {
        return orderMapper.toOrderDto(getOrderEntity(orderNumber));
    }

    @Override
    public List<OrderDto> getOrdersByCustomer(Long customerId) {
        return shoppingOrderRepository.findByKeyCustomerId(customerId)
                .stream()
                .map(orderMapper::toOrderDto)
                .collect(Collectors.toList());
    }

    @Override
    public OrderDto syncPaymentStatus(String orderNumber, OrderPaymentSyncRequestDto requestDto) {
        ShoppingOrder order = getOrderEntity(orderNumber);
        ensureProcessedEventList(order);
        String syncId = safeOperationId(requestDto.getPaymentReference(), orderNumber) + ":" + requestDto.getPaymentStatus().name();
        if (order.getProcessedPaymentEventIds().contains(syncId)) {
            return orderMapper.toOrderDto(order);
        }
        order.setPaymentReference(requestDto.getPaymentReference());
        order.setStatusReason(requestDto.getStatusReason());
        order.setUpdatedAt(Instant.now());
        order.setVersion(order.getVersion() + 1);

        if (requestDto.getPaymentStatus() == PaymentStatus.CAPTURED) {
            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(Instant.now());
        }

        if (requestDto.getPaymentStatus() == PaymentStatus.REFUNDED) {
            OrderStatus nextStatus = requestDto.getOrderStatus() == null ? OrderStatus.REFUNDED : requestDto.getOrderStatus();
            if (order.getStatus() == OrderStatus.PAID) {
                adjustInventory(order.getOrderNumber(),
                        order.getItems(),
                        "payment-refund-" + requestDto.getPaymentReference(),
                        InventoryAdjustmentType.RESTOCK);
            }
            order.setStatus(nextStatus);
            order.setStatusReason(requestDto.getStatusReason() == null ? "Payment refunded" : requestDto.getStatusReason());
            if (nextStatus == OrderStatus.CANCELLED) {
                order.setCancelledAt(Instant.now());
            }
        }

        if (requestDto.getPaymentStatus() == PaymentStatus.CANCELLED) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setStatusReason(requestDto.getStatusReason() == null ? "Payment canceled" : requestDto.getStatusReason());
            order.setCancelledAt(Instant.now());
        }

        if (requestDto.getPaymentStatus() == PaymentStatus.FAILED) {
            order.setStatus(OrderStatus.FAILED);
            order.setStatusReason(requestDto.getStatusReason() == null ? "Payment failed" : requestDto.getStatusReason());
        }

        order.getProcessedPaymentEventIds().add(syncId);

        return orderMapper.toOrderDto(shoppingOrderRepository.save(order));
    }

    private ShoppingOrder getOrderEntity(String orderNumber) {
        OrderNumberLookup lookup = orderNumberLookupRepository.findById(orderNumber)
                .orElseThrow(() -> new ShoppingResourceNotFoundException("ShoppingOrder", "orderNumber", orderNumber));
        OrderKey key = new OrderKey();
        key.setCustomerId(lookup.getCustomerId());
        key.setCreatedAt(lookup.getCreatedAt());
        key.setOrderId(lookup.getOrderId());
        return shoppingOrderRepository.findById(key)
                .orElseThrow(() -> new ShoppingResourceNotFoundException("ShoppingOrder", "orderNumber", orderNumber));
    }

    private List<OrderLineItem> materializeLineItems(List<OrderLineItemDto> items) {
        List<OrderLineItem> lineItems = orderMapper.toOrderLineItems(items == null ? Collections.emptyList() : items);
        for (OrderLineItem item : lineItems) {
            ItemDto catalogItem = itemServiceClient.getItemBySku(item.getSku());
            item.setItemId(resolveLineItemId(item.getItemId(), catalogItem.getSku()));
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

    private void adjustInventory(String orderNumber,
                                 List<OrderLineItem> items,
                                 String operationPrefix,
                                 InventoryAdjustmentType adjustmentType) {
        String prefix = safeOperationId(operationPrefix, adjustmentType.name().toLowerCase() + "-" + orderNumber);
        for (OrderLineItem item : deduplicateLineItems(items)) {
            InventoryAdjustmentRequestDto requestDto = new InventoryAdjustmentRequestDto();
            requestDto.setAdjustmentType(adjustmentType);
            requestDto.setQuantity(item.getQuantity());
            requestDto.setReference(orderNumber);
            requestDto.setOperationId(prefix + "-" + item.getSku());
            itemServiceClient.adjustInventory(item.getSku(), requestDto);
        }
    }

    private List<OrderLineItem> deduplicateLineItems(List<OrderLineItem> items) {
        return (items == null ? Collections.<OrderLineItem>emptyList() : items)
                .stream()
                .filter(item -> item.getSku() != null && item.getQuantity() != null)
                .collect(Collectors.toMap(
                        OrderLineItem::getItemId,
                        OrderServiceImpl::copyInventoryItem,
                        (left, right) -> {
                            left.setQuantity(left.getQuantity() + right.getQuantity());
                            return left;
                        }))
                .values()
                .stream()
                .collect(Collectors.toList());
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

    private String safeOperationId(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void ensureProcessedEventList(ShoppingOrder order) {
        if (order.getProcessedPaymentEventIds() == null) {
            order.setProcessedPaymentEventIds(new ArrayList<>());
        }
    }

    private static OrderLineItem copyInventoryItem(OrderLineItem source) {
        OrderLineItem copy = new OrderLineItem();
        copy.setItemId(source.getItemId());
        copy.setSku(source.getSku());
        copy.setItemName(source.getItemName());
        copy.setUpc(source.getUpc());
        copy.setQuantity(source.getQuantity());
        copy.setUnitPrice(source.getUnitPrice());
        copy.setLineTotal(source.getLineTotal());
        return copy;
    }

    private String resolveLineItemId(String requestItemId, String sku) {
        if (requestItemId != null && requestItemId.contains("::")) {
            return requestItemId;
        }
        return sku;
    }

    private OrderNumberLookup toOrderNumberLookup(ShoppingOrder order) {
        OrderNumberLookup lookup = new OrderNumberLookup();
        lookup.setOrderNumber(order.getOrderNumber());
        lookup.setCustomerId(order.getKey().getCustomerId());
        lookup.setCreatedAt(order.getKey().getCreatedAt());
        lookup.setOrderId(order.getKey().getOrderId());
        return lookup;
    }

    private boolean isInsufficientStockFailure(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("inventory adjustment could not be applied")
                || normalized.contains("requested quantity exceeds available inventory");
    }
}
