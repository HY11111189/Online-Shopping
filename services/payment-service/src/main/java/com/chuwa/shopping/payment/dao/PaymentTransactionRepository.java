package com.chuwa.shopping.payment.dao;

import com.chuwa.shopping.payment.entity.PaymentTransaction;
import com.chuwa.shopping.dto.payment.PaymentOperationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByPaymentNumber(String paymentNumber);

    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentTransaction> findByOrderIdAndOperationType(String orderId, PaymentOperationType operationType);
}
