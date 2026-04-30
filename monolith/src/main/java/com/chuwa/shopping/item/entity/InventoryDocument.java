package com.chuwa.shopping.item.entity;

import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

public class InventoryDocument {

    @Field("total_quantity")
    private Integer totalQuantity;

    @Field("available_quantity")
    private Integer availableQuantity;

    @Field("reserved_quantity")
    private Integer reservedQuantity;

    @Field("reorder_level")
    private Integer reorderLevel;

    @Field("warehouse_code")
    private String warehouseCode;

    @Field("in_stock")
    private Boolean inStock;

    @Field("last_restocked_at")
    private LocalDateTime lastRestockedAt;

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Integer totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(Integer availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public Integer getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(Integer reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public Integer getReorderLevel() {
        return reorderLevel;
    }

    public void setReorderLevel(Integer reorderLevel) {
        this.reorderLevel = reorderLevel;
    }

    public String getWarehouseCode() {
        return warehouseCode;
    }

    public void setWarehouseCode(String warehouseCode) {
        this.warehouseCode = warehouseCode;
    }

    public Boolean getInStock() {
        return inStock;
    }

    public void setInStock(Boolean inStock) {
        this.inStock = inStock;
    }

    public LocalDateTime getLastRestockedAt() {
        return lastRestockedAt;
    }

    public void setLastRestockedAt(LocalDateTime lastRestockedAt) {
        this.lastRestockedAt = lastRestockedAt;
    }
}
