package com.chuwa.shopping.order.service.impl;

import com.chuwa.shopping.client.AccountServiceClient;
import com.chuwa.shopping.client.PaymentServiceClient;
import com.chuwa.shopping.exception.ShoppingResourceNotFoundException;
import com.chuwa.shopping.order.dao.OrderNumberLookupRepository;
import com.chuwa.shopping.client.ItemServiceClient;
import com.chuwa.shopping.dto.item.InventoryAdjustmentRequestDto;
import com.chuwa.shopping.dto.item.InventoryAdjustmentType;
import com.chuwa.shopping.dto.item.ItemDto;
import com.chuwa.shopping.dto.account.AccountProfileDto;
import com.chuwa.shopping.dto.order.OrderDto;
import com.chuwa.shopping.dto.order.OrderLineItemDto;
import com.chuwa.shopping.dto.order.OrderPaymentSyncRequestDto;
import com.chuwa.shopping.dto.order.OrderStatus;
import com.chuwa.shopping.dto.payment.PaymentProcessingResultDto;
import com.chuwa.shopping.dto.payment.PaymentRequestDto;
import com.chuwa.shopping.dto.payment.PaymentMethod;
import com.chuwa.shopping.dto.payment.PaymentStatus;
import com.chuwa.shopping.order.dao.ShoppingOrderRepository;
import com.chuwa.shopping.order.dto.OrderCancelRequestDto;
import com.chuwa.shopping.order.dto.OrderCreateRequestDto;
import com.chuwa.shopping.order.dto.OrderUpdateRequestDto;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);
    private static final long CANCEL_WINDOW_HOURS = 24;
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("35.00");
    private static final BigDecimal STANDARD_SHIPPING_FEE = new BigDecimal("6.00");

    private final ShoppingOrderRepository shoppingOrderRepository;
    private final OrderNumberLookupRepository orderNumberLookupRepository;
    private final OrderMapper orderMapper;
    private final ItemServiceClient itemServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final PaymentServiceClient paymentServiceClient;

    public OrderServiceImpl(ShoppingOrderRepository shoppingOrderRepository,
                            OrderNumberLookupRepository orderNumberLookupRepository,
                            OrderMapper orderMapper,
                            ItemServiceClient itemServiceClient,
                            AccountServiceClient accountServiceClient,
                            PaymentServiceClient paymentServiceClient) {
        this.shoppingOrderRepository = shoppingOrderRepository;
        this.orderNumberLookupRepository = orderNumberLookupRepository;
        this.orderMapper = orderMapper;
        this.itemServiceClient = itemServiceClient;
        this.accountServiceClient = accountServiceClient;
        this.paymentServiceClient = paymentServiceClient;
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
        order.setShippingAddress(orderMapper.toAddressSnapshot(requestDto.getShippingAddress()));
        order.setBillingAddress(orderMapper.toAddressSnapshot(requestDto.getBillingAddress()));
        order.setPaymentMethod(requestDto.getPaymentMethod() == null ? PaymentMethod.CREDIT_CARD : requestDto.getPaymentMethod());
        order.setItems(materializeLineItems(requestDto.getItems()));
        order.setCreateRequestId(requestDto.getCreateRequestId());
        order.setVersion(1);
        order.setUpdatedAt(Instant.now());
        applyPricing(order, requestDto.getCustomerId());
        recalculateTotals(order);
        ShoppingOrder saved = shoppingOrderRepository.save(order);
        orderNumberLookupRepository.save(toOrderNumberLookup(saved));
        return orderMapper.toOrderDto(saved);
    }

    @Override
    public OrderDto placeOrder(String orderNumber) {
        ShoppingOrder saved = getOrderEntity(orderNumber);
        if (saved.getStatus() != OrderStatus.CREATED) {
            throw new IllegalStateException("Only draft orders can be placed");
        }

        // Reserve stock first. If this fails, payment is never attempted.
        try {
            adjustInventory(saved.getOrderNumber(), saved.getItems(), "checkout-" + saved.getOrderNumber(), InventoryAdjustmentType.PURCHASE);
        } catch (RuntimeException inventoryFailure) {
            ShoppingOrder failedOrder = markCheckoutFailed(saved, "Inventory reservation failed");
            return orderMapper.toOrderDto(failedOrder);
        }

        // Then capture payment. If this fails after stock is reserved, restock immediately.
        PaymentProcessingResultDto payment;
        try {
            PaymentRequestDto paymentRequest = new PaymentRequestDto();
            paymentRequest.setOrderId(saved.getOrderNumber());
            paymentRequest.setCustomerId(saved.getKey().getCustomerId());
            paymentRequest.setPaymentMethod(saved.getPaymentMethod() == null ? PaymentMethod.CREDIT_CARD : saved.getPaymentMethod());
            paymentRequest.setAmount(defaultMoney(saved.getTotalAmount()));
            paymentRequest.setCurrencyCode(firstNonBlank(saved.getCurrencyCode(), "USD"));
            paymentRequest.setIdempotencyKey(firstNonBlank(saved.getCreateRequestId(), saved.getOrderNumber() + ":PAY"));
            paymentRequest.setExternalReference(firstNonBlank(saved.getCreateRequestId(), saved.getOrderNumber()));
            payment = paymentServiceClient.processPayment(paymentRequest);
        } catch (RuntimeException paymentFailure) {
            adjustInventory(saved.getOrderNumber(), saved.getItems(), "checkout-" + saved.getOrderNumber(), InventoryAdjustmentType.RESTOCK);
            ShoppingOrder failedOrder = markCheckoutFailed(saved, "Payment processing failed");
            return orderMapper.toOrderDto(failedOrder);
        }

        OrderDto response = orderMapper.toOrderDto(saved);
        response.setPaymentReference(payment == null || payment.getPaymentNumber() == null
                ? saved.getOrderNumber()
                : payment.getPaymentNumber());
        return response;
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
        order.setShippingAddress(orderMapper.toAddressSnapshot(requestDto.getShippingAddress()));
        order.setBillingAddress(orderMapper.toAddressSnapshot(requestDto.getBillingAddress()));
        order.setItems(updatedItems);
        order.setLastUpdateRequestId(requestDto.getUpdateRequestId());
        order.setStatusReason(requestDto.getStatusReason());
        order.setStatus(OrderStatus.CREATED);
        order.setVersion(order.getVersion() + 1);
        order.setUpdatedAt(Instant.now());
        applyPricing(order, order.getKey().getCustomerId());
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
            // Kafka can redeliver, so repeated payment events become a no-op.
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
            if (order.getStatus() == OrderStatus.PAID) {
                adjustInventory(order.getOrderNumber(),
                        order.getItems(),
                        "payment-refund-" + requestDto.getPaymentReference(),
                        InventoryAdjustmentType.RESTOCK);
            }
            order.setStatus(OrderStatus.REFUNDED);
            order.setStatusReason(requestDto.getStatusReason() == null ? "Payment refunded" : requestDto.getStatusReason());
        }

        if (requestDto.getPaymentStatus() == PaymentStatus.CANCELLED) {
            if (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.REFUNDED) {
                adjustInventory(order.getOrderNumber(),
                        order.getItems(),
                        "payment-cancel-" + requestDto.getPaymentReference(),
                        InventoryAdjustmentType.RESTOCK);
            }
            order.setStatus(OrderStatus.CANCELLED);
            order.setStatusReason(requestDto.getStatusReason() == null ? "Payment canceled" : requestDto.getStatusReason());
            order.setCancelledAt(Instant.now());
        }

        if (requestDto.getPaymentStatus() == PaymentStatus.FAILED) {
            if (order.getStatus() != OrderStatus.FAILED
                    && order.getStatus() != OrderStatus.CANCELLED
                    && order.getStatus() != OrderStatus.REFUNDED) {
                adjustInventory(order.getOrderNumber(),
                        order.getItems(),
                        "payment-failure-" + requestDto.getPaymentReference(),
                        InventoryAdjustmentType.RESTOCK);
            }
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

            BigDecimal originalPrice = originalUnitPrice(catalogItem);
            if (originalPrice != null && item.getQuantity() != null) {
                item.setLineTotal(originalPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }
        return lineItems;
    }

    private void applyPricing(ShoppingOrder order, Long customerId) {
        // Order pricing is computed from the catalog snapshot plus account membership.
        AccountProfileDto account = loadAccount(customerId);
        // Shipping threshold is based on the discounted price (what the customer actually pays),
        // not the original price — so we compute it from unitPrice × quantity, not lineTotal.
        BigDecimal discountedSubtotal = order.getItems().stream()
                .filter(item -> item.getUnitPrice() != null && item.getQuantity() != null)
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setShippingAmount(calculateShippingAmount(account, order.getItems(), discountedSubtotal));
        order.setDiscountAmount(calculateDiscountAmount(order.getItems()));
    }

    private AccountProfileDto loadAccount(Long customerId) {
        if (customerId == null) {
            return null;
        }
        try {
            return accountServiceClient.getAccount(customerId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private BigDecimal calculateShippingAmount(AccountProfileDto account, List<OrderLineItem> items, BigDecimal subtotal) {
        boolean hasShippingItems = (items == null ? Collections.<OrderLineItem>emptyList() : items)
                .stream()
                .anyMatch(item -> item.getItemId() != null && item.getItemId().endsWith("::SHIPPING"));
        if (!hasShippingItems) {
            return BigDecimal.ZERO;
        }
        boolean premium = account != null && "PREMIUM".equalsIgnoreCase(account.getMembershipLevel());
        if (premium || subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0) {
            return BigDecimal.ZERO;
        }
        return STANDARD_SHIPPING_FEE;
    }

    private BigDecimal calculateDiscountAmount(List<OrderLineItem> items) {
        BigDecimal discount = BigDecimal.ZERO;
        for (OrderLineItem item : items == null ? Collections.<OrderLineItem>emptyList() : items) {
            if (item.getSku() == null || item.getQuantity() == null || item.getUnitPrice() == null) {
                continue;
            }
            ItemDto catalogItem = itemServiceClient.getItemBySku(item.getSku());
            BigDecimal originalUnitPrice = originalUnitPrice(catalogItem);
            if (originalUnitPrice.compareTo(item.getUnitPrice()) > 0) {
                discount = discount.add(originalUnitPrice.subtract(item.getUnitPrice())
                        .multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }
        return discount;
    }

    private BigDecimal originalUnitPrice(ItemDto item) {
        if (item == null) {
            return BigDecimal.ZERO;
        }
        if (item.getListPrice() != null && item.getListPrice().compareTo(defaultMoney(item.getUnitPrice())) > 0) {
            return item.getListPrice();
        }
        if (item.getUnitPrice() != null && item.getDiscountPercent() != null && item.getDiscountPercent() > 0) {
            BigDecimal divisor = BigDecimal.ONE.subtract(BigDecimal.valueOf(item.getDiscountPercent()).movePointLeft(2));
            if (divisor.compareTo(BigDecimal.ZERO) > 0) {
                return item.getUnitPrice().divide(divisor, 2, java.math.RoundingMode.HALF_UP);
            }
        }
        return defaultMoney(item.getUnitPrice());
    }

    private void adjustInventory(String orderNumber,
                                 List<OrderLineItem> items,
                                 String operationPrefix,
                                 InventoryAdjustmentType adjustmentType) {
        // Inventory changes are sent item-by-item with unique operation IDs for idempotency.
        String prefix = safeOperationId(operationPrefix, adjustmentType.name().toLowerCase() + "-" + orderNumber);
        List<OrderLineItem> changedItems = new ArrayList<>();
        try {
            for (OrderLineItem item : deduplicateLineItems(items)) {
                InventoryAdjustmentRequestDto requestDto = new InventoryAdjustmentRequestDto();
                requestDto.setAdjustmentType(adjustmentType);
                requestDto.setQuantity(item.getQuantity());
                requestDto.setReference(orderNumber);
                requestDto.setOperationId(prefix + "-" + item.getSku());
                itemServiceClient.adjustInventory(item.getSku(), requestDto);
                changedItems.add(item);
            }
        } catch (RuntimeException inventoryFailure) {
            if (adjustmentType == InventoryAdjustmentType.PURCHASE && !changedItems.isEmpty()) {
                rollbackChangedItems(orderNumber, prefix, changedItems);
            }
            throw inventoryFailure;
        }
    }

    private void rollbackChangedItems(String orderNumber, String operationPrefix, List<OrderLineItem> changedItems) {
        // If a purchase fails mid-way, only the already adjusted items are restocked.
        for (OrderLineItem item : changedItems) {
            InventoryAdjustmentRequestDto requestDto = new InventoryAdjustmentRequestDto();
            requestDto.setAdjustmentType(InventoryAdjustmentType.RESTOCK);
            requestDto.setQuantity(item.getQuantity());
            requestDto.setReference(orderNumber);
            requestDto.setOperationId(operationPrefix + "-rollback-" + item.getSku());
            itemServiceClient.adjustInventory(item.getSku(), requestDto);
        }
    }

    private ShoppingOrder markCheckoutFailed(ShoppingOrder order, String statusReason) {
        // Persist a failed checkout when the user-facing place-order step cannot finish.
        order.setStatus(OrderStatus.FAILED);
        order.setStatusReason(firstNonBlank(statusReason, "Checkout failed"));
        order.setUpdatedAt(Instant.now());
        order.setVersion(order.getVersion() + 1);
        return shoppingOrderRepository.save(order);
    }

    private String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback == null ? "" : fallback;
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
        // Track processed payment events so Kafka redelivery does not double-apply state changes.
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
