package com.chuwa.shopping.item.controller;

import com.chuwa.shopping.dto.item.InventoryDto;
import com.chuwa.shopping.dto.item.ItemDto;
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

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(itemService.getCategories());
    }

    @GetMapping("/search")
    public ResponseEntity<List<ItemDto>> searchItems(@RequestParam(value = "q", required = false) String query,
                                                     @RequestParam(value = "category", required = false) String category,
                                                     @RequestParam(value = "brand", required = false) String brand,
                                                     @RequestParam(value = "inStock", required = false) Boolean inStock,
                                                     @RequestParam(value = "limit", defaultValue = "24") int limit) {
        return ResponseEntity.ok(itemService.searchItems(query, category, brand, inStock, limit));
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
