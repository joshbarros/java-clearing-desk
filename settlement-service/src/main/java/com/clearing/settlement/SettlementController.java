package com.clearing.settlement;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class SettlementController {

    private final PostingRepository postings;

    public SettlementController(PostingRepository postings) {
        this.postings = postings;
    }

    @GetMapping("/postings/{transferId}")
    public PostingEntity posting(@PathVariable String transferId) {
        return postings.findById(transferId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "posting not found"));
    }
}
