package com.globalbank.orderservice.api;

import com.globalbank.orderservice.adapters.persistence.PaymentRepository;
import com.globalbank.orderservice.core.domain.OrderStatus;
import com.globalbank.orderservice.core.domain.Payment;
import com.globalbank.orderservice.core.domain.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CancelOrderControllerIT {

    @Autowired TestRestTemplate restTemplate;
    @Autowired PaymentRepository paymentRepository;

    @Test
    void cancel_happyPath() {
        Long orderId = createOrder();

        ResponseEntity<OrderResponse> response = postCancel(orderId, "{\"reasonCode\":\"CUSTOMER_REQUEST\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        OrderResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(body.cancellationReason()).isEqualTo("CUSTOMER_REQUEST");
        assertThat(body.cancelledAt()).isNotNull();
        assertThat(body.amount()).isEqualByComparingTo("250.00");
    }

    @Test
    void cancel_orderNotFound_returns404() {
        ResponseEntity<Map> response = postCancelRaw(99999L, "{\"reasonCode\":\"CUSTOMER_REQUEST\"}", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    void cancel_alreadyCancelled_returns409() {
        Long orderId = createOrder();
        postCancel(orderId, "{\"reasonCode\":\"CUSTOMER_REQUEST\"}");

        ResponseEntity<Map> response = postCancelRaw(orderId, "{\"reasonCode\":\"CUSTOMER_REQUEST\"}", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("ORDER_NOT_CANCELLABLE");
    }

    @Test
    void cancel_paymentCaptured_returns409() {
        Long orderId = createOrder();
        paymentRepository.save(new Payment(orderId, new BigDecimal("250.00"), PaymentStatus.CAPTURED));

        ResponseEntity<Map> response = postCancelRaw(orderId, "{\"reasonCode\":\"CUSTOMER_REQUEST\"}", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("PAYMENT_ALREADY_CAPTURED");
    }

    @Test
    void cancel_invalidReasonCode_returns400() {
        Long orderId = createOrder();

        ResponseEntity<Map> response = postCancelRaw(orderId, "{\"reasonCode\":\"BOGUS\"}", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_REASON_CODE");
    }

    @Test
    void cancel_unknownField_returns400() {
        Long orderId = createOrder();

        ResponseEntity<Map> response = postCancelRaw(orderId,
                "{\"reasonCode\":\"CUSTOMER_REQUEST\",\"unexpected\":\"field\"}", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("UNKNOWN_FIELDS");
    }

    @Test
    void cancel_errorBody_doesNotContainCustomerId() {
        ResponseEntity<Map> response = postCancelRaw(99999L, "{\"reasonCode\":\"CUSTOMER_REQUEST\"}", Map.class);

        assertThat(response.getBody()).doesNotContainKey("customerId");
    }

    private Long createOrder() {
        var request = new CreateOrderRequest("CUST-001", new BigDecimal("250.00"));
        var created = restTemplate.postForEntity("/orders", request, OrderResponse.class);
        assertThat(created.getBody()).isNotNull();
        return created.getBody().id();
    }

    private ResponseEntity<OrderResponse> postCancel(Long orderId, String json) {
        return postCancelRaw(orderId, json, OrderResponse.class);
    }

    private <T> ResponseEntity<T> postCancelRaw(Long orderId, String json, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(json, headers);
        return restTemplate.exchange("/orders/" + orderId + "/cancel", HttpMethod.POST, entity, responseType);
    }
}
