package com.rafaelnontol.mcpclient.client;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdGeneratorTest {

    @Test
    void generatesCorrelativeIdsStartingAtOne() {
        RequestIdGenerator generator = new RequestIdGenerator();

        assertThat(generator.nextId()).isEqualTo(1);
        assertThat(generator.nextId()).isEqualTo(2);
        assertThat(generator.nextId()).isEqualTo(3);
    }

    @Test
    void neverRepeatsIdsUnderConcurrency() {
        RequestIdGenerator generator = new RequestIdGenerator();
        Set<Long> ids = ConcurrentHashMap.newKeySet();

        IntStream.range(0, 10_000).parallel().forEach(i -> ids.add(generator.nextId()));

        assertThat(ids).hasSize(10_000);
    }
}
