package com.chuwa.shopping.integration;

import com.chuwa.shopping.OnlineShoppingApplication;
import com.chuwa.shopping.item.dao.ItemRepository;
import com.chuwa.shopping.item.dto.InventoryDto;
import com.chuwa.shopping.item.dto.ItemDto;
import com.chuwa.shopping.item.entity.ItemDocument;
import com.chuwa.shopping.item.entity.ItemStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = OnlineShoppingApplication.class)
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "shopping.integration-tests", matches = "true")
class ItemControllerIntegrationTest extends AuthenticatedIntegrationTestSupport {

    @Autowired
    private ItemRepository itemRepository;

    private String createdItemId;
    private String createdSku;

    @AfterEach
    void tearDown() {
        if (createdItemId != null) {
            itemRepository.deleteById(createdItemId);
        } else if (createdSku != null) {
            itemRepository.findBySku(createdSku).map(ItemDocument::getId).ifPresent(itemRepository::deleteById);
        }
    }

    @Test
    void createAndGetItemBySkuShouldUseMongoPersistence() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        createdSku = "SKU-" + unique;

        ItemDto request = new ItemDto();
        request.setSku(createdSku);
        request.setUpc("12345678" + unique);
        request.setItemName("Coffee Mug");
        request.setBrand("Acme");
        request.setCategory("Kitchen");
        request.setDescription("White ceramic mug");
        request.setUnitPrice(new BigDecimal("12.99"));
        request.setCurrencyCode("USD");
        request.setPictureUrls(List.of("https://example.com/mug.jpg"));
        request.setStatus(ItemStatus.ACTIVE);
        InventoryDto inventory = new InventoryDto();
        inventory.setTotalQuantity(100);
        inventory.setAvailableQuantity(90);
        inventory.setReservedQuantity(10);
        inventory.setWarehouseCode("WH1");
        inventory.setInStock(true);
        request.setInventory(inventory);
        request.setAttributes(Map.of("color", "white"));

        String response = mockMvc.perform(post("/api/v1/shopping/items")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value(createdSku))
                .andReturn()
                .getResponse()
                .getContentAsString();

        createdItemId = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(get("/api/v1/shopping/items/sku/{sku}", createdSku)
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value(createdSku))
                .andExpect(jsonPath("$.inventory.availableQuantity").value(90))
                .andExpect(jsonPath("$.attributes.color").value("white"));
    }
}
