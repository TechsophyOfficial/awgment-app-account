package com.techsophy.tsf.account.config;

import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.idgenerator.exception.NodeLimitException;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdGeneratorConfigTest {

    @Test
    void shouldGenerateUniquePositiveMonotonicSnowflakeIds() {
        IdGeneratorConfig config = new IdGeneratorConfig();
        IdGeneratorImpl generator = config.idGenerator(-1);

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

    @Test
    void shouldHonorExplicitDeterministicNodeIdForMultiReplicaDeploymentsAndRejectOutOfRange() {
        IdGeneratorConfig config = new IdGeneratorConfig();
        IdGeneratorImpl generator = config.idGenerator(42);
        BigInteger id = generator.nextId();
        assertThat(generator.parse(id.longValue())).containsEntry("nodeId", 42L);

        assertThatThrownBy(() -> config.idGenerator(1024))
                .isInstanceOf(NodeLimitException.class);
    }

    @Test
    void shouldGenerateZeroDuplicatesUnderConcurrentMultiThreadedContention() throws Exception {
        IdGeneratorImpl generator = new IdGeneratorConfig().idGenerator(7);
        int threads = 8;
        int idsPerThread = 250;
        Set<BigInteger> allIds = ConcurrentHashMap.newKeySet();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                Callable<Integer> task = () -> {
                    ready.countDown();
                    start.await();
                    int added = 0;
                    for (int i = 0; i < idsPerThread; i++) {
                        BigInteger id = generator.nextId();
                        assertThat(id).isNotNull().isGreaterThan(BigInteger.ZERO);
                        if (allIds.add(id)) {
                            added++;
                        }
                    }
                    return added;
                };
                futures.add(pool.submit(task));
            }
            ready.await();
            start.countDown();

            int totalAdded = 0;
            for (Future<Integer> future : futures) {
                totalAdded += future.get();
            }
            assertThat(totalAdded).isEqualTo(threads * idsPerThread);
            assertThat(allIds).hasSize(threads * idsPerThread);
        }
    }
}
