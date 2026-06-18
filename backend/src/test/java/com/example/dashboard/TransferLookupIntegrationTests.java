package com.example.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.dashboard.account.AccountStore;
import com.example.dashboard.transfer.IdempotencyKeyStore;
import com.example.dashboard.transfer.Transfer;
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
class TransferLookupIntegrationTests {

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

    private String createTransfer(Cookie auth, String sourceId, String destination,
                                  String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/accounts/{accountId}/transfers", sourceId)
                        .cookie(auth)
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransferPayload(destination, new BigDecimal(amount)))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("transfer").get("id").asText();
    }

    @Test
    void sourceOwnerCanViewTransfer_returns200() throws Exception {
        Cookie bob = loginAs("bob", "Password123!");
        String transferId = createTransfer(bob, "ACC-2001", "ACC-1001", "100.00");

        mockMvc.perform(get("/api/transfers/{id}", transferId)
                        .cookie(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source.id").value("ACC-2001"))
                .andExpect(jsonPath("$.source.owner").value("bob"))
                .andExpect(jsonPath("$.destination.id").value("ACC-1001"))
                .andExpect(jsonPath("$.destination.owner").value("alice"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void destinationOwnerCanViewTransfer_returns200() throws Exception {
        Cookie bob = loginAs("bob", "Password123!");
        String transferId = createTransfer(bob, "ACC-2001", "ACC-1001", "100.00");

        Cookie alice = loginAs("alice", "Password123!");

        mockMvc.perform(get("/api/transfers/{id}", transferId)
                        .cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source.id").value("ACC-2001"))
                .andExpect(jsonPath("$.source.owner").value("bob"))
                .andExpect(jsonPath("$.destination.id").value("ACC-1001"))
                .andExpect(jsonPath("$.destination.owner").value("alice"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void thirdPartyGets404_notLeakingExistence() throws Exception {
        // Inject a synthetic bob->bob transfer directly via the store so we can
        // create a record that wouldn't pass the transfer validation rules.
        transferStore.save(new Transfer("synthetic-test-id", "ACC-2001", "ACC-2002",
                new BigDecimal("100.00"), "USD", Transfer.Status.COMPLETED,
                Instant.now(), "synthetic-key"));

        Cookie alice = loginAs("alice", "Password123!");

        // IDOR-prevention: same response as a genuinely missing transfer, so an
        // attacker cannot infer existence by probing IDs.
        mockMvc.perform(get("/api/transfers/{id}", "synthetic-test-id")
                        .cookie(alice))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transfer not found"));
    }

    @Test
    void nonexistentTransferReturns404() throws Exception {
        Cookie alice = loginAs("alice", "Password123!");

        mockMvc.perform(get("/api/transfers/{id}", "genuinely-nonexistent-id")
                        .cookie(alice))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transfer not found"));
    }

    @Test
    void unauthenticatedAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/transfers/{id}", "any-id"))
                .andExpect(status().isUnauthorized());
    }

    private record LoginPayload(String username, String password) {
    }

    private record TransferPayload(String destination, BigDecimal amount) {
    }
}
