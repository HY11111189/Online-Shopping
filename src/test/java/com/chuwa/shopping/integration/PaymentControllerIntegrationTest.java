package com.chuwa.shopping.integration;

import com.chuwa.shopping.OnlineShoppingApplication;
import com.chuwa.shopping.shared.client.OrderServiceClient;
import com.chuwa.shopping.order.dto.OrderDto;
import com.chuwa.shopping.payment.dao.PaymentTransactionRepository;
import com.chuwa.shopping.payment.dto.PaymentRequestDto;
import com.chuwa.shopping.payment.dto.PaymentUpdateRequestDto;
import com.chuwa.shopping.payment.dto.RefundRequestDto;
import com.chuwa.shopping.payment.entity.PaymentMethod;
import com.chuwa.shopping.payment.entity.PaymentStatus;
import com.chuwa.shopping.payment.entity.PaymentTransaction;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = OnlineShoppingApplication.class)
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "shopping.integration-tests", matches = "true")
class PaymentControllerIntegrationTest extends AuthenticatedIntegrationTestSupport {

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @MockBean
    private OrderServiceClient orderServiceClient;

    private String createdPaymentNumber;
    private String refundPaymentNumber;

    @AfterEach
    void tearDown() {
        if (refundPaymentNumber != null) {
            paymentTransactionRepository.findByPaymentNumber(refundPaymentNumber).ifPresent(paymentTransactionRepository::delete);
        }
        if (createdPaymentNumber != null) {
            paymentTransactionRepository.findByPaymentNumber(createdPaymentNumber).ifPresent(paymentTransactionRepository::delete);
        }
    }

    @Test
    void submitUpdateRefundAndLookupPaymentShouldUseMysqlPersistence() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        given(orderServiceClient.getOrder("ORD-TEST-" + unique)).willReturn(orderDto("ORD-TEST-" + unique, 901L, "45.47"));
        given(orderServiceClient.syncPayment(any(), any())).willAnswer(invocation -> orderDto(invocation.getArgument(0), 901L, "45.47"));

        PaymentRequestDto submitRequest = new PaymentRequestDto();
        submitRequest.setOrderId("ORD-TEST-" + unique);
        submitRequest.setCustomerId(901L);
        submitRequest.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        submitRequest.setAmount(new BigDecimal("45.47"));
        submitRequest.setCurrencyCode("USD");
        submitRequest.setIdempotencyKey("pay-" + unique);
        submitRequest.setExternalReference("gateway-init-" + unique);

        String submitResponse = mockMvc.perform(post("/api/v1/shopping/payments")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentStatus").value("INITIATED"))
                .andExpect(jsonPath("$.operationType").value("SUBMIT"))
                .andExpect(jsonPath("$.amount").value(45.47))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode submitJson = objectMapper.readTree(submitResponse);
        createdPaymentNumber = submitJson.get("paymentNumber").asText();
        Long createdPaymentId = submitJson.get("id").asLong();
        assertNotNull(createdPaymentNumber);

        String duplicateSubmitResponse = mockMvc.perform(post("/api/v1/shopping/payments")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentNumber").value(createdPaymentNumber))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode duplicateJson = objectMapper.readTree(duplicateSubmitResponse);
        assertEquals(createdPaymentId, duplicateJson.get("id").asLong());

        PaymentUpdateRequestDto updateRequest = new PaymentUpdateRequestDto();
        updateRequest.setPaymentStatus(PaymentStatus.CAPTURED);
        updateRequest.setExternalReference("gateway-captured-" + unique);
        updateRequest.setGatewayResponseCode("00");
        updateRequest.setGatewayResponseMessage("Approved");

        mockMvc.perform(put("/api/v1/shopping/payments/{paymentNumber}", createdPaymentNumber)
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentNumber").value(createdPaymentNumber))
                .andExpect(jsonPath("$.paymentStatus").value("CAPTURED"))
                .andExpect(jsonPath("$.operationType").value("UPDATE"))
                .andExpect(jsonPath("$.gatewayResponseCode").value("00"))
                .andExpect(jsonPath("$.processedAt").isNotEmpty());

        RefundRequestDto refundRequest = new RefundRequestDto();
        refundRequest.setIdempotencyKey("refund-" + unique);
        refundRequest.setAmount(new BigDecimal("45.47"));
        refundRequest.setExternalReference("gateway-refund-" + unique);

        String refundResponse = mockMvc.perform(post("/api/v1/shopping/payments/{paymentNumber}/refund", createdPaymentNumber)
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refundRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.operationType").value("REFUND"))
                .andExpect(jsonPath("$.relatedPaymentNumber").value(createdPaymentNumber))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode refundJson = objectMapper.readTree(refundResponse);
        refundPaymentNumber = refundJson.get("paymentNumber").asText();
        Long refundId = refundJson.get("id").asLong();

        String duplicateRefundResponse = mockMvc.perform(post("/api/v1/shopping/payments/{paymentNumber}/refund", createdPaymentNumber)
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refundRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentNumber").value(refundPaymentNumber))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode duplicateRefundJson = objectMapper.readTree(duplicateRefundResponse);
        assertEquals(refundId, duplicateRefundJson.get("id").asLong());

        mockMvc.perform(get("/api/v1/shopping/payments/{paymentNumber}", createdPaymentNumber)
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentNumber").value(createdPaymentNumber))
                .andExpect(jsonPath("$.paymentStatus").value("CAPTURED"));

        List<PaymentTransaction> matchingPayments = paymentTransactionRepository.findAll().stream()
                .filter(payment -> ("pay-" + unique).equals(payment.getIdempotencyKey())
                        || ("refund-" + unique).equals(payment.getIdempotencyKey()))
                .toList();
        assertEquals(2, matchingPayments.size());
    }

    private OrderDto orderDto(String orderNumber, Long customerId, String totalAmount) {
        OrderDto order = new OrderDto();
        order.setOrderNumber(orderNumber);
        order.setCustomerId(customerId);
        order.setCurrencyCode("USD");
        order.setTotalAmount(new BigDecimal(totalAmount));
        return order;
    }
}
