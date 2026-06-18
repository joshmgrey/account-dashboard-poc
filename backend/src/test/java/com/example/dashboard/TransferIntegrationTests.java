package com.example.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.example.dashboard.account.Account;
import com.example.dashboard.account.AccountStore;
import com.example.dashboard.transfer.IdempotencyKeyStore;
import com.example.dashboard.transfer.TransactionStore;
import com.example.dashboard.transfer.TransferStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        // Each test logs in, so the per-IP auth rate-limit budget (default 8/min)
        // is exhausted partway through the class and later logins get 429'd.
        // Raise it generously for the test context only.
        "app.ratelimit.auth-capacity=1000",
        "app.ratelimit.auth-refill-per-minute=1000"
})
@AutoConfigureMockMvc
class TransferIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private Cookie loginAs(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginPayload(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("ACCESS_TOKEN");
        if (cookie == null) {
            throw new IllegalStateException("No ACCESS_TOKEN cookie in login response");
        }
        return cookie;
    }

    private ResultActions postTransfer(Cookie auth, String sourceId, String idempotencyKey,
                                       String destination, String amount) throws Exception {
        return mockMvc.perform(post("/api/accounts/{accountId}/transfers", sourceId)
                .cookie(auth)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new TransferPayload(destination, new BigDecimal(amount)))));
    }

    private BigDecimal getBalance(Cookie auth, String accountId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/accounts/{id}", accountId)
                        .cookie(auth))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new BigDecimal(body.get("balance").asText());
    }

    private String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    @Test
    void happyPath_returns201AndMovesBalances() throws Exception {
        Cookie auth = loginAs("alice", "Password123!");

        BigDecimal balanceBefore = getBalance(auth, "ACC-1001");

        postTransfer(auth, "ACC-1001", newIdempotencyKey(), "ACC-2001", "100.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transfer.id").exists())
                .andExpect(jsonPath("$.transfer.amount").value(100.00))
                .andExpect(jsonPath("$.transfer.status").value("COMPLETED"))
                .andExpect(jsonPath("$.message").value("Transfer created"));

        BigDecimal balanceAfter = getBalance(auth, "ACC-1001");
        assertThat(balanceBefore.subtract(balanceAfter)).isEqualByComparingTo("100.00");
    }

    @Test
    void sourceNotOwnedByCaller_returns404() throws Exception {
        Cookie auth = loginAs("alice", "Password123!");

        postTransfer(auth, "ACC-2001", newIdempotencyKey(), "ACC-1001", "100.00")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Source account not found"));
    }

    @Test
    void destinationNotFound_returns404() throws Exception {
        Cookie auth = loginAs("alice", "Password123!");

        postTransfer(auth, "ACC-1001", newIdempotencyKey(), "ACC-9999", "100.00")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Destination account not found"));
    }

    @Test
    void sourceEqualsDestination_returns422() throws Exception {
        Cookie auth = loginAs("alice", "Password123!");

        postTransfer(auth, "ACC-1001", newIdempotencyKey(), "ACC-1001", "100.00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Source and destination accounts must differ"));
    }

    @Test
    void sourceNotActive_returns422() throws Exception {
        Cookie auth = loginAs("alice", "Password123!");
        accountStore.save(new Account("ACC-1004", "alice", "Inactive", "CHECKING", "USD",
                new BigDecimal("1000.00"), "FROZEN"));

        postTransfer(auth, "ACC-1004", newIdempotencyKey(), "ACC-2001", "100.00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Source account is not active"));
    }

    @Test
    void destinationNotActive_returns422() throws Exception {
        Cookie auth = loginAs("alice", "Password123!");

        postTransfer(auth, "ACC-1001", newIdempotencyKey(), "ACC-2002", "100.00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Destination account is not active"));
    }

    @Test
    void currencyMismatch_returns422() throws Exception {
        Cookie auth = loginAs("alice", "Password123!");
        accountStore.save(new Account("ACC-1005", "alice", "Euro", "CHECKING", "EUR",
                new BigDecimal("1000.00"), "ACTIVE"));

        postTransfer(auth, "ACC-1001", newIdempotencyKey(), "ACC-1005", "100.00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Source and destination currencies must match"));
    }

    @Test
    void amountZero_returns422() throws Exception {
        Cookie auth = loginAs("alice", "Password123!");

        postTransfer(auth, "ACC-1001", newIdempotencyKey(), "ACC-2001", "0")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Amount must be greater than zero"));
    }

    @Test
    void amountExceedsMaximum_returns422() throws Exception {
        Cookie auth = loginAs("alice", "Password123!");

        postTransfer(auth, "ACC-1001", newIdempotencyKey(), "ACC-2001", "25001")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Amount exceeds the maximum transfer limit"));
    }

    @Test
    void amountWithTooManyDecimals_returns422() throws Exception {
        Cookie auth = loginAs("alice", "Password123!");

        postTransfer(auth, "ACC-1001", newIdempotencyKey(), "ACC-2001", "100.001")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Amount cannot have more than two decimal places"));
    }

    @Test
    void insufficientBalance_returns422() throws Exception {
        Cookie auth = loginAs("alice", "Password123!");
        accountStore.save(new Account("ACC-1001", "alice", "Operating Checking", "CHECKING", "USD",
                new BigDecimal("50.00"), "ACTIVE"));

        postTransfer(auth, "ACC-1001", newIdempotencyKey(), "ACC-2001", "100.00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Insufficient balance"));
    }

    @Test
    void idempotencyReplay_sameRequestReturns200WithSameTransfer() throws Exception {
        Cookie auth = loginAs("alice", "Password123!");
        String idempotencyKey = newIdempotencyKey();

        BigDecimal balanceBefore = getBalance(auth, "ACC-1001");

        MvcResult firstResult = postTransfer(auth, "ACC-1001", idempotencyKey, "ACC-2001", "100.00")
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode firstBody = objectMapper.readTree(firstResult.getResponse().getContentAsString());
        String originalTransferId = firstBody.get("transfer").get("id").asText();

        BigDecimal balanceAfterFirst = getBalance(auth, "ACC-1001");
        assertThat(balanceBefore.subtract(balanceAfterFirst)).isEqualByComparingTo("100.00");

        postTransfer(auth, "ACC-1001", idempotencyKey, "ACC-2001", "100.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Transfer already processed"))
                .andExpect(jsonPath("$.transfer.id").value(originalTransferId));

        BigDecimal balanceAfterReplay = getBalance(auth, "ACC-1001");
        assertThat(balanceAfterReplay).isEqualByComparingTo(balanceAfterFirst);
    }

    @Test
    void idempotencyConflict_differentRequestReturns409() throws Exception {
        Cookie auth = loginAs("alice", "Password123!");
        String idempotencyKey = newIdempotencyKey();

        postTransfer(auth, "ACC-1001", idempotencyKey, "ACC-2001", "100.00")
                .andExpect(status().isCreated());

        postTransfer(auth, "ACC-1001", idempotencyKey, "ACC-2001", "200.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Idempotency key already used with a different request"));
    }

    @Test
    void missingIdempotencyKey_returns500() throws Exception {
        // Spring throws MissingRequestHeaderException for the missing required header,
        // which falls through to the catch-all Exception handler. Production should
        // map this to 400 Bad Request via a dedicated handler.
        Cookie auth = loginAs("alice", "Password123!");

        mockMvc.perform(post("/api/accounts/{accountId}/transfers", "ACC-1001")
                        .cookie(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransferPayload("ACC-2001", new BigDecimal("100.00")))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    private record LoginPayload(String username, String password) {
    }

    private record TransferPayload(String destination, BigDecimal amount) {
    }
}
