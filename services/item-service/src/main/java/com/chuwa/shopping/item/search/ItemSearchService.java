package com.chuwa.shopping.item.search;

import com.chuwa.shopping.dto.item.ItemDto;
import com.chuwa.shopping.dto.item.InventoryDto;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.DisMaxQueryBuilder;
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
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "shopping.search.elasticsearch.enabled", havingValue = "true", matchIfMissing = true)
public class ItemSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    public ItemSearchService(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public List<ItemDto> search(String query, String category, String brand, Boolean inStock, int limit) {
        BoolQueryBuilder outerBool = QueryBuilders.boolQuery();

        if (query != null && !query.isBlank()) {
            String q = query.trim();
            String expanded = expandQuery(q);

            // dis_max: pick the single best-matching clause; tieBreaker rewards multi-field overlap
            DisMaxQueryBuilder disMax = QueryBuilders.disMaxQuery().tieBreaker(0.3f);

            // 1. Exact SKU lookup — highest priority
            disMax.add(QueryBuilders.termQuery("sku", q).boost(20.0f));

            // 2. Search-as-you-type: prefix / n-gram autocomplete on itemName.suggest sub-fields
            disMax.add(QueryBuilders.multiMatchQuery(q)
                    .field("itemName.suggest")
                    .field("itemName.suggest._2gram")
                    .field("itemName.suggest._3gram")
                    .type(MultiMatchQueryBuilder.Type.BOOL_PREFIX)
                    .boost(10.0f));

            // 3. Phrase prefix on itemName for high-precision partial matches ("cof" → "coffee mug")
            disMax.add(QueryBuilders.matchPhrasePrefixQuery("itemName", q).boost(12.0f));

            // 4. Cross-fields: query terms spread across name/brand/description/category
            //    AND operator means ALL terms must appear somewhere across the named fields
            disMax.add(QueryBuilders.multiMatchQuery(q)
                    .field("itemName", 3.0f)
                    .field("brand", 2.0f)
                    .field("description", 1.0f)
                    .field("category.text", 2.0f)
                    .type(MultiMatchQueryBuilder.Type.CROSS_FIELDS)
                    .operator(Operator.AND)
                    .boost(5.0f));

            // 5. Fuzzy best-fields: tolerates typos up to Levenshtein distance AUTO (1–2 edits)
            disMax.add(QueryBuilders.multiMatchQuery(q)
                    .field("itemName", 2.0f)
                    .field("brand", 1.5f)
                    .field("description", 1.0f)
                    .type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
                    .fuzziness(Fuzziness.AUTO)
                    .prefixLength(2)
                    .boost(2.0f));

            if (!expanded.equals(q)) {
                disMax.add(QueryBuilders.multiMatchQuery(expanded)
                        .field("itemName", 2.0f)
                        .field("brand", 1.5f)
                        .field("description", 1.0f)
                        .field("category.text", 2.0f)
                        .type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
                        .operator(Operator.OR)
                        .boost(1.5f));
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

            outerBool.must(QueryBuilders.functionScoreQuery(disMax, functions)
                    .boostMode(CombineFunction.SUM)
                    .scoreMode(FunctionScoreQuery.ScoreMode.SUM));

        } else {
            outerBool.must(QueryBuilders.matchAllQuery());
        }

        // Filters run in cached bitset context — zero scoring cost
        if (category != null && !category.isBlank()) {
            // category is mapped as Keyword main field — exact term match
            outerBool.filter(QueryBuilders.termQuery("category", category));
        }
        if (brand != null && !brand.isBlank()) {
            outerBool.filter(QueryBuilders.termQuery("brand.keyword", brand));
        }
        if (inStock != null) {
            outerBool.filter(QueryBuilders.termQuery("inStock", inStock));
        }

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

    private String expandQuery(String query) {
        Set<String> terms = new LinkedHashSet<>();
        for (String token : query.toLowerCase().split("[^a-z0-9]+")) {
            if (token.isBlank()) {
                continue;
            }
            terms.add(token);
            switch (token) {
                case "kid":
                case "kids":
                case "child":
                case "children":
                case "toddler":
                    terms.add("baby");
                    terms.add("toy");
                    terms.add("toys");
                    break;
                case "toy":
                case "toys":
                case "game":
                case "games":
                    terms.add("kids");
                    terms.add("baby");
                    break;
                case "grocery":
                    terms.add("food");
                    terms.add("essentials");
                    break;
                case "home":
                    terms.add("garden");
                    terms.add("tools");
                    break;
                case "fashion":
                    terms.add("clothing");
                    terms.add("shoes");
                    break;
                default:
                    break;
            }
        }
        return String.join(" ", terms);
    }
}
