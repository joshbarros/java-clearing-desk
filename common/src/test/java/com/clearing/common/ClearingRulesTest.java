package com.clearing.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClearingRulesTest {

    @Test
    void fiveHundredThousandClearsAndOneCentMoreIsHeld() {
        assertFalse(ClearingRules.held(500_000, "invoice"));
        assertTrue(ClearingRules.held(500_001, "invoice"));
        assertTrue(ClearingRules.held(100, "Please Review"));
        assertFalse(ClearingRules.held(100, "monthly"));
    }

    @Test
    void onlyTheExactNameIsFrozen() {
        assertTrue(ClearingRules.frozen("FROZEN"));
        assertFalse(ClearingRules.frozen("frozen"));
        assertFalse(ClearingRules.frozen(null));
    }
}
