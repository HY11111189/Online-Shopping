package com.chuwa.shopping.agent.controller;

import com.chuwa.shopping.assistant.dto.ShoppingAssistantRequestDto;
import com.chuwa.shopping.assistant.dto.ShoppingAssistantResponseDto;
import com.chuwa.shopping.agent.service.ShoppingAgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shopping/assistant/agent")
public class ShoppingAgentController {

    private final ShoppingAgentService shoppingAgentService;

    public ShoppingAgentController(ShoppingAgentService shoppingAgentService) {
        this.shoppingAgentService = shoppingAgentService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ShoppingAssistantResponseDto> chat(@RequestBody ShoppingAssistantRequestDto requestDto,
                                                             Authentication authentication) {
        return ResponseEntity.ok(shoppingAgentService.chat(requestDto, authentication));
    }
}
