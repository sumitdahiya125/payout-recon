package com.chronon.recon.config;

import com.chronon.recon.domain.Money;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Every threshold the matcher uses, in one place and configurable without a rebuild.
 *
 * <p>The defaults implement the conservative policy: a payout is booked automatically only when a
 * payout reference is present and the amount matches exactly, or when the amount, account and date
 * all line up exactly. Anything softer is routed to human review.</p>
 */
@ConfigurationProperties(prefix = "recon.matching")
public record MatchingProperties(
        Integer dateWindowDaysBefore,
        Integer dateWindowDaysAfter,
        BigDecimal feeToleranceAmount,
        BigDecimal feeTolerancePercent,
        Boolean allowSplitPayments,
        Integer maxSplitParts
) {

    public MatchingProperties {
        dateWindowDaysBefore = dateWindowDaysBefore == null ? 1 : dateWindowDaysBefore;
        dateWindowDaysAfter = dateWindowDaysAfter == null ? 3 : dateWindowDaysAfter;
        feeToleranceAmount = feeToleranceAmount == null ? new BigDecimal("25.00") : feeToleranceAmount;
        feeTolerancePercent = feeTolerancePercent == null ? new BigDecimal("0.50") : feeTolerancePercent;
        allowSplitPayments = allowSplitPayments == null || allowSplitPayments;
        maxSplitParts = maxSplitParts == null ? 3 : maxSplitParts;
    }

    public int windowBefore() {
        return dateWindowDaysBefore;
    }

    public int windowAfter() {
        return dateWindowDaysAfter;
    }

    public boolean splitEnabled() {
        return allowSplitPayments;
    }

    public int splitParts() {
        return maxSplitParts;
    }

    public LocalDate earliest(LocalDate payoutDate) {
        return payoutDate.minusDays(windowBefore());
    }

    public LocalDate latest(LocalDate payoutDate) {
        return payoutDate.plusDays(windowAfter());
    }

    public boolean withinWindow(LocalDate payoutDate, LocalDate bankDate) {
        if (payoutDate == null || bankDate == null) {
            return false;
        }
        return !bankDate.isBefore(earliest(payoutDate)) && !bankDate.isAfter(latest(payoutDate));
    }

    public long dayOffset(LocalDate payoutDate, LocalDate bankDate) {
        return ChronoUnit.DAYS.between(payoutDate, bankDate);
    }

    /**
     * Amount slack allowed before we stop considering a credit at all. This is for bank fees and
     * rounding, not for genuinely different amounts.
     */
    public Money tolerance(Money payoutAmount) {
        Money absolute = Money.of(feeToleranceAmount);
        Money relative = payoutAmount.percentOf(feeTolerancePercent);
        return absolute.gt(relative) ? absolute : relative;
    }

    public boolean withinTolerance(Money payoutAmount, Money bankAmount) {
        return payoutAmount.minus(bankAmount).abs().compareTo(tolerance(payoutAmount)) <= 0;
    }

    public String describePolicy() {
        return "Date window: -" + windowBefore() + "/+" + windowAfter() + " days; "
                + "fee tolerance: " + Money.of(feeToleranceAmount).display() + " or "
                + feeTolerancePercent.stripTrailingZeros().toPlainString() + "% (whichever is larger); "
                + "split payments " + (splitEnabled() ? "enabled up to " + splitParts() + " parts" : "disabled")
                + "; auto-match requires exact amount plus a reference or account corroboration.";
    }
}
