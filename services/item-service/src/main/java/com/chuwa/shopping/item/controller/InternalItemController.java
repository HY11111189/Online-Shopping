package com.chuwa.shopping.item.controller;

import com.chuwa.shopping.dto.item.InventoryAdjustmentRequestDto;
import com.chuwa.shopping.dto.item.InventoryAdjustmentResultDto;
import com.chuwa.shopping.dto.item.ItemDto;
import com.chuwa.shopping.item.service.ItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/api/v1/shopping/items")
public class InternalItemController {

    private final ItemService itemService;

    public InternalItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<ItemDto> getItemBySku(@PathVariable String sku) {
        return ResponseEntity.ok(itemService.getItemBySku(sku));
    }

    @PostMapping("/sku/{sku}/inventory/adjustments")
    public ResponseEntity<InventoryAdjustmentResultDto> adjustInventory(@PathVariable String sku,
                                                                        @RequestBody InventoryAdjustmentRequestDto requestDto) {
        return ResponseEntity.ok(itemService.adjustInventory(sku, requestDto));
    }
}
