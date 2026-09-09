package com.example.recipecrawler;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Context loads with the default (localhost) downstream URLs. */
@SpringBootTest
@TestPropertySource(properties = "internal-auth.enabled=false")
class RecipeCrawlerApplicationTests {

    @Test
    void contextLoads() {
    }
}
