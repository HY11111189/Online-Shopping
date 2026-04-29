package com.chuwa.shopping.item.mapper;

import com.chuwa.shopping.item.entity.InventoryDocument;
import com.chuwa.shopping.item.entity.ItemDocument;
import com.chuwa.shopping.dto.item.InventoryDto;
import com.chuwa.shopping.dto.item.ItemDto;
import com.chuwa.shopping.dto.item.ItemStatus;
import org.springframework.stereotype.Component;

@Component
public class ItemMapper {

    public ItemDto toItemDto(ItemDocument document) {
        ItemDto dto = new ItemDto();
        dto.setId(document.getId());
        dto.setSku(document.getSku());
        dto.setUpc(document.getUpc());
        dto.setItemName(document.getItemName());
        dto.setBrand(document.getBrand());
        dto.setCategory(document.getCategory());
        dto.setDescription(document.getDescription());
        dto.setUnitPrice(document.getUnitPrice());
        dto.setListPrice(document.getListPrice());
        dto.setDiscountPercent(document.getDiscountPercent());
        dto.setCurrencyCode(document.getCurrencyCode());
        dto.setPictureUrls(document.getPictureUrls());
        dto.setStatus(document.getStatus() == null ? null : ItemStatus.valueOf(document.getStatus().name()));
        dto.setInventory(toInventoryDto(document.getInventory()));
        dto.setAttributes(document.getAttributes());
        dto.setCreatedAt(document.getCreatedAt());
        dto.setUpdatedAt(document.getUpdatedAt());
        return dto;
    }

    public InventoryDto toInventoryDto(InventoryDocument document) {
        if (document == null) {
            return null;
        }
        InventoryDto dto = new InventoryDto();
        dto.setTotalQuantity(document.getTotalQuantity());
        dto.setAvailableQuantity(document.getAvailableQuantity());
        dto.setReservedQuantity(document.getReservedQuantity());
        dto.setReorderLevel(document.getReorderLevel());
        dto.setWarehouseCode(document.getWarehouseCode());
        dto.setInStock(document.getInStock());
        dto.setLastRestockedAt(document.getLastRestockedAt());
        return dto;
    }

    public InventoryDocument toInventoryDocument(InventoryDto dto) {
        if (dto == null) {
            return null;
        }
        InventoryDocument document = new InventoryDocument();
        document.setTotalQuantity(dto.getTotalQuantity());
        document.setAvailableQuantity(dto.getAvailableQuantity());
        document.setReservedQuantity(dto.getReservedQuantity());
        document.setReorderLevel(dto.getReorderLevel());
        document.setWarehouseCode(dto.getWarehouseCode());
        document.setInStock(dto.getInStock());
        document.setLastRestockedAt(dto.getLastRestockedAt());
        return document;
    }
}
