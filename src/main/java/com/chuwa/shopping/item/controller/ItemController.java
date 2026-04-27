package com.chuwa.shopping.item.controller;

import com.chuwa.shopping.item.dto.InventoryDto;
import com.chuwa.shopping.item.dto.ItemDto;
import com.chuwa.shopping.item.service.ItemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shopping/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping
    public ResponseEntity<ItemDto> createItem(@RequestBody ItemDto itemDto) {
        return new ResponseEntity<>(itemService.createItem(itemDto), HttpStatus.CREATED);
    }

    @PutMapping("/{itemId}")
    public ResponseEntity<ItemDto> updateItem(@PathVariable String itemId, @RequestBody ItemDto itemDto) {
        return ResponseEntity.ok(itemService.updateItem(itemId, itemDto));
    }

    @GetMapping("/{itemId}")
    public ResponseEntity<ItemDto> getItem(@PathVariable String itemId) {
        return ResponseEntity.ok(itemService.getItem(itemId));
    }

    @GetMapping
    public ResponseEntity<List<ItemDto>> getItems() {
        return ResponseEntity.ok(itemService.getAllItems());
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<ItemDto> getItemBySku(@PathVariable String sku) {
        return ResponseEntity.ok(itemService.getItemBySku(sku));
    }

    @PutMapping("/{itemId}/inventory")
    public ResponseEntity<InventoryDto> updateInventory(@PathVariable String itemId, @RequestBody InventoryDto inventoryDto) {
        return ResponseEntity.ok(itemService.updateInventory(itemId, inventoryDto));
    }
}
