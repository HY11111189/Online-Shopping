package com.chuwa.shopping.item.service.impl;

import com.chuwa.shopping.dto.item.InventoryAdjustmentRequestDto;
import com.chuwa.shopping.dto.item.InventoryAdjustmentResultDto;
import com.chuwa.shopping.dto.item.InventoryAdjustmentType;
import com.chuwa.shopping.dto.item.InventoryDto;
import com.chuwa.shopping.dto.item.ItemDto;
import com.chuwa.shopping.dto.item.ItemStatus;
import com.chuwa.shopping.exception.ShoppingResourceNotFoundException;
import com.chuwa.shopping.item.dao.ItemRepository;
import com.chuwa.shopping.item.entity.InventoryDocument;
import com.chuwa.shopping.item.entity.ItemDocument;
import com.chuwa.shopping.item.mapper.ItemMapper;
import com.chuwa.shopping.item.service.ItemService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ItemServiceImpl implements ItemService {

    private final ItemRepository itemRepository;
    private final ItemMapper itemMapper;
    private final MongoTemplate mongoTemplate;

    public ItemServiceImpl(ItemRepository itemRepository, ItemMapper itemMapper, MongoTemplate mongoTemplate) {
        this.itemRepository = itemRepository;
        this.itemMapper = itemMapper;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public ItemDto createItem(ItemDto itemDto) {
        ItemDocument document = buildItemDocument(new ItemDocument(), itemDto);
        LocalDateTime now = LocalDateTime.now();
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        return itemMapper.toItemDto(itemRepository.save(document));
    }

    @Override
    public ItemDto updateItem(String itemId, ItemDto itemDto) {
        ItemDocument document = getItemDocument(itemId);
        buildItemDocument(document, itemDto);
        document.setUpdatedAt(LocalDateTime.now());
        return itemMapper.toItemDto(itemRepository.save(document));
    }

    @Override
    public ItemDto getItem(String itemId) {
        return itemMapper.toItemDto(getItemDocument(itemId));
    }

    @Override
    public ItemDto getItemBySku(String sku) {
        ItemDocument document = itemRepository.findBySku(sku)
                .orElseThrow(() -> new ShoppingResourceNotFoundException("Item", "sku", sku));
        return itemMapper.toItemDto(document);
    }

    @Override
    public List<ItemDto> getAllItems() {
        return itemRepository.findAll().stream().map(itemMapper::toItemDto).collect(Collectors.toList());
    }

    @Override
    public InventoryDto updateInventory(String itemId, InventoryDto inventoryDto) {
        ItemDocument document = getItemDocument(itemId);
        document.setInventory(itemMapper.toInventoryDocument(inventoryDto));
        document.setUpdatedAt(LocalDateTime.now());
        itemRepository.save(document);
        return itemMapper.toInventoryDto(document.getInventory());
    }

    @Override
    public InventoryAdjustmentResultDto adjustInventory(String sku, InventoryAdjustmentRequestDto requestDto) {
        validateInventoryAdjustment(requestDto);
        ItemDocument existing = itemRepository.findBySku(sku)
                .orElseThrow(() -> new ShoppingResourceNotFoundException("Item", "sku", sku));
        InventoryDocument existingInventory = existing.getInventory();
        if (existingInventory == null) {
            throw new IllegalStateException("Inventory not initialized for sku " + sku);
        }
        if (existingInventory.getProcessedOperationIds() != null
                && existingInventory.getProcessedOperationIds().contains(requestDto.getOperationId())) {
            return toInventoryAdjustmentResult(existing, requestDto, false);
        }

        Query query = new Query(Criteria.where("sku").is(sku)
                .and("inventory.processed_operation_ids").ne(requestDto.getOperationId()));
        Update update = new Update()
                .currentDate("updated_at")
                .addToSet("inventory.processed_operation_ids", requestDto.getOperationId());

        int quantity = requestDto.getQuantity();
        switch (requestDto.getAdjustmentType()) {
            case PURCHASE:
                query.addCriteria(Criteria.where("inventory.available_quantity").gte(quantity));
                update.inc("inventory.available_quantity", -quantity);
                update.inc("inventory.total_quantity", -quantity);
                break;
            case RESTOCK:
                update.inc("inventory.available_quantity", quantity);
                update.inc("inventory.total_quantity", quantity);
                update.set("inventory.last_restocked_at", LocalDateTime.now());
                break;
            default:
                throw new IllegalArgumentException("Unsupported inventory adjustment type");
        }

        ItemDocument adjusted = mongoTemplate.findAndModify(query, update, ItemDocument.class);
        if (adjusted == null) {
            throw new IllegalStateException("Inventory adjustment could not be applied for sku " + sku);
        }
        adjusted = itemRepository.findBySku(sku)
                .orElseThrow(() -> new ShoppingResourceNotFoundException("Item", "sku", sku));
        refreshInStock(adjusted.getInventory());
        adjusted.setUpdatedAt(LocalDateTime.now());
        itemRepository.save(adjusted);
        return toInventoryAdjustmentResult(adjusted, requestDto, true);
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
        document.setStatus(itemDto.getStatus() == null ? null : ItemStatus.valueOf(itemDto.getStatus().name()));
        document.setInventory(itemMapper.toInventoryDocument(itemDto.getInventory()));
        document.setAttributes(itemDto.getAttributes() == null ? new HashMap<>() : itemDto.getAttributes());
        return document;
    }

    private void validateInventoryAdjustment(InventoryAdjustmentRequestDto requestDto) {
        if (requestDto == null || requestDto.getAdjustmentType() == null) {
            throw new IllegalArgumentException("Inventory adjustment type is required");
        }
        if (requestDto.getOperationId() == null || requestDto.getOperationId().isBlank()) {
            throw new IllegalArgumentException("Inventory operationId is required");
        }
        if (requestDto.getQuantity() == null || requestDto.getQuantity() <= 0) {
            throw new IllegalArgumentException("Inventory quantity must be greater than zero");
        }
    }

    private InventoryAdjustmentResultDto toInventoryAdjustmentResult(ItemDocument document,
                                                                     InventoryAdjustmentRequestDto requestDto,
                                                                     boolean applied) {
        InventoryAdjustmentResultDto result = new InventoryAdjustmentResultDto();
        result.setItemId(document.getId());
        result.setSku(document.getSku());
        result.setOperationId(requestDto.getOperationId());
        result.setAdjustmentType(requestDto.getAdjustmentType());
        result.setQuantity(requestDto.getQuantity());
        if (document.getInventory() != null) {
            refreshInStock(document.getInventory());
            result.setAvailableQuantity(document.getInventory().getAvailableQuantity());
            result.setReservedQuantity(document.getInventory().getReservedQuantity());
            result.setTotalQuantity(document.getInventory().getTotalQuantity());
            result.setInStock(document.getInventory().getInStock());
        }
        result.setApplied(applied);
        return result;
    }

    private void refreshInStock(InventoryDocument inventory) {
        if (inventory == null) {
            return;
        }
        int available = inventory.getAvailableQuantity() == null ? 0 : inventory.getAvailableQuantity();
        inventory.setInStock(available > 0);
    }
}
