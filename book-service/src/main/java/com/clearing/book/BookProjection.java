package com.clearing.book;

import com.clearing.common.Channels;
import com.clearing.common.ScreenEvent;
import com.clearing.common.SettleEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BookProjection {

    private final AccountRepository accounts;
    private final TransferRepository transfers;

    public BookProjection(AccountRepository accounts, TransferRepository transfers) {
        this.accounts = accounts;
        this.transfers = transfers;
    }

    @KafkaListener(topics = Channels.SCREENING, groupId = "book-screening")
    @Transactional
    public void onScreen(ScreenEvent event) {
        if ("HELD".equals(event.status())) {
            transfers.markHeld(event.transferId());
        } else if ("CLEARED".equals(event.status())) {
            transfers.markCleared(event.transferId());
        }
    }

    @KafkaListener(topics = Channels.SETTLEMENT, groupId = "book-settlement")
    @Transactional
    public void onSettle(SettleEvent event) {
        TransferEntity transfer = transfers.findById(event.transferId())
                .orElseThrow(() -> new IllegalStateException("transfer not visible " + event.transferId()));
        if ("POSTED".equals(event.status())) {
            if (transfers.markPosted(transfer.getId()) == 1) {
                if (accounts.credit(transfer.getToAccountId(), transfer.getAmountCents()) == 0) {
                    throw new IllegalStateException("destination missing " + transfer.getId());
                }
            }
        } else if ("RETURNED".equals(event.status())) {
            if (transfers.markReturned(transfer.getId()) == 1) {
                if (accounts.credit(transfer.getFromAccountId(), transfer.getAmountCents()) == 0) {
                    throw new IllegalStateException("source missing " + transfer.getId());
                }
            }
        }
    }
}
