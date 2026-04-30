package com.chuwa.shopping.payment.controller;

import com.chuwa.shopping.payment.dto.PaymentDto;
import com.chuwa.shopping.payment.dto.PaymentRequestDto;
import com.chuwa.shopping.payment.dto.PaymentUpdateRequestDto;
import com.chuwa.shopping.payment.dto.RefundRequestDto;
import com.chuwa.shopping.payment.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shopping/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentDto> submitPayment(@RequestBody PaymentRequestDto requestDto) {
        return new ResponseEntity<>(paymentService.submitPayment(requestDto), HttpStatus.CREATED);
    }

    @PutMapping("/{paymentNumber}")
    public ResponseEntity<PaymentDto> updatePayment(@PathVariable String paymentNumber,
                                                    @RequestBody PaymentUpdateRequestDto requestDto) {
        return ResponseEntity.ok(paymentService.updatePayment(paymentNumber, requestDto));
    }

    @PostMapping("/{paymentNumber}/refund")
    public ResponseEntity<PaymentDto> refundPayment(@PathVariable String paymentNumber,
                                                    @RequestBody RefundRequestDto requestDto) {
        return ResponseEntity.ok(paymentService.refundPayment(paymentNumber, requestDto));
    }

    @GetMapping("/{paymentNumber}")
    public ResponseEntity<PaymentDto> getPayment(@PathVariable String paymentNumber) {
        return ResponseEntity.ok(paymentService.getPayment(paymentNumber));
    }
}
