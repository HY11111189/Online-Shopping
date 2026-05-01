package com.chuwa.shopping.item.search;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned to v5 so analyzer changes can be created fresh on first start.
 * Run `DELETE /shopping-items-v4` in Kibana / curl if you need to force a remap.
 */
@Document(indexName = "shopping-items-v5")
public class ItemSearchDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String sku;

    @Field(type = FieldType.Keyword)
    private String upc;

    /**
     * Multi-field itemName:
     *  - itemName          → standard Text, for full-text search
     *  - itemName.keyword  → Keyword, for exact/sort
     *  - itemName.suggest  → Search_As_You_Type, powers prefix/ngram auto-complete
     */
    @MultiField(
        mainField  = @Field(type = FieldType.Text, analyzer = "english"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword),
            @InnerField(suffix = "suggest", type = FieldType.Search_As_You_Type)
        }
    )
    private String itemName;

    /**
     * Multi-field brand:
     *  - brand         → Text for fuzzy/phrase search
     *  - brand.keyword → Keyword for exact filter
     */
    @MultiField(
        mainField  = @Field(type = FieldType.Text, analyzer = "english"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword)
        }
    )
    private String brand;

    /**
     * Multi-field category:
     *  - category      → Keyword for exact filter (e.g. department click)
     *  - category.text → Text for free-text search (e.g. user types "electronics")
     */
    @MultiField(
        mainField  = @Field(type = FieldType.Keyword),
        otherFields = {
            @InnerField(suffix = "text", type = FieldType.Text, analyzer = "english")
        }
    )
    private String category;

    @Field(type = FieldType.Text, analyzer = "english")
    private String description;

    @Field(type = FieldType.Double)
    private BigDecimal unitPrice;

    @Field(type = FieldType.Integer)
    private Integer discountPercent;

    @Field(type = FieldType.Keyword)
    private String currencyCode;

    @Field(type = FieldType.Keyword)
    private List<String> pictureUrls = new ArrayList<>();

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Boolean)
    private Boolean inStock;

    @Field(type = FieldType.Integer)
    private Integer availableQuantity;

    @Field(type = FieldType.Object)
    private Map<String, String> attributes = new HashMap<>();

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getUpc() { return upc; }
    public void setUpc(String upc) { this.upc = upc; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public Integer getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(Integer discountPercent) { this.discountPercent = discountPercent; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public List<String> getPictureUrls() { return pictureUrls; }
    public void setPictureUrls(List<String> pictureUrls) { this.pictureUrls = pictureUrls; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getInStock() { return inStock; }
    public void setInStock(Boolean inStock) { this.inStock = inStock; }

    public Integer getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(Integer availableQuantity) { this.availableQuantity = availableQuantity; }

    public Map<String, String> getAttributes() { return attributes; }
    public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
