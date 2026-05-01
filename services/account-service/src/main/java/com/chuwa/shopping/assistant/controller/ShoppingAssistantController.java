package com.chuwa.shopping.assistant.controller;

import com.chuwa.shopping.assistant.dto.ShoppingAssistantRequestDto;
import com.chuwa.shopping.assistant.dto.ShoppingAssistantResponseDto;
import com.chuwa.shopping.assistant.service.ShoppingAssistantService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shopping/assistant")
public class ShoppingAssistantController {

    private final ShoppingAssistantService shoppingAssistantService;

    public ShoppingAssistantController(ShoppingAssistantService shoppingAssistantService) {
        this.shoppingAssistantService = shoppingAssistantService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ShoppingAssistantResponseDto> chat(@RequestBody ShoppingAssistantRequestDto requestDto,
                                                             Authentication authentication) {
        return ResponseEntity.ok(shoppingAssistantService.chat(requestDto, authentication));
    }
}
