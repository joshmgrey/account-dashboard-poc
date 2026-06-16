package com.example.dashboard.transfer;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts/{accountId}/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(Authentication authentication,
                                                           @PathVariable("accountId") String accountId,
                                                           @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                           @RequestBody TransferRequest request) {
        TransferResult result = transferService.createTransfer(
                authentication.getName(), accountId, idempotencyKey, request);

        HttpStatus status = result.isReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        String message = result.isReplay() ? "Transfer already processed" : "Transfer created";

        return ResponseEntity.status(status).body(new TransferResponse(result.transfer(), message));
    }
}
