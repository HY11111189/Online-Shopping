package com.chuwa.shopping.order.controller;

import com.chuwa.shopping.order.dto.OrderDto;
import com.chuwa.shopping.order.dto.OrderPaymentSyncRequestDto;
import com.chuwa.shopping.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/api/v1/shopping/orders")
public class InternalOrderController {

    private final OrderService orderService;

    public InternalOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{orderNumber}")
    public ResponseEntity<OrderDto> getOrder(@PathVariable String orderNumber) {
        return ResponseEntity.ok(orderService.getOrder(orderNumber));
    }

    @PostMapping("/{orderNumber}/payment")
    public ResponseEntity<OrderDto> syncPayment(@PathVariable String orderNumber,
                                                @RequestBody OrderPaymentSyncRequestDto requestDto) {
        return ResponseEntity.ok(orderService.syncPaymentStatus(orderNumber, requestDto));
    }
}
