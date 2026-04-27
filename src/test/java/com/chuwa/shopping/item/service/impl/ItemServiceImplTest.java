package com.chuwa.shopping.item.service.impl;

import com.chuwa.shopping.item.dao.ItemRepository;
import com.chuwa.shopping.item.dto.InventoryDto;
import com.chuwa.shopping.item.dto.ItemDto;
import com.chuwa.shopping.item.entity.ItemDocument;
import com.chuwa.shopping.item.entity.ItemStatus;
import com.chuwa.shopping.shared.mapper.ShoppingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemServiceImplTest {

    @Mock
    private ItemRepository itemRepository;

    private ShoppingMapper shoppingMapper;

    private ItemServiceImpl itemService;

    @BeforeEach
    void setUp() {
        shoppingMapper = new ShoppingMapper();
        itemService = new ItemServiceImpl(itemRepository, shoppingMapper);
    }

    @Test
    void createItemShouldPersistMappedFieldsAndReturnSavedDto() {
        ItemDto request = new ItemDto();
        request.setSku("SKU-1001");
        request.setUpc("123456789012");
        request.setItemName("Coffee Mug");
        request.setBrand("Acme");
        request.setCategory("Kitchen");
        request.setDescription("White ceramic mug");
        request.setUnitPrice(new BigDecimal("12.99"));
        request.setCurrencyCode("USD");
        request.setPictureUrls(List.of("https://example.com/mug.jpg"));
        request.setStatus(ItemStatus.ACTIVE);
        InventoryDto inventory = new InventoryDto();
        inventory.setAvailableQuantity(90);
        request.setInventory(inventory);
        request.setAttributes(Map.of("color", "white"));

        when(itemRepository.save(any(ItemDocument.class))).thenAnswer(invocation -> {
            ItemDocument document = invocation.getArgument(0);
            document.setId("mongo-id-1");
            return document;
        });

        ItemDto result = itemService.createItem(request);

        ArgumentCaptor<ItemDocument> captor = ArgumentCaptor.forClass(ItemDocument.class);
        verify(itemRepository).save(captor.capture());
        ItemDocument saved = captor.getValue();
        assertEquals("SKU-1001", saved.getSku());
        assertEquals("123456789012", saved.getUpc());
        assertEquals("Coffee Mug", saved.getItemName());
        assertEquals(new BigDecimal("12.99"), saved.getUnitPrice());
        assertEquals("USD", saved.getCurrencyCode());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals("mongo-id-1", result.getId());
        assertEquals("SKU-1001", result.getSku());
    }

    @Test
    void updateInventoryShouldReplaceInventoryDocument() {
        ItemDocument existing = new ItemDocument();
        existing.setId("mongo-id-1");
        existing.setSku("SKU-1001");
        when(itemRepository.findById("mongo-id-1")).thenReturn(Optional.of(existing));
        when(itemRepository.save(any(ItemDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryDto inventory = new InventoryDto();
        inventory.setTotalQuantity(100);
        inventory.setAvailableQuantity(80);
        inventory.setReservedQuantity(20);
        inventory.setWarehouseCode("WH1");
        inventory.setInStock(true);

        InventoryDto result = itemService.updateInventory("mongo-id-1", inventory);

        assertEquals(100, result.getTotalQuantity());
        assertEquals(80, result.getAvailableQuantity());
        assertEquals(20, result.getReservedQuantity());
        verify(itemRepository).save(existing);
    }
}
