package com.chuwa.shopping.order.service;

import com.chuwa.shopping.order.dto.CartDto;
import com.chuwa.shopping.order.dto.CartItemDto;

public interface CartService {

    CartDto getCart(Long customerId);

    CartDto addItem(Long customerId, CartItemDto itemDto);

    CartDto updateItem(Long customerId, String itemId, CartItemDto itemDto);

    CartDto removeItem(Long customerId, String itemId);

    CartDto checkout(Long customerId);
}
