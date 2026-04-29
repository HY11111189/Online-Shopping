package com.chuwa.shopping.dto.item;

public class InventoryAdjustmentRequestDto {

    private InventoryAdjustmentType adjustmentType;
    private Integer quantity;
    private String operationId;
    private String reference;

    public InventoryAdjustmentType getAdjustmentType() {
        return adjustmentType;
    }

    public void setAdjustmentType(InventoryAdjustmentType adjustmentType) {
        this.adjustmentType = adjustmentType;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }
}
