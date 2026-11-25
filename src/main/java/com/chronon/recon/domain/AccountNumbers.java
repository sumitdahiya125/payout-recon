package com.chronon.recon.domain;

import java.util.Locale;

/** Helpers for masked bank account numbers, which are the weakest but most common matching signal. */
public final class AccountNumbers {

    private AccountNumbers() {
    }

    /** Keeps only digits; returns the last four digits (or the whole value when shorter). */
    public static String tail(String account) {
        if (account == null) {
            return "";
        }
        String digits = account.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return "";
        }
        return digits.length() <= 4 ? digits : digits.substring(digits.length() - 4);
    }

    public static String normalize(String account) {
        if (account == null) {
            return "";
        }
        return account.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s\\-]", "");
    }

    public static String normalizeIfsc(String ifsc) {
        if (ifsc == null) {
            return "";
        }
        return ifsc.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s\\-]", "");
    }
}
