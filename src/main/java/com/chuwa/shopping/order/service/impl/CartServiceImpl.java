package com.chuwa.shopping.order.service.impl;

import com.chuwa.shopping.shared.client.ItemServiceClient;
import com.chuwa.shopping.item.dto.ItemDto;
import com.chuwa.shopping.order.dao.ShoppingCartRepository;
import com.chuwa.shopping.order.entity.CartItem;
import com.chuwa.shopping.order.entity.CartStatus;
import com.chuwa.shopping.order.entity.ShoppingCart;
import com.chuwa.shopping.order.dto.CartDto;
import com.chuwa.shopping.order.dto.CartItemDto;
import com.chuwa.shopping.order.service.CartService;
import com.chuwa.shopping.shared.mapper.ShoppingMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@Service
public class CartServiceImpl implements CartService {

    private final ShoppingCartRepository shoppingCartRepository;
    private final ShoppingMapper shoppingMapper;
    private final ItemServiceClient itemServiceClient;

    public CartServiceImpl(ShoppingCartRepository shoppingCartRepository,
                           ShoppingMapper shoppingMapper,
                           ItemServiceClient itemServiceClient) {
        this.shoppingCartRepository = shoppingCartRepository;
        this.shoppingMapper = shoppingMapper;
        this.itemServiceClient = itemServiceClient;
    }

    @Override
    public CartDto getCart(Long customerId) {
        return shoppingMapper.toCartDto(getOrCreateCart(customerId));
    }

    @Override
    public CartDto addItem(Long customerId, CartItemDto itemDto) {
        ShoppingCart cart = getOrCreateCart(customerId);
        ensureItems(cart);
        CartItemDto authoritativeItem = materializeCartItem(itemDto);
        cart.getItems().removeIf(existing -> existing.getItemId().equals(authoritativeItem.getItemId()));
        cart.getItems().add(shoppingMapper.toCartItem(authoritativeItem));
        cart.setUpdatedAt(Instant.now());
        return shoppingMapper.toCartDto(shoppingCartRepository.save(cart));
    }

    @Override
    public CartDto updateItem(Long customerId, String itemId, CartItemDto itemDto) {
        ShoppingCart cart = getOrCreateCart(customerId);
        ensureItems(cart);
        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getItemId().equals(itemId))
                .findFirst();
        CartItem cartItem = existingItem.orElseGet(() -> {
            CartItem newItem = new CartItem();
            cart.getItems().add(newItem);
            return newItem;
        });
        CartItemDto authoritativeItem = materializeCartItem(itemDto);
        cartItem.setItemId(itemId);
        cartItem.setSku(authoritativeItem.getSku());
        cartItem.setItemName(authoritativeItem.getItemName());
        cartItem.setUpc(authoritativeItem.getUpc());
        cartItem.setQuantity(authoritativeItem.getQuantity());
        cartItem.setUnitPrice(authoritativeItem.getUnitPrice());
        cart.setUpdatedAt(Instant.now());
        return shoppingMapper.toCartDto(shoppingCartRepository.save(cart));
    }

    @Override
    public CartDto removeItem(Long customerId, String itemId) {
        ShoppingCart cart = getOrCreateCart(customerId);
        ensureItems(cart);
        cart.getItems().removeIf(item -> item.getItemId().equals(itemId));
        cart.setUpdatedAt(Instant.now());
        return shoppingMapper.toCartDto(shoppingCartRepository.save(cart));
    }

    @Override
    public CartDto checkout(Long customerId) {
        ShoppingCart cart = getOrCreateCart(customerId);
        cart.setStatus(CartStatus.CHECKED_OUT);
        cart.setCheckedOutAt(Instant.now());
        cart.setUpdatedAt(Instant.now());
        return shoppingMapper.toCartDto(shoppingCartRepository.save(cart));
    }

    private ShoppingCart getOrCreateCart(Long customerId) {
        ShoppingCart cart = shoppingCartRepository.findByCustomerId(customerId).orElseGet(() -> {
            ShoppingCart newCart = new ShoppingCart();
            newCart.setCustomerId(customerId);
            newCart.setCartId("CART-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            newCart.setStatus(CartStatus.ACTIVE);
            newCart.setItems(new ArrayList<>());
            newCart.setCreatedAt(Instant.now());
            newCart.setUpdatedAt(Instant.now());
            return shoppingCartRepository.save(newCart);
        });
        ensureItems(cart);
        return cart;
    }

    private void ensureItems(ShoppingCart cart) {
        if (cart.getItems() == null) {
            cart.setItems(new ArrayList<>());
        }
    }

    private CartItemDto materializeCartItem(CartItemDto itemDto) {
        ItemDto catalogItem = itemServiceClient.getItemBySku(itemDto.getSku());
        Integer requestedQuantity = itemDto.getQuantity() == null ? 1 : itemDto.getQuantity();
        Integer availableQuantity = catalogItem.getInventory() == null ? null : catalogItem.getInventory().getAvailableQuantity();

        if (availableQuantity != null && availableQuantity < requestedQuantity) {
            throw new IllegalStateException("Requested quantity exceeds available inventory");
        }

        if (catalogItem.getInventory() != null && Boolean.FALSE.equals(catalogItem.getInventory().getInStock())) {
            throw new IllegalStateException("Item is out of stock");
        }

        CartItemDto authoritative = new CartItemDto();
        authoritative.setItemId(catalogItem.getId() != null ? catalogItem.getId() : catalogItem.getSku());
        authoritative.setSku(catalogItem.getSku());
        authoritative.setItemName(catalogItem.getItemName());
        authoritative.setUpc(catalogItem.getUpc());
        authoritative.setQuantity(requestedQuantity);
        authoritative.setUnitPrice(catalogItem.getUnitPrice());
        return authoritative;
    }
}
