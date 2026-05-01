package com.chuwa.shopping.assistant.dto;

public class ShoppingAssistantRequestDto {

    private String message;
    private String selectedAction;
    private String selectedSku;
    private String selectedItemName;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSelectedAction() {
        return selectedAction;
    }

    public void setSelectedAction(String selectedAction) {
        this.selectedAction = selectedAction;
    }

    public String getSelectedSku() {
        return selectedSku;
    }

    public void setSelectedSku(String selectedSku) {
        this.selectedSku = selectedSku;
    }

    public String getSelectedItemName() {
        return selectedItemName;
    }

    public void setSelectedItemName(String selectedItemName) {
        this.selectedItemName = selectedItemName;
    }
}
