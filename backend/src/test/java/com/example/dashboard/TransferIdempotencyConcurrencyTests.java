package com.example.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.dashboard.account.AccountStore;
import com.example.dashboard.transfer.IdempotencyConflictException;
import com.example.dashboard.transfer.IdempotencyKeyStore;
import com.example.dashboard.transfer.TransactionStore;
import com.example.dashboard.transfer.TransferRequest;
import com.example.dashboard.transfer.TransferResult;
import com.example.dashboard.transfer.TransferService;
import com.example.dashboard.transfer.TransferStore;

/**
 * Concurrent requests sharing one Idempotency-Key must execute the transfer
 * exactly once. Every other request either replays the completed transfer or
 * is rejected with a 409-mapped conflict while the first is still in flight.
 */
@SpringBootTest
class TransferIdempotencyConcurrencyTests {

    private static final int THREADS = 20;

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountStore accountStore;

    @Autowired
    private TransferStore transferStore;

    @Autowired
    private TransactionStore transactionStore;

    @Autowired
    private IdempotencyKeyStore idempotencyKeyStore;

    @BeforeEach
    void resetStores() {
        accountStore.clearForTest();
        transferStore.clearForTest();
        transactionStore.clearForTest();
        idempotencyKeyStore.clearForTest();
    }

    @RepeatedTest(10)
    void concurrentRequestsWithSameKey_executeTransferExactlyOnce() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest("ACC-2001", new BigDecimal("100.00"));
        BigDecimal sourceBefore = accountStore.findById("ACC-1001").orElseThrow().balance();
        BigDecimal destinationBefore = accountStore.findById("ACC-2001").orElseThrow().balance();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<TransferResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return transferService.createTransfer("alice", "ACC-1001", idempotencyKey, request);
                }));
            }
            ready.await();
            start.countDown();

            int created = 0;
            int replayed = 0;
            int inFlightConflicts = 0;
            for (Future<TransferResult> future : futures) {
                try {
                    TransferResult result = future.get(10, TimeUnit.SECONDS);
                    if (result.isReplay()) {
                        replayed++;
                    } else {
                        created++;
                    }
                } catch (java.util.concurrent.ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(IdempotencyConflictException.class);
                    inFlightConflicts++;
                }
            }

            assertThat(created).isEqualTo(1);
            assertThat(created + replayed + inFlightConflicts).isEqualTo(THREADS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(accountStore.findById("ACC-1001").orElseThrow().balance())
                .isEqualByComparingTo(sourceBefore.subtract(new BigDecimal("100.00")));
        assertThat(accountStore.findById("ACC-2001").orElseThrow().balance())
                .isEqualByComparingTo(destinationBefore.add(new BigDecimal("100.00")));
        assertThat(transactionStore.findByAccountId("ACC-1001")).hasSize(1);
    }
}
