package com.chuwa.shopping.order.service.impl;

import com.chuwa.shopping.shared.client.ItemServiceClient;
import com.chuwa.shopping.item.dto.InventoryDto;
import com.chuwa.shopping.item.dto.ItemDto;
import com.chuwa.shopping.order.dao.ShoppingCartRepository;
import com.chuwa.shopping.order.dto.CartDto;
import com.chuwa.shopping.order.dto.CartItemDto;
import com.chuwa.shopping.order.entity.CartItem;
import com.chuwa.shopping.order.entity.CartStatus;
import com.chuwa.shopping.order.entity.ShoppingCart;
import com.chuwa.shopping.shared.mapper.ShoppingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private ShoppingCartRepository shoppingCartRepository;

    @Mock
    private ItemServiceClient itemServiceClient;

    private CartServiceImpl cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartServiceImpl(shoppingCartRepository, new ShoppingMapper(), itemServiceClient);
    }

    @Test
    void addItemShouldCreateCartWhenCustomerHasNoCart() {
        when(shoppingCartRepository.findByCustomerId(1L)).thenReturn(Optional.empty());
        when(shoppingCartRepository.save(any(ShoppingCart.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemServiceClient.getItemBySku("SKU-1001")).thenReturn(catalogItem());

        CartItemDto item = new CartItemDto();
        item.setItemId("SKU-1001");
        item.setSku("SKU-1001");
        item.setItemName("Coffee Mug");
        item.setUpc("123456789012");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("12.99"));

        CartDto result = cartService.addItem(1L, item);

        ArgumentCaptor<ShoppingCart> captor = ArgumentCaptor.forClass(ShoppingCart.class);
        verify(shoppingCartRepository, times(2)).save(captor.capture());
        ShoppingCart saved = captor.getAllValues().get(1);
        assertEquals(1L, saved.getCustomerId());
        assertEquals(CartStatus.ACTIVE, saved.getStatus());
        assertEquals(1, saved.getItems().size());
        assertNotNull(saved.getCartId());
        assertEquals("SKU-1001", result.getItems().get(0).getItemId());
    }

    @Test
    void updateItemShouldMutateExistingCartItem() {
        ShoppingCart cart = new ShoppingCart();
        cart.setCustomerId(1L);
        cart.setItems(new ArrayList<>());
        CartItem existing = new CartItem();
        existing.setItemId("SKU-1001");
        existing.setQuantity(1);
        cart.getItems().add(existing);

        when(shoppingCartRepository.findByCustomerId(1L)).thenReturn(Optional.of(cart));
        when(shoppingCartRepository.save(any(ShoppingCart.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemServiceClient.getItemBySku("SKU-1001")).thenReturn(catalogItem());

        CartItemDto update = new CartItemDto();
        update.setSku("SKU-1001");
        update.setItemName("Coffee Mug");
        update.setUpc("123456789012");
        update.setQuantity(3);
        update.setUnitPrice(new BigDecimal("12.99"));

        CartDto result = cartService.updateItem(1L, "SKU-1001", update);

        assertEquals(1, cart.getItems().size());
        assertEquals(3, cart.getItems().get(0).getQuantity());
        assertEquals(3, result.getItems().get(0).getQuantity());
    }

    @Test
    void checkoutShouldMarkCartAsCheckedOut() {
        ShoppingCart cart = new ShoppingCart();
        cart.setCustomerId(1L);
        cart.setStatus(CartStatus.ACTIVE);
        cart.setItems(new ArrayList<>());

        when(shoppingCartRepository.findByCustomerId(1L)).thenReturn(Optional.of(cart));
        when(shoppingCartRepository.save(any(ShoppingCart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartDto result = cartService.checkout(1L);

        assertEquals(CartStatus.CHECKED_OUT, cart.getStatus());
        assertNotNull(cart.getCheckedOutAt());
        assertNotNull(cart.getUpdatedAt());
        assertEquals(CartStatus.CHECKED_OUT, result.getStatus());
    }

    private ItemDto catalogItem() {
        ItemDto item = new ItemDto();
        item.setId("SKU-1001");
        item.setSku("SKU-1001");
        item.setItemName("Coffee Mug");
        item.setUpc("123456789012");
        item.setUnitPrice(new BigDecimal("12.99"));
        InventoryDto inventory = new InventoryDto();
        inventory.setAvailableQuantity(10);
        inventory.setInStock(true);
        item.setInventory(inventory);
        return item;
    }
}
