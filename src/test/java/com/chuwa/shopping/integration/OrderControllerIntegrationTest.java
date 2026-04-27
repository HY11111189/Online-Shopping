package com.chuwa.shopping.integration;

import com.chuwa.shopping.OnlineShoppingApplication;
import com.chuwa.shopping.shared.client.ItemServiceClient;
import com.chuwa.shopping.item.dto.ItemDto;
import com.chuwa.shopping.order.dao.ShoppingOrderRepository;
import com.chuwa.shopping.order.dto.AddressSnapshotDto;
import com.chuwa.shopping.order.dto.OrderCancelRequestDto;
import com.chuwa.shopping.order.dto.OrderCreateRequestDto;
import com.chuwa.shopping.order.dto.OrderLineItemDto;
import com.chuwa.shopping.order.dto.OrderUpdateRequestDto;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = OnlineShoppingApplication.class)
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "shopping.integration-tests", matches = "true")
class OrderControllerIntegrationTest extends AuthenticatedIntegrationTestSupport {

    @Autowired
    private ShoppingOrderRepository shoppingOrderRepository;

    @MockBean
    private ItemServiceClient itemServiceClient;

    private String createdOrderNumber;

    @AfterEach
    void tearDown() {
        if (createdOrderNumber != null) {
            shoppingOrderRepository.findByOrderNumber(createdOrderNumber).ifPresent(shoppingOrderRepository::delete);
        }
    }

    @Test
    void createUpdateCancelAndLookupOrderShouldUseCassandraPersistence() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        given(itemServiceClient.getItemBySku(anyString())).willAnswer(invocation -> catalogItem(invocation.getArgument(0)));

        OrderCreateRequestDto createRequest = new OrderCreateRequestDto();
        createRequest.setCustomerId(801L);
        createRequest.setCurrencyCode("USD");
        createRequest.setTaxAmount(new BigDecimal("2.50"));
        createRequest.setShippingAmount(new BigDecimal("5.00"));
        createRequest.setDiscountAmount(new BigDecimal("1.00"));
        createRequest.setShippingAddress(address("Alice Smith", "3125551111"));
        createRequest.setBillingAddress(address("Alice Smith", "3125552222"));
        createRequest.setItems(List.of(lineItem("SKU-" + unique, "Coffee Mug", 2, "12.99")));
        createRequest.setCreateRequestId("create-" + unique);

        String createResponse = mockMvc.perform(post("/api/v1/shopping/orders")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(801L))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.subtotalAmount").value(25.98))
                .andExpect(jsonPath("$.totalAmount").value(32.48))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        createdOrderNumber = createJson.get("orderNumber").asText();
        assertNotNull(createdOrderNumber);

        OrderUpdateRequestDto updateRequest = new OrderUpdateRequestDto();
        updateRequest.setCurrencyCode("USD");
        updateRequest.setTaxAmount(new BigDecimal("3.00"));
        updateRequest.setShippingAmount(new BigDecimal("4.00"));
        updateRequest.setDiscountAmount(new BigDecimal("0.50"));
        updateRequest.setShippingAddress(address("Alice Updated", "3125553333"));
        updateRequest.setBillingAddress(address("Alice Updated", "3125554444"));
        updateRequest.setItems(List.of(lineItem("SKU-" + unique, "Coffee Mug", 3, "12.99")));
        updateRequest.setUpdateRequestId("update-" + unique);
        updateRequest.setStatusReason("Customer changed quantity");

        mockMvc.perform(put("/api/v1/shopping/orders/{orderNumber}", createdOrderNumber)
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(createdOrderNumber))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.totalAmount").value(45.47))
                .andExpect(jsonPath("$.lastUpdateRequestId").value("update-" + unique));

        OrderCancelRequestDto cancelRequest = new OrderCancelRequestDto();
        cancelRequest.setCancelRequestId("cancel-" + unique);
        cancelRequest.setStatusReason("Customer request");

        mockMvc.perform(post("/api/v1/shopping/orders/{orderNumber}/cancel", createdOrderNumber)
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(createdOrderNumber))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelRequestId").value("cancel-" + unique))
                .andExpect(jsonPath("$.version").value(3));

        mockMvc.perform(get("/api/v1/shopping/orders/{orderNumber}", createdOrderNumber)
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(createdOrderNumber))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.shippingAddress.recipientName").value("Alice Updated"));

        mockMvc.perform(get("/api/v1/shopping/orders/customers/{customerId}", 801L)
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.orderNumber=='" + createdOrderNumber + "')]").exists());
    }

    private ItemDto catalogItem(String sku) {
        ItemDto dto = new ItemDto();
        dto.setId("catalog-" + sku);
        dto.setSku(sku);
        dto.setItemName("Coffee Mug");
        dto.setUpc("123456789012");
        dto.setUnitPrice(new BigDecimal("12.99"));
        dto.setCurrencyCode("USD");
        return dto;
    }

    private AddressSnapshotDto address(String recipientName, String phoneNumber) {
        AddressSnapshotDto dto = new AddressSnapshotDto();
        dto.setRecipientName(recipientName);
        dto.setAddressLine1("123 Main St");
        dto.setCity("Chicago");
        dto.setState("IL");
        dto.setPostalCode("60601");
        dto.setCountry("US");
        dto.setPhoneNumber(phoneNumber);
        return dto;
    }

    private OrderLineItemDto lineItem(String sku, String itemName, int quantity, String unitPrice) {
        OrderLineItemDto dto = new OrderLineItemDto();
        dto.setItemId(sku);
        dto.setSku(sku);
        dto.setItemName(itemName);
        dto.setUpc("123456789012");
        dto.setQuantity(quantity);
        dto.setUnitPrice(new BigDecimal(unitPrice));
        return dto;
    }
}
