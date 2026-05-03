package com.chuwa.shopping.client;

import com.chuwa.shopping.dto.payment.PaymentProcessingResultDto;
import com.chuwa.shopping.dto.payment.PaymentRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service-client", url = "${shopping.services.payment.base-url}")
public interface PaymentServiceClient {

    @PostMapping("/internal/api/v1/shopping/payments/process")
    PaymentProcessingResultDto processPayment(@RequestBody PaymentRequestDto requestDto);
}
