package com.chuwa.shopping.payment.service;

import com.chuwa.shopping.payment.dto.PaymentDto;
import com.chuwa.shopping.payment.dto.PaymentRequestDto;
import com.chuwa.shopping.payment.dto.PaymentUpdateRequestDto;
import com.chuwa.shopping.payment.dto.RefundRequestDto;

public interface PaymentService {

    PaymentDto submitPayment(PaymentRequestDto requestDto);

    PaymentDto updatePayment(String paymentNumber, PaymentUpdateRequestDto requestDto);

    PaymentDto cancelPayment(String paymentNumber, RefundRequestDto requestDto);

    PaymentDto refundPayment(String paymentNumber, RefundRequestDto requestDto);

    PaymentDto getPayment(String paymentNumber);
}
