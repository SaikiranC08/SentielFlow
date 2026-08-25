package app.saikiran.SentinelFlow.model.enums;

import lombok.Getter;

@Getter
public enum AccountType {
    SAVINGS_ACCOUNT(3.0),
    BUSINESS_ACCOUNT(6.0),
    TRADING_ACCOUNT(8.0),
    HIGH_NET_WORTH(10.0);

    private final double defaultMultiplierThreshold;

    AccountType(double defaultMultiplierThreshold) {
        this.defaultMultiplierThreshold = defaultMultiplierThreshold;
    }
}
