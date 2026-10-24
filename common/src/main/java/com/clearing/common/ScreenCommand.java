package com.clearing.common;

public record ScreenCommand(String transferId, String fromAccountId, String toAccountId, String toName,
        int amountCents, String reference) {
}
