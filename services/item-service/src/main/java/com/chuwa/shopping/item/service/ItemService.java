package com.chuwa.shopping.item.service;

import com.chuwa.shopping.dto.PageResponse;
import com.chuwa.shopping.dto.item.InventoryAdjustmentRequestDto;
import com.chuwa.shopping.dto.item.InventoryAdjustmentResultDto;
import com.chuwa.shopping.dto.item.InventoryDto;
import com.chuwa.shopping.dto.item.ItemDto;

import java.util.List;

public interface ItemService {

    ItemDto createItem(ItemDto itemDto);

    ItemDto updateItem(String itemId, ItemDto itemDto);

    ItemDto getItem(String itemId);

    ItemDto getItemBySku(String sku);

    List<ItemDto> getAllItems();

    List<String> getCategories();

    List<ItemDto> getItemsByCategory(String category, int limit);

    PageResponse<ItemDto> getItemsByCategoryPage(String category, int page, int size);

    List<ItemDto> searchItems(String query, String category, String brand, Boolean inStock, int limit);

    PageResponse<ItemDto> searchItemsPage(String query, String category, String brand, Boolean inStock, int page, int size);

    PageResponse<ItemDto> getAllItemsPage(int page, int size);

    InventoryDto updateInventory(String itemId, InventoryDto inventoryDto);

    InventoryAdjustmentResultDto adjustInventory(String sku, InventoryAdjustmentRequestDto requestDto);
}
