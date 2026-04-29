package com.chuwa.shopping.client;

import com.chuwa.shopping.dto.item.InventoryAdjustmentRequestDto;
import com.chuwa.shopping.dto.item.InventoryAdjustmentResultDto;
import com.chuwa.shopping.dto.item.ItemDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "item-service-client", url = "${shopping.services.item.base-url}")
public interface ItemServiceClient {

    @GetMapping("/internal/api/v1/shopping/items/sku/{sku}")
    ItemDto getItemBySku(@PathVariable("sku") String sku);

    @PostMapping("/internal/api/v1/shopping/items/sku/{sku}/inventory/adjustments")
    InventoryAdjustmentResultDto adjustInventory(@PathVariable("sku") String sku,
                                                 @RequestBody InventoryAdjustmentRequestDto requestDto);
}
