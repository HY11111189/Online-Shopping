package com.chuwa.shopping.item.service;

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

    InventoryDto updateInventory(String itemId, InventoryDto inventoryDto);

    InventoryAdjustmentResultDto adjustInventory(String sku, InventoryAdjustmentRequestDto requestDto);
}
