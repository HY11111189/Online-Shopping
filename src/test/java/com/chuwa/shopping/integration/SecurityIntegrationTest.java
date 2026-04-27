package com.chuwa.shopping.integration;

import com.chuwa.shopping.OnlineShoppingApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = OnlineShoppingApplication.class)
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "shopping.integration-tests", matches = "true")
class SecurityIntegrationTest extends AuthenticatedIntegrationTestSupport {

    @Test
    void publicCatalogShouldAllowAnonymousBrowsingButProtectedEndpointsShouldRejectWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/shopping/items"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/shopping/carts/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shoppingEndpointsShouldAcceptRequestsWithBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/shopping/items")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk());
    }

    @Test
    void signInShouldReturnBearerTokenPayload() throws Exception {
        mockMvc.perform(get("/api/v1/oauth2/validate")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.username").value("itest-user"));
    }
}
