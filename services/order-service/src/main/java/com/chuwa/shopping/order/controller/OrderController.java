package com.chuwa.shopping.order.controller;

import com.chuwa.shopping.order.dto.OrderCancelRequestDto;
import com.chuwa.shopping.order.dto.OrderCreateRequestDto;
import com.chuwa.shopping.order.dto.OrderUpdateRequestDto;
import com.chuwa.shopping.order.service.OrderService;
import com.chuwa.shopping.dto.order.OrderDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shopping/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderDto> createOrder(@RequestBody OrderCreateRequestDto requestDto) {
        return new ResponseEntity<>(orderService.createOrder(requestDto), HttpStatus.CREATED);
    }

    @PostMapping("/{orderNumber}/place")
    public ResponseEntity<OrderDto> placeOrder(@PathVariable String orderNumber) {
        return ResponseEntity.ok(orderService.placeOrder(orderNumber));
    }

    @PutMapping("/{orderNumber}")
    public ResponseEntity<OrderDto> updateOrder(@PathVariable String orderNumber, @RequestBody OrderUpdateRequestDto requestDto) {
        return ResponseEntity.ok(orderService.updateOrder(orderNumber, requestDto));
    }

    @PostMapping("/{orderNumber}/cancel")
    public ResponseEntity<OrderDto> cancelOrder(@PathVariable String orderNumber, @RequestBody OrderCancelRequestDto requestDto) {
        return ResponseEntity.ok(orderService.cancelOrder(orderNumber, requestDto));
    }

    @GetMapping("/{orderNumber}")
    public ResponseEntity<OrderDto> getOrder(@PathVariable String orderNumber) {
        return ResponseEntity.ok(orderService.getOrder(orderNumber));
    }

    @GetMapping("/customers/{customerId}")
    public ResponseEntity<List<OrderDto>> getOrdersByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(orderService.getOrdersByCustomer(customerId));
    }
}
