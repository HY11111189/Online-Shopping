package com.chuwa.shopping.item.search;

import com.chuwa.shopping.dto.item.ItemDto;
import com.chuwa.shopping.item.dao.ItemRepository;
import com.chuwa.shopping.item.entity.ItemDocument;
import com.chuwa.shopping.item.mapper.ItemMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "shopping.search.elasticsearch.enabled", havingValue = "true", matchIfMissing = true)
public class ItemSearchIndexer {

    private static final Logger log = LoggerFactory.getLogger(ItemSearchIndexer.class);

    private final ItemSearchRepository itemSearchRepository;
    private final ItemRepository itemRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ItemMapper itemMapper;

    public ItemSearchIndexer(ItemSearchRepository itemSearchRepository,
                             ItemRepository itemRepository,
                             ElasticsearchOperations elasticsearchOperations,
                             ItemMapper itemMapper) {
        this.itemSearchRepository = itemSearchRepository;
        this.itemRepository = itemRepository;
        this.elasticsearchOperations = elasticsearchOperations;
        this.itemMapper = itemMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        initializeIndex();
    }

    public void initializeIndex() {
        try {
            if (!elasticsearchOperations.indexOps(ItemSearchDocument.class).exists()) {
                elasticsearchOperations.indexOps(ItemSearchDocument.class).createWithMapping();
            }
            syncAll();
        } catch (Exception ex) {
            log.warn("Elasticsearch unavailable during item index initialization; continuing with Mongo-backed item reads", ex);
        }
    }

    public void syncAll() {
        List<ItemSearchDocument> documents = itemRepository.findAll()
                .stream()
                .map(this::toSearchDocument)
                .collect(Collectors.toList());
        itemSearchRepository.saveAll(documents);
    }

    public void index(ItemDocument document) {
        try {
            itemSearchRepository.save(toSearchDocument(document));
        } catch (Exception ex) {
            log.warn("Elasticsearch unavailable while indexing sku {}; continuing without search sync", document.getSku(), ex);
        }
    }

    public ItemSearchDocument toSearchDocument(ItemDocument document) {
        ItemDto dto = itemMapper.toItemDto(document);
        ItemSearchDocument search = new ItemSearchDocument();
        search.setId(dto.getId());
        search.setSku(dto.getSku());
        search.setUpc(dto.getUpc());
        search.setItemName(dto.getItemName());
        search.setItemNameNoSpace(dto.getItemName() != null ? dto.getItemName().toLowerCase().replaceAll("\\s+", "") : null);
        search.setBrand(dto.getBrand());
        search.setCategory(dto.getCategory());
        search.setDescription(dto.getDescription());
        search.setUnitPrice(dto.getUnitPrice());
        search.setDiscountPercent(dto.getDiscountPercent());
        search.setCurrencyCode(dto.getCurrencyCode());
        search.setPictureUrls(dto.getPictureUrls());
        search.setStatus(dto.getStatus() == null ? null : dto.getStatus().name());
        search.setInStock(dto.getInventory() == null ? Boolean.FALSE : dto.getInventory().getInStock());
        search.setAvailableQuantity(dto.getInventory() == null ? 0 : dto.getInventory().getAvailableQuantity());
        search.setAttributes(dto.getAttributes());
        search.setUpdatedAt(dto.getUpdatedAt());
        return search;
    }
}
