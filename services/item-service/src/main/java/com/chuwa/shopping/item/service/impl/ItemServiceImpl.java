package com.chuwa.shopping.item.service.impl;

import com.chuwa.shopping.dto.PageResponse;
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
import com.chuwa.shopping.item.search.ItemSearchIndexer;
import com.chuwa.shopping.item.search.ItemSearchService;
import com.chuwa.shopping.item.service.ItemService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
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
    private final ItemSearchIndexer itemSearchIndexer;
    private final ItemSearchService itemSearchService;

    public ItemServiceImpl(ItemRepository itemRepository,
                           ItemMapper itemMapper,
                           MongoTemplate mongoTemplate,
                           ObjectProvider<ItemSearchIndexer> itemSearchIndexerProvider,
                           ObjectProvider<ItemSearchService> itemSearchServiceProvider) {
        this.itemRepository = itemRepository;
        this.itemMapper = itemMapper;
        this.mongoTemplate = mongoTemplate;
        this.itemSearchIndexer = itemSearchIndexerProvider.getIfAvailable();
        this.itemSearchService = itemSearchServiceProvider.getIfAvailable();
    }

    @Override
    public ItemDto createItem(ItemDto itemDto) {
        ItemDocument document = buildItemDocument(new ItemDocument(), itemDto);
        LocalDateTime now = LocalDateTime.now();
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        ItemDocument saved = itemRepository.save(document);
        indexIfAvailable(saved);
        return itemMapper.toItemDto(saved);
    }

    @Override
    public ItemDto updateItem(String itemId, ItemDto itemDto) {
        ItemDocument document = getItemDocument(itemId);
        buildItemDocument(document, itemDto);
        document.setUpdatedAt(LocalDateTime.now());
        ItemDocument saved = itemRepository.save(document);
        indexIfAvailable(saved);
        return itemMapper.toItemDto(saved);
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
        return itemRepository.findAll().stream()
                .map(itemMapper::toItemDto)
                .filter(item -> item.getSku() != null)
                .collect(Collectors.collectingAndThen(Collectors.toMap(
                        ItemDto::getSku,
                        item -> item,
                        (first, duplicate) -> first,
                        java.util.LinkedHashMap::new
                ), map -> new ArrayList<>(map.values())));
    }

    @Override
    public List<String> getCategories() {
        return itemRepository.findAll().stream()
                .map(itemMapper::toItemDto)
                .map(ItemDto::getCategory)
                .filter(category -> category != null && !category.isBlank())
                .map(category -> category.trim())
                .collect(Collectors.collectingAndThen(Collectors.toCollection(java.util.LinkedHashSet::new), ArrayList::new));
    }

    @Override
    public List<ItemDto> getItemsByCategory(String category, int limit) {
        final String normalizedCategory = category == null ? "" : category.trim();
        final int cappedLimit = Math.max(1, Math.min(limit, 60));
        return itemRepository.findAll().stream()
                .map(itemMapper::toItemDto)
                .filter(item -> item.getSku() != null)
                .filter(item -> item.getCategory() != null && item.getCategory().trim().equalsIgnoreCase(normalizedCategory))
                .collect(Collectors.collectingAndThen(Collectors.toList(), items -> items.stream()
                        .limit(cappedLimit)
                        .collect(Collectors.toList())));
    }

    @Override
    public PageResponse<ItemDto> getItemsByCategoryPage(String category, int page, int size) {
        int cappedSize = Math.max(1, Math.min(size, 60));
        int cappedPage = Math.max(0, page);
        Page<ItemDocument> result = itemRepository.findByCategoryIgnoreCase(
                category == null ? "" : category.trim(),
                PageRequest.of(cappedPage, cappedSize, Sort.by(Sort.Direction.ASC, "itemName")));
        List<ItemDto> content = result.getContent().stream()
                .map(itemMapper::toItemDto)
                .collect(Collectors.toList());
        return new PageResponse<>(content, cappedPage, cappedSize, result.getTotalElements());
    }

    @Override
    public PageResponse<ItemDto> getAllItemsPage(int page, int size) {
        int cappedSize = Math.max(1, Math.min(size, 60));
        int cappedPage = Math.max(0, page);
        Page<ItemDocument> result = itemRepository.findAll(
                PageRequest.of(cappedPage, cappedSize, Sort.by(Sort.Direction.ASC, "itemName")));
        List<ItemDto> content = result.getContent().stream()
                .map(itemMapper::toItemDto)
                .filter(item -> item.getSku() != null)
                .collect(Collectors.toList());
        return new PageResponse<>(content, cappedPage, cappedSize, result.getTotalElements());
    }

    @Override
    public PageResponse<ItemDto> searchItemsPage(String query, String category, String brand, Boolean inStock, int page, int size) {
        if (query == null || query.isBlank()) {
            return getAllItemsPage(page, size);
        }
        if (itemSearchService == null) {
            throw new IllegalStateException("Elasticsearch search is not available");
        }
        return itemSearchService.searchPage(query, inStock, page, size);
    }

    @Override
    public List<ItemDto> searchItems(String query, String category, String brand, Boolean inStock, int limit) {
        if (query == null || query.isBlank()) {
            return getAllItems().stream()
                    .limit(Math.max(1, Math.min(limit, 60)))
                    .collect(Collectors.toList());
        }
        if (itemSearchService == null) {
            throw new IllegalStateException("Elasticsearch search is not available");
        }
        return itemSearchService.search(query, inStock, limit);
    }

    @Override
    public InventoryDto updateInventory(String itemId, InventoryDto inventoryDto) {
        ItemDocument document = getItemDocument(itemId);
        document.setInventory(itemMapper.toInventoryDocument(inventoryDto));
        document.setUpdatedAt(LocalDateTime.now());
        ItemDocument saved = itemRepository.save(document);
        indexIfAvailable(saved);
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

        ItemDocument adjusted = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), ItemDocument.class);
        if (adjusted == null) {
            throw new IllegalStateException("Inventory adjustment could not be applied for sku " + sku);
        }
        refreshInStock(adjusted.getInventory());
        // Update only the derived inStock flag — never save the full document, which would
        // overwrite concurrent findAndModify decrements from other threads.
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("sku").is(sku)),
                new Update().set("inventory.in_stock", adjusted.getInventory() != null && Boolean.TRUE.equals(adjusted.getInventory().getInStock())),
                ItemDocument.class);
        indexIfAvailable(adjusted);
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

    private void indexIfAvailable(ItemDocument saved) {
        if (itemSearchIndexer != null) {
            itemSearchIndexer.index(saved);
        }
    }
}
