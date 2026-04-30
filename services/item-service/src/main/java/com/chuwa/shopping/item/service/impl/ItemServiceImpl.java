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
import com.chuwa.shopping.item.search.ItemSearchIndexer;
import com.chuwa.shopping.item.search.ItemSearchService;
import com.chuwa.shopping.item.service.ItemService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    public List<ItemDto> searchItems(String query, String category, String brand, Boolean inStock, int limit) {
        // Elasticsearch only for free-text queries; category/brand browsing goes straight to MongoDB
        boolean hasText = query != null && !query.isBlank();
        if (hasText && itemSearchService != null) {
            try {
                return itemSearchService.search(query, category, brand, inStock, limit);
            } catch (Exception ex) {
                // fall through to MongoDB
            }
        }
        return fallbackSearch(query, category, brand, inStock, limit);
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

        ItemDocument adjusted = mongoTemplate.findAndModify(query, update, ItemDocument.class);
        if (adjusted == null) {
            throw new IllegalStateException("Inventory adjustment could not be applied for sku " + sku);
        }
        adjusted = itemRepository.findBySku(sku)
                .orElseThrow(() -> new ShoppingResourceNotFoundException("Item", "sku", sku));
        refreshInStock(adjusted.getInventory());
        adjusted.setUpdatedAt(LocalDateTime.now());
        ItemDocument saved = itemRepository.save(adjusted);
        indexIfAvailable(saved);
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

    private List<ItemDto> fallbackSearch(String query, String category, String brand, Boolean inStock, int limit) {
        final String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        final String normalizedCategory = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        final String normalizedBrand = brand == null ? "" : brand.trim().toLowerCase(Locale.ROOT);
        final int cappedLimit = Math.max(1, Math.min(limit, 60));

        return itemRepository.findAll().stream()
                .map(itemMapper::toItemDto)
                .filter(item -> item.getSku() != null)
                .filter(item -> normalizedCategory.isEmpty()
                        || matchesCategory(item, normalizedCategory))
                .filter(item -> normalizedBrand.isEmpty()
                        || (item.getBrand() != null && item.getBrand().toLowerCase(Locale.ROOT).contains(normalizedBrand)))
                .filter(item -> inStock == null
                        || (item.getInventory() != null && Boolean.valueOf(inStock).equals(item.getInventory().getInStock())))
                .filter(item -> normalizedQuery.isEmpty() || matchesQuery(item, normalizedQuery))
                .collect(Collectors.collectingAndThen(Collectors.toMap(
                        ItemDto::getSku,
                        item -> item,
                        (first, duplicate) -> first,
                        java.util.LinkedHashMap::new
                ), map -> new ArrayList<>(map.values()).stream().limit(cappedLimit).collect(Collectors.toList())));
    }

    private boolean matchesQuery(ItemDto item, String normalizedQuery) {
        for (String term : expandSearchTerms(normalizedQuery)) {
            if (containsIgnoreCase(item.getItemName(), term)
                    || containsIgnoreCase(item.getBrand(), term)
                    || containsIgnoreCase(item.getCategory(), term)
                    || containsIgnoreCase(item.getDescription(), term)
                    || containsIgnoreCase(item.getSku(), term)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesCategory(ItemDto item, String normalizedCategory) {
        String category = item.getCategory() == null ? "" : item.getCategory().toLowerCase(Locale.ROOT);
        if (category.equals(normalizedCategory) || category.contains(normalizedCategory) || normalizedCategory.contains(category)) {
            return true;
        }
        for (String alias : categoryAliases(normalizedCategory)) {
            if (category.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    private List<String> expandSearchTerms(String normalizedQuery) {
        List<String> terms = new ArrayList<>();
        for (String token : normalizedQuery.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            addTerm(terms, token);
            switch (token) {
                case "kid":
                case "kids":
                case "child":
                case "children":
                case "toddler":
                    addTerm(terms, "baby");
                    addTerm(terms, "toy");
                    addTerm(terms, "toys");
                    addTerm(terms, "games");
                    break;
                case "toy":
                case "toys":
                case "game":
                case "games":
                    addTerm(terms, "kid");
                    addTerm(terms, "kids");
                    addTerm(terms, "baby");
                    break;
                case "grocery":
                    addTerm(terms, "food");
                    addTerm(terms, "essentials");
                    break;
                case "home":
                    addTerm(terms, "garden");
                    addTerm(terms, "tools");
                    break;
                case "fashion":
                    addTerm(terms, "clothing");
                    addTerm(terms, "shoes");
                    break;
                default:
                    break;
            }
        }
        return terms;
    }

    private List<String> categoryAliases(String normalizedCategory) {
        List<String> aliases = new ArrayList<>();
        switch (normalizedCategory) {
            case "grocery & essentials":
            case "grocery":
                addTerm(aliases, "grocery");
                addTerm(aliases, "essentials");
                break;
            case "home & garden":
            case "home":
                addTerm(aliases, "home");
                addTerm(aliases, "garden");
                addTerm(aliases, "tools");
                break;
            case "clothing & shoes":
            case "fashion":
                addTerm(aliases, "clothing");
                addTerm(aliases, "fashion");
                addTerm(aliases, "shoes");
                break;
            case "baby":
            case "baby & kids":
                addTerm(aliases, "baby");
                addTerm(aliases, "kids");
                addTerm(aliases, "kid");
                break;
            case "toys & games":
            case "toy":
            case "toys":
                addTerm(aliases, "toy");
                addTerm(aliases, "toys");
                addTerm(aliases, "games");
                break;
            default:
                addTerm(aliases, normalizedCategory);
                break;
        }
        return aliases;
    }

    private void addTerm(List<String> terms, String term) {
        if (!terms.contains(term)) {
            terms.add(term);
        }
    }

    private boolean containsIgnoreCase(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private void indexIfAvailable(ItemDocument saved) {
        if (itemSearchIndexer != null) {
            itemSearchIndexer.index(saved);
        }
    }
}
