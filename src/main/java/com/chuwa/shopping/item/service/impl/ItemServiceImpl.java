package com.chuwa.shopping.item.service.impl;

import com.chuwa.shopping.item.dao.ItemRepository;
import com.chuwa.shopping.exception.ShoppingResourceNotFoundException;
import com.chuwa.shopping.item.entity.ItemDocument;
import com.chuwa.shopping.item.dto.InventoryDto;
import com.chuwa.shopping.item.dto.ItemDto;
import com.chuwa.shopping.item.service.ItemService;
import com.chuwa.shopping.shared.mapper.ShoppingMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ItemServiceImpl implements ItemService {

    private final ItemRepository itemRepository;
    private final ShoppingMapper shoppingMapper;

    public ItemServiceImpl(ItemRepository itemRepository, ShoppingMapper shoppingMapper) {
        this.itemRepository = itemRepository;
        this.shoppingMapper = shoppingMapper;
    }

    @Override
    public ItemDto createItem(ItemDto itemDto) {
        ItemDocument document = buildItemDocument(new ItemDocument(), itemDto);
        LocalDateTime now = LocalDateTime.now();
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        return shoppingMapper.toItemDto(itemRepository.save(document));
    }

    @Override
    public ItemDto updateItem(String itemId, ItemDto itemDto) {
        ItemDocument document = getItemDocument(itemId);
        buildItemDocument(document, itemDto);
        document.setUpdatedAt(LocalDateTime.now());
        return shoppingMapper.toItemDto(itemRepository.save(document));
    }

    @Override
    public ItemDto getItem(String itemId) {
        return shoppingMapper.toItemDto(getItemDocument(itemId));
    }

    @Override
    public ItemDto getItemBySku(String sku) {
        ItemDocument document = itemRepository.findBySku(sku)
                .orElseThrow(() -> new ShoppingResourceNotFoundException("Item", "sku", sku));
        return shoppingMapper.toItemDto(document);
    }

    @Override
    public List<ItemDto> getAllItems() {
        return itemRepository.findAll().stream().map(shoppingMapper::toItemDto).collect(Collectors.toList());
    }

    @Override
    public InventoryDto updateInventory(String itemId, InventoryDto inventoryDto) {
        ItemDocument document = getItemDocument(itemId);
        document.setInventory(shoppingMapper.toInventoryDocument(inventoryDto));
        document.setUpdatedAt(LocalDateTime.now());
        itemRepository.save(document);
        return shoppingMapper.toInventoryDto(document.getInventory());
    }

    private ItemDocument getItemDocument(String itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ShoppingResourceNotFoundException("Item", "id", itemId));
    }

    private ItemDocument buildItemDocument(ItemDocument document, ItemDto itemDto) {
        document.setSku(itemDto.getSku());
        document.setUpc(itemDto.getUpc());
        document.setItemName(itemDto.getItemName());
        document.setBrand(itemDto.getBrand());
        document.setCategory(itemDto.getCategory());
        document.setDescription(itemDto.getDescription());
        document.setUnitPrice(itemDto.getUnitPrice());
        document.setListPrice(itemDto.getListPrice());
        document.setDiscountPercent(itemDto.getDiscountPercent());
        document.setCurrencyCode(itemDto.getCurrencyCode());
        document.setPictureUrls(itemDto.getPictureUrls() == null ? new ArrayList<>() : itemDto.getPictureUrls());
        document.setStatus(itemDto.getStatus());
        document.setInventory(shoppingMapper.toInventoryDocument(itemDto.getInventory()));
        document.setAttributes(itemDto.getAttributes() == null ? new HashMap<>() : itemDto.getAttributes());
        return document;
    }
}
