package com.example.dashboard.account;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountView> getAccounts(Authentication authentication) {
        return accountService.findForOwner(authentication.getName());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountView> getAccount(Authentication authentication,
                                                  @PathVariable String id) {
        return accountService.findForOwnerById(authentication.getName(), id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
