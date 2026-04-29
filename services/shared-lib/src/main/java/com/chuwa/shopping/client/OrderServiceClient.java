package com.chuwa.shopping.client;

import com.chuwa.shopping.dto.order.OrderDto;
import com.chuwa.shopping.dto.order.OrderPaymentSyncRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "order-service-client", url = "${shopping.services.order.base-url}")
public interface OrderServiceClient {

    @GetMapping("/internal/api/v1/shopping/orders/{orderNumber}")
    OrderDto getOrder(@PathVariable("orderNumber") String orderNumber);

    @PostMapping("/internal/api/v1/shopping/orders/{orderNumber}/payment")
    OrderDto syncPayment(@PathVariable("orderNumber") String orderNumber,
                         @RequestBody OrderPaymentSyncRequestDto requestDto);
}
