package com.chuwa.shopping.shared.client;

import com.chuwa.shopping.item.dto.ItemDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "item-service-client", url = "${shopping.services.item.base-url}")
public interface ItemServiceClient {

    @GetMapping("/internal/api/v1/shopping/items/sku/{sku}")
    ItemDto getItemBySku(@PathVariable("sku") String sku);
}
