package com.chronon.recon.ingest.mapping;

/** Canonical field keys. Profiles map these names onto whatever the source spreadsheet calls them. */
public final class Fields {

    // Payout register
    public static final String PAYOUT_ID = "payoutId";
    public static final String EMPLOYEE_ID = "employeeId";
    public static final String EMPLOYEE_NAME = "employeeName";
    public static final String BANK_ACCOUNT = "bankAccount";
    public static final String IFSC = "ifsc";
    public static final String AMOUNT = "amount";
    public static final String PAYOUT_DATE = "payoutDate";
    public static final String CLAIM_IDS = "claimIds";
    public static final String STATUS = "status";

    // Bank statement
    public static final String TXN_DATE = "txnDate";
    public static final String VALUE_DATE = "valueDate";
    public static final String NARRATION = "narration";
    public static final String CHEQUE_REF = "chequeRef";
    public static final String DEBIT = "debit";
    public static final String CREDIT = "credit";
    public static final String BALANCE = "balance";
    public static final String DR_CR = "drCr";

    private Fields() {
    }
}
