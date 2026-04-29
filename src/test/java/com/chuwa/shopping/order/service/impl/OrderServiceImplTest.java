package com.chuwa.shopping.order.service.impl;

import com.chuwa.shopping.order.entity.OrderKey;
import com.chuwa.shopping.shared.client.ItemServiceClient;
import com.chuwa.shopping.item.dto.ItemDto;
import com.chuwa.shopping.order.dao.ShoppingOrderRepository;
import com.chuwa.shopping.order.dto.AddressSnapshotDto;
import com.chuwa.shopping.order.dto.OrderCancelRequestDto;
import com.chuwa.shopping.order.dto.OrderCreateRequestDto;
import com.chuwa.shopping.order.dto.OrderDto;
import com.chuwa.shopping.order.dto.OrderLineItemDto;
import com.chuwa.shopping.order.dto.OrderUpdateRequestDto;
import com.chuwa.shopping.order.entity.OrderStatus;
import com.chuwa.shopping.order.entity.ShoppingOrder;
import com.chuwa.shopping.shared.mapper.ShoppingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private ShoppingOrderRepository shoppingOrderRepository;

    @Mock
    private ItemServiceClient itemServiceClient;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(shoppingOrderRepository, new ShoppingMapper(), itemServiceClient);
    }

    @Test
    void createOrderShouldCalculateTotalsAndGenerateLineTotals() {
        when(shoppingOrderRepository.save(any(ShoppingOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemServiceClient.getItemBySku("SKU-1001")).thenReturn(catalogItem());

        OrderCreateRequestDto request = new OrderCreateRequestDto();
        request.setCustomerId(1L);
        request.setCurrencyCode("USD");
        request.setTaxAmount(new BigDecimal("1.50"));
        request.setShippingAmount(new BigDecimal("5.00"));
        request.setDiscountAmount(new BigDecimal("0.00"));
        request.setCreateRequestId("req-1");
        request.setShippingAddress(address());
        request.setBillingAddress(address());

        OrderLineItemDto item = new OrderLineItemDto();
        item.setItemId("SKU-1001");
        item.setSku("SKU-1001");
        item.setItemName("Coffee Mug");
        item.setUpc("123456789012");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("12.99"));
        request.setItems(List.of(item));

        OrderDto result = orderService.createOrder(request);

        ArgumentCaptor<ShoppingOrder> captor = ArgumentCaptor.forClass(ShoppingOrder.class);
        verify(shoppingOrderRepository).save(captor.capture());
        ShoppingOrder saved = captor.getValue();
        assertEquals(new BigDecimal("25.98"), saved.getSubtotalAmount());
        assertEquals(new BigDecimal("32.48"), saved.getTotalAmount());
        assertEquals(OrderStatus.CREATED, saved.getStatus());
        assertEquals(new BigDecimal("25.98"), saved.getItems().get(0).getLineTotal());
        assertTrue(result.getOrderNumber().startsWith("ORD-"));
    }

    @Test
    void updateOrderShouldRejectCancelledOrder() {
        ShoppingOrder existing = new ShoppingOrder();
        existing.setStatus(OrderStatus.CANCELLED);
        existing.setVersion(1);
        existing.setKey(orderKey());

        when(shoppingOrderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class,
                () -> orderService.updateOrder("ORD-1", new OrderUpdateRequestDto()));
    }

    @Test
    void cancelOrderShouldUpdateStatusAndVersion() {
        ShoppingOrder existing = new ShoppingOrder();
        existing.setStatus(OrderStatus.CREATED);
        existing.setVersion(2);
        existing.setOrderNumber("ORD-1");
        existing.setKey(orderKey());

        when(shoppingOrderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(existing));
        when(shoppingOrderRepository.save(any(ShoppingOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderCancelRequestDto cancel = new OrderCancelRequestDto();
        cancel.setCancelRequestId("cancel-1");
        cancel.setStatusReason("Customer request");

        OrderDto result = orderService.cancelOrder("ORD-1", cancel);

        assertEquals(OrderStatus.CANCELLED, existing.getStatus());
        assertEquals(3, existing.getVersion());
        assertEquals("cancel-1", existing.getCancelRequestId());
        assertEquals(OrderStatus.CANCELLED, result.getStatus());
    }

    private AddressSnapshotDto address() {
        AddressSnapshotDto dto = new AddressSnapshotDto();
        dto.setRecipientName("Alice Smith");
        dto.setAddressLine1("123 Main St");
        dto.setCity("Chicago");
        dto.setState("IL");
        dto.setPostalCode("60601");
        dto.setCountry("US");
        dto.setPhoneNumber("3125551111");
        return dto;
    }

    private OrderKey orderKey() {
        OrderKey key = new OrderKey();
        key.setCustomerId(1L);
        key.setCreatedAt(Instant.now());
        key.setOrderId(UUID.randomUUID());
        return key;
    }

    private ItemDto catalogItem() {
        ItemDto dto = new ItemDto();
        dto.setId("catalog-SKU-1001");
        dto.setSku("SKU-1001");
        dto.setItemName("Coffee Mug");
        dto.setUpc("123456789012");
        dto.setUnitPrice(new BigDecimal("12.99"));
        dto.setCurrencyCode("USD");
        return dto;
    }
}
