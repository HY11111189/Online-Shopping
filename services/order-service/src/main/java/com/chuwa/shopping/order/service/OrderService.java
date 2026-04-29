package com.chuwa.shopping.order.service;

import com.chuwa.shopping.order.dto.OrderCancelRequestDto;
import com.chuwa.shopping.order.dto.OrderCreateRequestDto;
import com.chuwa.shopping.dto.order.OrderPaymentSyncRequestDto;
import com.chuwa.shopping.order.dto.OrderUpdateRequestDto;
import com.chuwa.shopping.dto.order.OrderDto;

import java.util.List;

public interface OrderService {

    OrderDto createOrder(OrderCreateRequestDto requestDto);

    OrderDto updateOrder(String orderNumber, OrderUpdateRequestDto requestDto);

    OrderDto cancelOrder(String orderNumber, OrderCancelRequestDto requestDto);

    OrderDto getOrder(String orderNumber);

    List<OrderDto> getOrdersByCustomer(Long customerId);

    OrderDto syncPaymentStatus(String orderNumber, OrderPaymentSyncRequestDto requestDto);
}
