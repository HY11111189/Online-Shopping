package com.chuwa.shopping.integration;

import com.chuwa.shopping.OnlineShoppingApplication;
import com.chuwa.shopping.account.dao.CustomerAccountRepository;
import com.chuwa.shopping.account.dto.AccountRequestDto;
import com.chuwa.shopping.account.dto.AddressDto;
import com.chuwa.shopping.account.dto.StoredPaymentMethodDto;
import com.chuwa.shopping.account.entity.AddressType;
import com.chuwa.shopping.payment.entity.PaymentMethod;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = OnlineShoppingApplication.class)
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "shopping.integration-tests", matches = "true")
class AccountControllerIntegrationTest extends AuthenticatedIntegrationTestSupport {

    @Autowired
    private CustomerAccountRepository customerAccountRepository;

    private Long createdAccountId;

    @AfterEach
    void tearDown() {
        if (createdAccountId != null) {
            customerAccountRepository.deleteById(createdAccountId);
        }
    }

    @Test
    void createAndGetAccountShouldUseMysqlPersistence() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        AccountRequestDto request = new AccountRequestDto();
        request.setUsername("acct-" + unique);
        request.setFullName("Alice Smith");
        request.setEmail("alice-" + unique + "@example.com");
        request.setPassword("test123");
        request.setPhoneNumber("3125551111");

        AddressDto address = new AddressDto();
        address.setLabel("home");
        address.setRecipientName("Alice Smith");
        address.setAddressLine1("123 Main St");
        address.setCity("Chicago");
        address.setState("IL");
        address.setPostalCode("60601");
        address.setCountry("US");
        address.setAddressType(AddressType.SHIPPING);
        address.setDefaultAddress(true);
        request.setAddresses(List.of(address));

        StoredPaymentMethodDto method = new StoredPaymentMethodDto();
        method.setPaymentMethodType(PaymentMethod.CREDIT_CARD);
        method.setProvider("visa");
        method.setAccountToken("tok-" + unique);
        method.setMaskedNumber("****1111");
        method.setCardholderName("Alice Smith");
        method.setExpiryMonth(12);
        method.setExpiryYear(2030);
        method.setDefaultMethod(true);
        method.setActive(true);
        request.setPaymentMethods(List.of(method));

        String createResponse = mockMvc.perform(post("/api/v1/shopping/accounts")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("acct-" + unique))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(createResponse);
        createdAccountId = json.get("id").asLong();
        assertNotNull(createdAccountId);

        mockMvc.perform(get("/api/v1/shopping/accounts/{id}", createdAccountId)
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdAccountId))
                .andExpect(jsonPath("$.addresses[0].city").value("Chicago"))
                .andExpect(jsonPath("$.paymentMethods[0].provider").value("visa"));
    }
}
