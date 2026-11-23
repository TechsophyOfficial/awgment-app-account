package com.techsophy.tsf.account.config;

import com.techsophy.idgenerator.IdGeneratorImpl;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IdGeneratorConfigTest {

    @Test
    void shouldGenerateUniquePositiveMonotonicSnowflakeIds() {
        IdGeneratorConfig config = new IdGeneratorConfig();
        IdGeneratorImpl generator = config.idGenerator();

        Set<BigInteger> generated = new HashSet<>();
        BigInteger previous = BigInteger.ZERO;
        for (int i = 0; i < 100; i++) {
            BigInteger id = generator.nextId();
            assertThat(id).isNotNull().isGreaterThan(BigInteger.ZERO);
            assertThat(id).isGreaterThan(previous);
            assertThat(generated.add(id)).isTrue();
            previous = id;
        }
    }
}
