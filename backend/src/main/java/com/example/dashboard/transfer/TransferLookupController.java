package com.example.dashboard.transfer;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only endpoint for fetching transfer details by ID. Visibility is enforced
 * in the service layer (source or destination owner only). Pairs with
 * {@link TransferController} which handles the create operation under a nested
 * account-scoped path.
 */
@RestController
@RequestMapping("/api/transfers")
public class TransferLookupController {

    private final TransferService transferService;

    public TransferLookupController(TransferService transferService) {
        this.transferService = transferService;
    }

    @GetMapping("/{transferId}")
    public TransferDetailView getTransfer(Authentication authentication,
                                          @PathVariable String transferId) {
        return transferService.findById(authentication.getName(), transferId);
    }
}
