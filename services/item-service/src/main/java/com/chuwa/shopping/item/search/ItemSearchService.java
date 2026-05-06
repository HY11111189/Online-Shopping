package com.chuwa.shopping.item.search;

import com.chuwa.shopping.dto.PageResponse;
import com.chuwa.shopping.dto.item.ItemDto;
import com.chuwa.shopping.dto.item.InventoryDto;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "shopping.search.elasticsearch.enabled", havingValue = "true", matchIfMissing = true)
public class ItemSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    public ItemSearchService(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public PageResponse<ItemDto> searchPage(String query, Boolean inStock, int page, int size) {
        int cappedSize = Math.max(1, Math.min(size, 60));
        int cappedPage = Math.max(0, page);
        BoolQueryBuilder outerBool = buildQuery(query, inStock);

        NativeSearchQuery countQuery = new NativeSearchQueryBuilder()
                .withQuery(outerBool)
                .withPageable(PageRequest.of(0, 1))
                .build();
        long totalHits = elasticsearchOperations.search(countQuery, ItemSearchDocument.class).getTotalHits();

        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                .withQuery(outerBool)
                .withPageable(PageRequest.of(cappedPage, cappedSize))
                .build();
        SearchHits<ItemSearchDocument> hits = elasticsearchOperations.search(searchQuery, ItemSearchDocument.class);
        List<ItemDto> content = hits.stream()
                .map(SearchHit::getContent)
                .map(this::toItemDto)
                .collect(Collectors.toList());
        return new PageResponse<>(content, cappedPage, cappedSize, totalHits);
    }

    public List<ItemDto> search(String query, Boolean inStock, int limit) {
        BoolQueryBuilder outerBool = buildQuery(query, inStock);

        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                .withQuery(outerBool)
                .withPageable(PageRequest.of(0, Math.max(1, Math.min(limit * 2, 60))))
                .build();

        SearchHits<ItemSearchDocument> hits = elasticsearchOperations.search(searchQuery, ItemSearchDocument.class);
        List<ItemDto> results = hits.stream()
                .map(SearchHit::getContent)
                .map(this::toItemDto)
                .collect(Collectors.toList());
        return dedupeAndLimit(results, limit);
    }

    private BoolQueryBuilder buildQuery(String query, Boolean inStock) {
        BoolQueryBuilder outerBool = QueryBuilders.boolQuery();

        if (query != null && !query.isBlank()) {
            String q = query.trim();

            BoolQueryBuilder textQuery = QueryBuilders.boolQuery()
                    // 1. Exact SKU lookup — highest priority
                    .should(QueryBuilders.termQuery("sku", q).boost(20.0f))
                    // 2. Name and description should match naturally first
                    .should(QueryBuilders.matchQuery("itemName", q).operator(Operator.OR).boost(10.0f))
                    .should(QueryBuilders.matchQuery("description", q).operator(Operator.OR).boost(5.0f))
                    .should(QueryBuilders.matchQuery("category.text", q).operator(Operator.OR).boost(4.0f))
                    // 3. Search-as-you-type: prefix / n-gram autocomplete on itemName.suggest sub-fields
                    .should(QueryBuilders.multiMatchQuery(q)
                            .field("itemName.suggest")
                            .field("itemName.suggest._2gram")
                            .field("itemName.suggest._3gram")
                            .type(MultiMatchQueryBuilder.Type.BOOL_PREFIX)
                            .boost(9.0f))
                    // 4. Phrase prefix on itemName for high-precision partial matches
                    .should(QueryBuilders.matchPhrasePrefixQuery("itemName", q).boost(8.0f))
                    // 5. Broader best-fields matching across the main product fields
                    .should(QueryBuilders.multiMatchQuery(q)
                            .field("itemName", 3.0f)
                            .field("brand", 2.0f)
                            .field("description", 2.0f)
                            .field("category.text", 2.0f)
                            .type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
                            .operator(Operator.OR)
                            .fuzziness(Fuzziness.AUTO)
                            .prefixLength(1)
                            .boost(4.0f))
                    .should(QueryBuilders.multiMatchQuery(q)
                            .field("itemName", 2.0f)
                            .field("brand", 1.5f)
                            .field("description", 1.5f)
                            .field("category.text", 1.5f)
                            .type(MultiMatchQueryBuilder.Type.CROSS_FIELDS)
                            .operator(Operator.OR)
                            .boost(3.0f))
                    .minimumShouldMatch(1);

            // Only for single-word queries: wildcard on itemNameNoSpace so a compound word like
            // "houseware" matches items stored as "house ware" (indexed with spaces stripped).
            if (!q.contains(" ")) {
                textQuery.should(QueryBuilders.wildcardQuery("itemNameNoSpace", "*" + q.toLowerCase() + "*").boost(6.0f));
            }

            // function_score: boost in-stock and discounted items without zeroing out others
            // boostMode=SUM adds function bonuses on top of query score rather than multiplying
            FunctionScoreQueryBuilder.FilterFunctionBuilder[] functions = {
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            QueryBuilders.termQuery("inStock", true),
                            ScoreFunctionBuilders.weightFactorFunction(2.0f)
                    ),
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            QueryBuilders.rangeQuery("discountPercent").gt(0),
                            ScoreFunctionBuilders.weightFactorFunction(1.5f)
                    )
            };

            outerBool.must(QueryBuilders.functionScoreQuery(textQuery, functions)
                    .boostMode(CombineFunction.SUM)
                    .scoreMode(FunctionScoreQuery.ScoreMode.SUM));

        } else {
            outerBool.must(QueryBuilders.matchAllQuery());
        }

        if (inStock != null) {
            outerBool.filter(QueryBuilders.termQuery("inStock", inStock));
        }

        return outerBool;
    }

    private ItemDto toItemDto(ItemSearchDocument doc) {
        ItemDto dto = new ItemDto();
        dto.setId(doc.getId());
        dto.setSku(doc.getSku());
        dto.setUpc(doc.getUpc());
        dto.setItemName(doc.getItemName());
        dto.setBrand(doc.getBrand());
        dto.setCategory(doc.getCategory());
        dto.setDescription(doc.getDescription());
        dto.setUnitPrice(doc.getUnitPrice());
        dto.setDiscountPercent(doc.getDiscountPercent());
        dto.setCurrencyCode(doc.getCurrencyCode());
        dto.setPictureUrls(doc.getPictureUrls());
        dto.setAttributes(doc.getAttributes());
        InventoryDto inventory = new InventoryDto();
        inventory.setAvailableQuantity(doc.getAvailableQuantity());
        inventory.setInStock(doc.getInStock());
        dto.setInventory(inventory);
        return dto;
    }

    private List<ItemDto> dedupeAndLimit(List<ItemDto> items, int limit) {
        Map<String, ItemDto> unique = new LinkedHashMap<>();
        for (ItemDto item : items) {
            if (item == null || item.getSku() == null || unique.containsKey(item.getSku())) continue;
            unique.put(item.getSku(), item);
        }
        return new ArrayList<>(unique.values()).stream()
                .limit(Math.max(1, Math.min(limit, 60)))
                .collect(Collectors.toList());
    }
}
