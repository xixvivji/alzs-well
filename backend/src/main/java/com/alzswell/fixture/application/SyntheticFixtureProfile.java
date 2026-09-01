package com.alzswell.fixture.application;

public enum SyntheticFixtureProfile {
    SMOKE(10, 60),
    DEMO(50, 240),
    PUBLIC(300, 240),
    LOAD(250, 300),
    DEV(1_000, 1_000);

    private final int customerCount;
    private final int transactionsPerCustomer;

    SyntheticFixtureProfile(int customerCount, int transactionsPerCustomer) {
        this.customerCount = customerCount;
        this.transactionsPerCustomer = transactionsPerCustomer;
    }

    public int customerCount() {
        return customerCount;
    }

    public int accountCount() {
        return Math.multiplyExact(customerCount, 2);
    }

    public int transactionsPerCustomer() {
        return transactionsPerCustomer;
    }

    public int transactionCount() {
        return Math.multiplyExact(customerCount, transactionsPerCustomer);
    }
}
