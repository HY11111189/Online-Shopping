package com.chuwa.shopping.order.controller;

import com.chuwa.shopping.order.dto.CartDto;
import com.chuwa.shopping.order.dto.CartItemDto;
import com.chuwa.shopping.order.service.CartService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shopping/carts")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<CartDto> getCart(@PathVariable Long customerId) {
        return ResponseEntity.ok(cartService.getCart(customerId));
    }

    @PostMapping("/{customerId}/items")
    public ResponseEntity<CartDto> addItem(@PathVariable Long customerId, @RequestBody CartItemDto itemDto) {
        return ResponseEntity.ok(cartService.addItem(customerId, itemDto));
    }

    @PutMapping("/{customerId}/items/{itemId}")
    public ResponseEntity<CartDto> updateItem(@PathVariable Long customerId,
                                              @PathVariable String itemId,
                                              @RequestBody CartItemDto itemDto) {
        return ResponseEntity.ok(cartService.updateItem(customerId, itemId, itemDto));
    }

    @DeleteMapping("/{customerId}/items/{itemId}")
    public ResponseEntity<CartDto> removeItem(@PathVariable Long customerId, @PathVariable String itemId) {
        return ResponseEntity.ok(cartService.removeItem(customerId, itemId));
    }

    @PostMapping("/{customerId}/checkout")
    public ResponseEntity<CartDto> checkout(@PathVariable Long customerId) {
        return ResponseEntity.ok(cartService.checkout(customerId));
    }
}
