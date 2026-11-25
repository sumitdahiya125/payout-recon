package com.chronon.recon.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable monetary amount in INR.
 *
 * <p>Enforces two-decimal scale (HALF_UP rounding) and null-safety to prevent
 * floating-point rounding errors across matching and tie-out calculations.</p>
 */
public record Money(BigDecimal amount) implements Comparable<Money> {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal value) {
        return new Money(value);
    }

    public static Money of(String value) {
        return new Money(new BigDecimal(value));
    }

    public static Money of(long value) {
        return new Money(BigDecimal.valueOf(value));
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money abs() {
        return new Money(amount.abs());
    }

    public Money negate() {
        return new Money(amount.negate());
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean gt(Money other) {
        return amount.compareTo(other.amount) > 0;
    }

    public boolean gte(Money other) {
        return amount.compareTo(other.amount) >= 0;
    }

    public boolean lt(Money other) {
        return amount.compareTo(other.amount) < 0;
    }

    public Money percentOf(BigDecimal percent) {
        return new Money(amount.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
    }

    public String display() {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }
}