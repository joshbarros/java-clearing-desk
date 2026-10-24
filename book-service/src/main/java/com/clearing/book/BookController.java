package com.clearing.book;

import java.util.UUID;

import com.clearing.common.Channels;
import com.clearing.common.ScreenCommand;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class BookController {

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final RabbitTemplate rabbit;

    public BookController(AccountRepository accounts, TransferRepository transfers, RabbitTemplate rabbit) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.rabbit = rabbit;
    }

    @PostMapping("/accounts")
    public ResponseEntity<AccountEntity> open(@RequestBody OpenAccountRequest request) {
        if (request == null || request.name() == null || request.name().isBlank() || request.balanceCents() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name and balanceCents are required");
        }
        AccountEntity account = new AccountEntity();
        account.setId(UUID.randomUUID().toString());
        account.setName(request.name().trim());
        account.setBalanceCents(request.balanceCents());
        return ResponseEntity.status(HttpStatus.CREATED).body(accounts.save(account));
    }

    @GetMapping("/accounts/{id}")
    public AccountEntity account(@PathVariable String id) {
        return accounts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found"));
    }

    @PostMapping("/transfers")
    @Transactional
    public ResponseEntity<TransferEntity> transfer(@RequestBody TransferRequest request) {
        if (request == null || request.amountCents() <= 0 || request.reference() == null || request.reference().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amountCents and reference are required");
        }
        AccountEntity source = accounts.findById(request.fromAccountId() == null ? "" : request.fromAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found"));
        AccountEntity dest = accounts.findById(request.toAccountId() == null ? "" : request.toAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found"));
        if (source.getId().equals(dest.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accounts must differ");
        }
        if (accounts.debit(source.getId(), request.amountCents()) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "insufficient balance");
        }
        TransferEntity transfer = new TransferEntity();
        transfer.setId(UUID.randomUUID().toString());
        transfer.setFromAccountId(source.getId());
        transfer.setToAccountId(dest.getId());
        transfer.setToName(dest.getName());
        transfer.setAmountCents(request.amountCents());
        transfer.setReference(request.reference().trim());
        transfer.setStatus("PENDING");
        transfers.save(transfer);
        Publish.afterCommit(() -> rabbit.convertAndSend(Channels.SCREEN, new ScreenCommand(
                transfer.getId(), transfer.getFromAccountId(), transfer.getToAccountId(), transfer.getToName(),
                transfer.getAmountCents(), transfer.getReference())));
        return ResponseEntity.status(HttpStatus.CREATED).body(transfer);
    }

    @GetMapping("/transfers/{id}")
    public TransferEntity transfer(@PathVariable String id) {
        return transfers.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "transfer not found"));
    }

    public record OpenAccountRequest(String name, int balanceCents) {
    }

    public record TransferRequest(String fromAccountId, String toAccountId, int amountCents, String reference) {
    }
}
