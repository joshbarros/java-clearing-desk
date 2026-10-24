package com.clearing.common;

public record SettleCommand(String transferId, String fromAccountId, String toAccountId, String toName, int amountCents) {
}
