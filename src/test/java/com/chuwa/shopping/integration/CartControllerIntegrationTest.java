package com.chuwa.shopping.integration;

import com.chuwa.shopping.OnlineShoppingApplication;
import com.chuwa.shopping.shared.client.ItemServiceClient;
import com.chuwa.shopping.item.dto.InventoryDto;
import com.chuwa.shopping.item.dto.ItemDto;
import com.chuwa.shopping.order.dao.ShoppingCartRepository;
import com.chuwa.shopping.order.dto.CartItemDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = OnlineShoppingApplication.class)
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "shopping.integration-tests", matches = "true")
class CartControllerIntegrationTest extends AuthenticatedIntegrationTestSupport {

    @Autowired
    private ShoppingCartRepository shoppingCartRepository;

    @MockBean
    private ItemServiceClient itemServiceClient;

    private final Long customerId = 901L;

    @AfterEach
    void tearDown() {
        shoppingCartRepository.findByCustomerId(customerId).ifPresent(shoppingCartRepository::delete);
    }

    @Test
    void addItemAndGetCartShouldUseCassandraPersistence() throws Exception {
        given(itemServiceClient.getItemBySku("SKU-1001")).willReturn(catalogItem());

        CartItemDto item = new CartItemDto();
        item.setItemId("SKU-1001");
        item.setSku("SKU-1001");
        item.setItemName("Coffee Mug");
        item.setUpc("123456789012");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("12.99"));

        mockMvc.perform(post("/api/v1/shopping/carts/{customerId}/items", customerId)
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.items[0].itemId").value("catalog-SKU-1001"))
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        mockMvc.perform(get("/api/v1/shopping/carts/{customerId}", customerId)
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].sku").value("SKU-1001"));
    }

    private ItemDto catalogItem() {
        ItemDto item = new ItemDto();
        item.setId("catalog-SKU-1001");
        item.setSku("SKU-1001");
        item.setItemName("Coffee Mug");
        item.setUpc("123456789012");
        item.setUnitPrice(new BigDecimal("12.99"));
        InventoryDto inventory = new InventoryDto();
        inventory.setAvailableQuantity(50);
        inventory.setInStock(true);
        item.setInventory(inventory);
        return item;
    }
}
