package com.globalbank.orderservice.api;

import com.globalbank.orderservice.core.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void postThenGetRoundTrips() {
        var request = new CreateOrderRequest("CUST-001", new BigDecimal("250.00"));

        var created = restTemplate.postForEntity("/orders", request, OrderResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().id()).isNotNull();

        var id = created.getBody().id();

        var fetched = restTemplate.getForEntity("/orders/" + id, OrderResponse.class);

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody()).isNotNull();
        assertThat(fetched.getBody().customerId()).isEqualTo("CUST-001");
        assertThat(fetched.getBody().amount()).isEqualByComparingTo("250.00");
        assertThat(fetched.getBody().status()).isEqualTo(OrderStatus.CREATED);
    }
}
