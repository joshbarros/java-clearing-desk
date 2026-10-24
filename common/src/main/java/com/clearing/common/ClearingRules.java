package com.clearing.common;

import java.util.Locale;

public final class ClearingRules {
    public static final int HOLD_ABOVE_CENTS = 500_000;

    private ClearingRules() {
    }

    public static boolean held(int amountCents, String reference) {
        return amountCents > HOLD_ABOVE_CENTS
                || (reference != null && reference.toLowerCase(Locale.ROOT).contains("review"));
    }

    public static boolean frozen(String name) {
        return "FROZEN".equals(name);
    }
}
