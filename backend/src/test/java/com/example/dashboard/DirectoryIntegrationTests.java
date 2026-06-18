package com.example.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;

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
import com.example.dashboard.transfer.TransactionStore;
import com.example.dashboard.transfer.TransferStore;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        // Each test logs in, so the per-IP auth rate-limit budget (default 8/min)
        // is exhausted partway through the class and later logins get 429'd.
        // Raise it generously for the test context only.
        "app.ratelimit.auth-capacity=1000",
        "app.ratelimit.auth-refill-per-minute=1000"
})
@AutoConfigureMockMvc
class DirectoryIntegrationTests {

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

    @Test
    void directoryReturnsActiveAccountsOnly() throws Exception {
        Cookie auth = loginAs("alice", "Password123!");

        mockMvc.perform(get("/api/accounts/directory")
                        .cookie(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasSize(4)))
                .andExpect(jsonPath("$[?(@.id == 'ACC-2002')]").isEmpty());
    }

    @Test
    void directoryEntriesAreSanitized() throws Exception {
        Cookie auth = loginAs("alice", "Password123!");

        mockMvc.perform(get("/api/accounts/directory")
                        .cookie(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].currency").exists())
                .andExpect(jsonPath("$[0].owner").exists())
                .andExpect(jsonPath("$[0].balance").doesNotExist())
                .andExpect(jsonPath("$[0].status").doesNotExist());
    }

    @Test
    void directoryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/accounts/directory"))
                .andExpect(status().isUnauthorized());
    }

    private record LoginPayload(String username, String password) {
    }
}
