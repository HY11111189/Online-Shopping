package com.chuwa.shopping.payment.controller;

import com.chuwa.shopping.payment.dto.PaymentDto;
import com.chuwa.shopping.payment.service.PaymentService;
import com.chuwa.shopping.dto.payment.PaymentRequestDto;
import com.chuwa.shopping.dto.payment.PaymentProcessingResultDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/api/v1/shopping/payments")
public class InternalPaymentController {

    private final PaymentService paymentService;

    public InternalPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/process")
    public ResponseEntity<PaymentProcessingResultDto> processPayment(@RequestBody PaymentRequestDto requestDto) {
        PaymentDto payment = paymentService.processPayment(requestDto);
        PaymentProcessingResultDto result = new PaymentProcessingResultDto();
        result.setOrderNumber(payment.getOrderId());
        result.setPaymentNumber(payment.getPaymentNumber());
        result.setPaymentStatus(payment.getPaymentStatus());
        result.setStatusReason(payment.getFailureReason() == null ? "Payment captured" : payment.getFailureReason());
        return ResponseEntity.ok(result);
    }
}
