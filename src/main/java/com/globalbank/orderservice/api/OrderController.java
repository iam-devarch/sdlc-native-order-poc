package com.globalbank.orderservice.api;

import com.globalbank.orderservice.core.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@RequestBody CreateOrderRequest request) {
        var order = orderService.create(request.customerId(), request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getById(@PathVariable Long id) {
        return orderService.findById(id)
                .map(OrderResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // TODO Step 5: add @PreAuthorize("hasAnyRole('senior-ops','supervisor')") once SecurityConfig is wired
    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancel(
            @PathVariable Long id,
            @RequestBody CancelOrderRequest request,
            Principal principal) {
        String agentId = (principal != null) ? principal.getName() : "unresolved";
        var order = orderService.cancel(id, agentId, request.reasonCode());
        return ResponseEntity.ok(OrderResponse.from(order));
    }
}
